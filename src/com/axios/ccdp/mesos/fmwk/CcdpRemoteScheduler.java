/**
 * 
 */
package com.axios.ccdp.mesos.fmwk;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;

import org.apache.log4j.Logger;
import org.apache.mesos.Protos;
import org.apache.mesos.Protos.Attribute;
import org.apache.mesos.Protos.CommandInfo;
import org.apache.mesos.Protos.ExecutorInfo;
import org.apache.mesos.Protos.Filters;
import org.apache.mesos.Protos.Offer;
import org.apache.mesos.Protos.OfferID;
import org.apache.mesos.Protos.Resource;
import org.apache.mesos.Protos.SlaveID;
import org.apache.mesos.Protos.TaskID;
import org.apache.mesos.Protos.TaskInfo;
import org.apache.mesos.Protos.Value;

import com.axios.ccdp.mesos.resources.CcdpVMResource;
import com.axios.ccdp.mesos.resources.CcdpVMResource.ResourceStatus;
import com.axios.ccdp.mesos.tasking.CcdpTaskRequest;
import com.axios.ccdp.mesos.tasking.CcdpTaskRequest.CcdpTaskState;
import com.axios.ccdp.mesos.tasking.CcdpThreadRequest;
import com.axios.ccdp.mesos.tasking.CcdpThreadRequest.TasksRunningMode;
import com.axios.ccdp.mesos.utils.CcdpUtils;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.protobuf.ByteString;

/**
 * Class used to coordinate the tasking among multiple Virtual Machines.  It 
 * uses the information on the task to make determinations regarding where to 
 * execute the task.  Information affecting the task execution includes, but 
 * not limited, the following fields:
 * 
 *  - NodeType: Based on the node type, it can run on a simple EC2 instance or
 *              a cluster such as EMR, Hadoop, etc.
 *  - CPU:  The CPU value determines the schema to use as follow:
 *      CPU = 0:        Let the Scheduler decide where to run it
 *      0 &gt; CPU &lt; 100:  Use the first VM with enough resources to run the task
 *      CPU = 100:      Run this task by itself on a new VM
 *  
 * @author Oscar E. Ganteaume
 *
 */
public class CcdpRemoteScheduler extends CcdpMesosScheduler
{
  /**
   * Generates debug print statements based on the verbosity level.
   */
  private Logger logger = 
      Logger.getLogger(CcdpRemoteScheduler.class.getName());
  
  /**
   * Stores the default channel to report tasking status updates
   */
  private String def_channel = null;
  
  /**
   * Instantiates a new executors and starts the jobs assigned as the jobs
   * argument.  If the jobs is null then it ignores them
   * 
   * @param execInfo the name of the executor to use to execute the tasks
   * @param jobs an optional list of jobs
   */
  public CcdpRemoteScheduler( ExecutorInfo execInfo, List<CcdpThreadRequest> jobs )
  {
    super(execInfo, jobs);
    this.def_channel = 
        CcdpUtils.getProperty(CcdpUtils.CFG_KEY_RESPONSE_CHANNEL);
    
  }

  
  /**
   * Invoked when resources have been offered to this framework. A single offer 
   * will only contain resources from a single slave. Resources associated with 
   * an offer will not be re-offered to _this_ framework until either (a) this 
   * framework has rejected those resources (see 
   * SchedulerDriver.launchTasks(Collection&lt;OfferID&gt;, Collection&lt;TaskInfo&gt;, 
   *                             Filters)) 
   * or (b) those resources have been rescinded (see 
   * offerRescinded(SchedulerDriver, OfferID)). Note that resources may be 
   * concurrently offered to more than one framework at a time (depending on 
   * the allocator being used). In that case, the first framework to launch 
   * tasks using those resources will be able to use them while the other 
   * frameworks will have those resources rescinded (or if a framework has 
   * already launched tasks with those resources then those tasks will fail 
   * with a TASK_LOST status and a message saying as much).
   * 
   * @param offers - The resources offered to this framework.
   * 
   */
  public void handleResourceOffering(List<Offer> offers)
  {
    this.logger.info("resourceOffers: Got some resource Offers" );
    
    // generates a list of resources using the offers
    for( Offer offer : offers )
    {
      this.logger.debug("Offer: " + offer.toString());
      String id = null;
      String sid = null;
      String aid = offer.getSlaveId().getValue();
      
      // Getting the attribute information
      for( Attribute attr : offer.getAttributesList() )
      {
        switch( attr.getName() )
        {
        case CcdpUtils.KEY_INSTANCE_ID:
          id = attr.getText().getValue();
          break;
        case CcdpUtils.KEY_SESSION_ID:
          sid = attr.getText().getValue();
          break;
        }
      }
      
      // if the instance id was not set use the agent id
      if( id == null )
        id = aid;
      
      // creating a CcdpVMResource object to compare
      CcdpVMResource vm = new CcdpVMResource(id);
      vm.setAssignedSession(sid);
      this.logger.debug("The Agent ID: [" + aid + "]");
      vm.setAgentId(aid);
      
      // We always need to extract the resource info from the offer
      for( Resource r : offer.getResourcesList() )
      {
        switch( r.getName() )
        {
          case "cpus":
            vm.setCPU( r.getScalar().getValue() );
            break;
          case "mem":
            vm.setMEM( r.getScalar().getValue() );
            break;
          case "disk":
            vm.setDisk( r.getScalar().getValue() );
            break;
        }
        
      }// end of resources loop

      this.logger.debug("Checking SID " + sid );
      
      // if it has not been assigned, then check free
      if( sid != null && !this.updateResource( vm, this.sessions.get(sid) ) )
      {
        this.logger.info("Have session id, but not list; creating one");
        List<CcdpVMResource> newList = new ArrayList<>();
        newList.add(vm);
        
        // Is my laptop and not an actual VM
        if( id.startsWith("i-test") )
          vm.setStatus(ResourceStatus.RUNNING);
        
        this.sessions.put(sid, newList);
      }// it does not have a session id
      else if( sid == null )
      {
        this.logger.info("Resource does not have session id, adding it to free");
        this.free_vms.add(vm);
      }
      
      List<Offer.Operation> allOps = new ArrayList<>();
      
      // Assign the tasks for each request
      synchronized( this.requests )
      {
        for( CcdpThreadRequest req : this.requests )
        {
          // getting the resource based on the request session id
          CcdpVMResource tgt_resource = null;
          List<CcdpVMResource> resources = this.getResources(req);
          if( resources != null )
          {
            for( CcdpVMResource resource : this.getResources(req) )
            {
              if( resource.equals(vm))
              {
                this.logger.debug("Resource found (" + vm.getInstanceId() + ")");
                tgt_resource = resource;
              }
            }
          }// end of the resources if condition
          
          List<Offer.Operation> ops = this.assignTasks(tgt_resource, req);
          
          if( ops != null )
          {
            this.logger.debug("Operations to accept: " + ops.size());
            allOps.addAll(ops);
          }
          else
            this.logger.info("Request does not have tasks to run");
        }
        
        List<OfferID> list = Collections.singletonList( offer.getId() );
        Filters filters = Filters.newBuilder().setRefuseSeconds(5).build();
        this.driver.acceptOffers(list, allOps, filters);
      }
      
    }// end of the offer loop
  }
  
  /**
   * Handles a status change on a single CcdpTaskRequest.  If the replyTo of
   * either the Task or the Thread is set, then it sends a notification to the
   * client of the change.
   * 
   * @param task the task whose status changed 
   */
  public void handleStatusUpdate( CcdpTaskRequest task )
  {
    String channel = task.getReplyTo();
    // if is not set, the let's try the default one
    if ( channel == null || channel.length() == 0 )
      channel = this.def_channel;
    
    if( channel != null && channel.length() > 0 )
    {
      String tid = task.getTaskId();
      
      this.logger.debug("Status change, sending message to " + channel);
      ObjectNode node = this.mapper.createObjectNode();
      node.put(CcdpUtils.KEY_SESSION_ID, task.getSessionId());
      node.put(CcdpUtils.KEY_TASK_ID, tid);
      node.put(CcdpUtils.KEY_TASK_STATUS, task.getState().toString());
      
      this.taskingInf.sendEvent(channel, null, node.toString());
    }
    else
    {
      this.logger.debug("Task did not have a channel set!");
    }
    
    CcdpTaskState state = task.getState();
    if( state.equals(CcdpTaskState.SUCCESSFUL) || 
        state.equals(CcdpTaskState.FAILED))
      this.removeTask(task);
  }

  /**
   * Removes a task that has either FAILED to execute or it finished 
   * successfully.  The task is found looking into each VM resource assigned to
   * each session.  
   * 
   * @param task the task to remove from one of the VM Resources
   */
  private void removeTask( CcdpTaskRequest task )
  {
    boolean found = false;
    // Get all the resources from each session, then look for the task in it
    for( String sid : this.sessions.keySet() )
    {
      this.logger.debug("Looking task in session " + sid );
      for( CcdpVMResource resource : this.sessions.get(sid) )
      {
        if( resource.removeTask(task) )
        {
          this.logger.debug("Found task in resource " + resource.getAgentId());
          found = true;
          break;
        }
      }
      // no need to keep looking for the task in remaining sessions
      if( found )
        break;
    }// end of the session's loop
  }
  
  /**
   * Updates the item in the list if the instance id matches one of the free
   * resources.
   * 
   * @param vm the resource to compare
   * @param list the list of resources to search for the list
   * 
   * @return true if the item was found and updated false otherwise
   */
  public boolean updateResource( CcdpVMResource vm, List<CcdpVMResource> list)
  {
    if( list == null )
    {
      this.logger.info("Could not find a list for the given session");
      return false;
    }
    
    // checking every item in the list
    for( CcdpVMResource resource : list )
    {
      if( resource.equals(vm))
      {
        this.logger.debug("Resource found (" + vm.getInstanceId() + ")");
        resource = vm;
        return true;
      }
    }
    
    return false;
  }

  /**************************************************************************/
  /***************************************************************************/
  /***************************************************************************/
  
  /**
   * It determines which tasks in the given thread can be executed in the 
   * target VM.  If all the tasks are completed, then the request is removed 
   * from the data structure and the method returns null.  If all the tasks 
   * have been submitted then the method just returns null.
   * 
   * If neither of the two scenarios described above are true, then it assigns
   * each of the tasks if possible to run on a specific VM.  Once those tasks 
   * are assigned it return a list of TaskInfo objects so they can be launched 
   * by the SchedulerDriver
   * 
   * @param target the VM intended to run the tasks
   * @param req the request object containing the tasks to assign
   * 
   * @return as list of Operation objects to launch by the SchedulerDriver
   */
  private List<Offer.Operation> assignTasks( CcdpVMResource target, 
                                          CcdpThreadRequest req )
  {
    this.logger.debug("Assinging Tasks to Request " + req.toString() );
    if( req.threadRequestCompleted() )
    {
      this.logger.info("Thread " + req.getThreadId() + " is Complete!!");
      this.requests.remove(req);
      return null;
    }
    
    // Get all the resources for the session set in the request
    List<CcdpVMResource> resources = this.getResources(req);
    this.logger.debug("Got the resources");
    List<CcdpTaskRequest> tasks = new ArrayList<>();
    // if we have resources to run the task
    if( target != null && resources != null  )
    {
      this.logger.info("Found A session");
      if( TasksRunningMode.PARALLEL.equals( req.getTasksRunningMode() ) )
      {
        // adding all tasks
        for( CcdpTaskRequest task : req.getTasks() )
        {
          if( !task.isSubmitted() )
          {
            tasks.add(task);
          }
        }
        // all the tasks are submitted
        req.setTasksSubmitted(true);
      }
      else
      {
        CcdpTaskRequest task = req.getNextTask();
        if(task != null && !task.isSubmitted() )
        {
          tasks.add(task);
        }
      }
      
      this.logger.debug("Getting the tasks");
      // Do I need to assign a whole node to the task?
      for( CcdpTaskRequest task : tasks )
      {
        double cpu = task.getCPU();
        if( cpu == 100 )
        {

          this.logger.info("CPU = " + cpu + 
                           " Assigning a Resource just for this task");
          if( target.getTasks().size() == 0 )
          {
            this.logger.info("Resource " + target.getInstanceId() + 
                             " is empty, using it");
            target.setSingleTask(task.getTaskId() );
          }
          else
          {
            this.logger.info("Did not find available resource, launching one");
            List<String> ids = this.controller.startInstances(1, 1);
            for( String id : ids )
            {
              CcdpVMResource vm = new CcdpVMResource(id);
              vm.setSingleTask(task.getTaskId());
            }
          }
          task.assigned();
        }// end of the cpu = 100 if condition
      }// end of the tasks loop
      
      List<CcdpTaskRequest> assigned = this.tasker.assignTasks(tasks, target, resources);
      List<TaskInfo> taskInfo = this.makeTasks(assigned, target.getAgentId());
      
      this.logger.info("Launching " + assigned.size() + " tasks");
      List<Offer.Operation> ops = new ArrayList<>();
      for( TaskInfo ti : taskInfo)
      {
        Offer.Operation.Launch.Builder launch = Offer.Operation.Launch.newBuilder();
        launch.addTaskInfos(TaskInfo.newBuilder(ti));
        
        Offer.Operation operation = Offer.Operation.newBuilder()
            .setType(Offer.Operation.Type.LAUNCH)
            .setLaunch(launch)
            .build();

          ops.add(operation);
      }
      
      return ops;
      
    }// found the resources
    this.logger.debug("About to return null, no resources found");
    return null;
  }
  
  
  
  /**
   * Gets all the resources assigned to the session.  The following checks are
   * done in order to return the appropriate list
   * 
   *    - The request cannot be null
   *    - The session-id cannot be null
   *    - At least one of the tasks in the request need to be submitted
   *    - The thread is not complete
   *    - The session exists in the list sessions container
   *    
   * @param req the request that needs to be processed and need resources
   * 
   * @return a list of resources available to process this request
   */
  private List<CcdpVMResource> getResources(CcdpThreadRequest req)
  {
    if( req == null )
      return null;
    
    String id = req.getThreadId();
    String sid = req.getSessionId();
    
    this.logger.info("Assigning Resources to Request " + id + " Session " + sid);
    // is there a problem with the session-id?
    if( sid == null )
    {
      this.logger.error("The Thread " + id + " does not have Session ID");
      return null;
    }
    
    // have all the tasks been submitted already?
    if( req.isTasksSubmitted() )
    {
      this.logger.info("All the tasks have been submitted for " + id);
      return null;
    }
    
    // Is this thread done?
    if( req.threadRequestCompleted() )
    {
      this.logger.info("Thread " + id + " for Session " + sid + " Complete");
      this.requests.remove(req);
      return null;
    }
    List<CcdpVMResource> list = this.sessions.get(sid);
    if ( list == null )
    {
      this.logger.info("The session id " + sid + " does not have resources, checking free");
      if( this.free_vms.size() > 0 )
      {
        CcdpVMResource res = this.free_vms.remove(0);
        this.logger.info("Assigning VM " + res.getInstanceId() + " to " + sid);
        res.setAssignedSession(sid);
        res.setStatus(ResourceStatus.RUNNING);
        List<CcdpVMResource> resources = new ArrayList<>();
        resources.add(res);
        this.sessions.put(sid, resources);
      }
    }
    
    // Getting all the resources for this session
    return this.sessions.get(sid);
  }
  
  /**
   * Generates all the TaskInfo objects required to run the assigned tasks in
   * the given mesos agent.
   * 
   * @param tasks all the tasks to launch 
   * @param targetSlave the agent id of the mesos node assigned to run all 
   *        these tasks
   *        
   * @return a list of TaskInfo object that are used to launch the given tasks
   */
  private List<TaskInfo> makeTasks(List<CcdpTaskRequest> tasks, String agent )
  {
    List<TaskInfo> list = new ArrayList<>();
    for( CcdpTaskRequest task : tasks )
    {
      list.add(this.makeTask(agent, task));
    }
    
    return list;
  }
  
  /**
   * Generates a TaskInfo object that will be executed by the appropriate Mesos 
   * Agent.
   * 
   * @param targetSlave the Unique identifier of the Mesos Agent responsible for
   *        running this task
   * @param exec The mesos executor to use to execute the task
   * 
   * @return a TaskInfo object containing all the information required to run
   *         this job
   */
  private TaskInfo makeTask(String targetSlave, CcdpTaskRequest task)
  {
    this.logger.debug("Making Task at Slave " + targetSlave);
    TaskID id = TaskID.newBuilder().setValue(task.getTaskId()).build();
    
    Protos.TaskInfo.Builder bldr = TaskInfo.newBuilder();
    bldr.setName("task " + id.getValue());
    bldr.setTaskId(id);
    // Adding the CPU
    Protos.Resource.Builder resBldr = Resource.newBuilder();
    resBldr.setName("cpus");
    resBldr.setType(Value.Type.SCALAR);
    resBldr.setScalar(Value.Scalar.newBuilder().setValue(task.getCPU()));
    Resource cpuRes = resBldr.build();
    bldr.addResources(cpuRes);
    this.logger.debug("Adding CPU Resource " + cpuRes.toString());
    
    // Adding the Memory
    resBldr.setName("mem");
    resBldr.setType(Value.Type.SCALAR);
    resBldr.setScalar(Value.Scalar.newBuilder().setValue(task.getMEM()));
    Resource memRes = resBldr.build();
    bldr.addResources(memRes);
    this.logger.debug("Adding MEM Resource " + memRes.toString());
    
    Protos.CommandInfo.Builder cmdBldr = CommandInfo.newBuilder();
    StringJoiner joiner = new StringJoiner(" ");
    task.getCommand().forEach(joiner::add);
    
    String cmd = joiner.toString();
    cmdBldr.setValue(cmd);
    this.logger.debug("Running Command: " + cmd);
    
    Protos.SlaveID.Builder slvBldr = SlaveID.newBuilder();
    slvBldr.setValue(targetSlave);
    bldr.setSlaveId(slvBldr.build());
    
    bldr.setExecutor( ExecutorInfo.newBuilder(this.executor) );
    ObjectNode json = this.mapper.createObjectNode();
    
    task.getCommand();
    task.getConfiguration();
    Map<String, String> cfg = task.getConfiguration();
    // if there is a configuration, add it
    if( cfg != null )
    {
      JsonNode jsonNode = mapper.convertValue(cfg, JsonNode.class);
      json.set("cfg", jsonNode);
    }
    
    json.put("cmd", cmd);
    
    bldr.setData(ByteString.copyFrom(json.toString().getBytes()));
    return bldr.build();
  }
}
