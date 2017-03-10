/**
 * 
 */
package com.axios.ccdp.mesos.fmwk;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.apache.log4j.Logger;
import org.apache.mesos.Protos.Attribute;
import org.apache.mesos.Protos.ExecutorInfo;
import org.apache.mesos.Protos.Filters;
import org.apache.mesos.Protos.Offer;
import org.apache.mesos.Protos.OfferID;
import org.apache.mesos.Protos.Resource;
import org.apache.mesos.Protos.TaskInfo;

import com.axios.ccdp.mesos.resources.CcdpVMResource;
import com.axios.ccdp.mesos.resources.CcdpVMResource.ResourceStatus;
import com.axios.ccdp.mesos.tasking.CcdpTaskRequest;
import com.axios.ccdp.mesos.tasking.CcdpThreadRequest;
import com.axios.ccdp.mesos.tasking.CcdpThreadRequest.TasksRunningMode;
import com.axios.ccdp.mesos.utils.CcdpUtils;
import com.fasterxml.jackson.databind.node.ObjectNode;

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
 *      0 > CPU < 100:  Use the first VM with enough resources to run the task
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
   * SchedulerDriver.launchTasks(Collection<OfferID>, Collection<TaskInfo>, 
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
   * @param driver - The driver that was used to run this scheduler.
   * @param offers - The resources offered to this framework.
   * 
   */
  public void handleResourceOffering(List<Offer> offers)
  {
    this.logger.info("resourceOffers: Got some resource Offers" );
    //List<OfferID> offerIds = new ArrayList<OfferID>();
    
    // generates a list of resources using the offers
    for( Offer offer : offers )
    {
      this.logger.debug("Offer: " + offer.toString());
      String id = null;
      String sid = null;
      String aid = offer.getSlaveId().getValue();
      
//      offerIds.add( offer.getId() );
      
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
        this.logger.info("Resorce does not have session id, adding it to free");
        this.free_vms.add(vm);
      }
      
      List<Offer.Operation> allOps = new ArrayList<>();
      
      // Assign the tasks for each request
      synchronized( this.requests )
      {
        for( CcdpThreadRequest req : this.requests )
        {
          List<Offer.Operation> ops = this.assignOps(req);
          
          this.logger.info("Using Operations rather than TaskInfo");
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
   * @param @param task the task whose status changed 
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

  /***************************************************************************/
  /***************************************************************************/
  /***************************************************************************/
  
  /**
   * It finds a resource where to run each one of the tasks in the request.  If
   * all the tasks are completed, then the request is removed from the data 
   * structure and the method returns null.  If all the tasks have been 
   * submitted then the method just returns null.
   * 
   * If neither of the two scenarios described above are true, then it assigns
   * each of the tasks if possible to run on a specific VM.  Once those tasks 
   * are assigned it return a list of TaskInfo objects so they can be launched 
   * by the SchedulerDriver
   * 
   * @param req the request object containing the tasks to assign
   * 
   * @return as list of TaskInfo objects to launch by the SchedulerDriver
   */
  public List<TaskInfo> assignTasks( CcdpThreadRequest req )
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
    
    if( resources != null  )
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
          task.assigned();
          this.logger.info("CPU = " + cpu + " Assigning a Resource just for this task");
          List<String> ids = this.controller.startInstances(1, 1);
          for( String id : ids )
          {
            CcdpVMResource vm = new CcdpVMResource(id);
            vm.setSingleTask(task.getTaskId());
          }
        }
      }
      this.tasker.setExecutor(this.executor);
      List<TaskInfo> taskInfo = this.tasker.assignTasks(tasks, resources);
      this.logger.info("Launching " + taskInfo.size() + " tasks");
      return taskInfo;
    }// found the resources
    this.logger.debug("About to return null, no resources found");
    return null;
  }
  

  /**
   * It finds a resource where to run each one of the tasks in the request.  If
   * all the tasks are completed, then the request is removed from the data 
   * structure and the method returns null.  If all the tasks have been 
   * submitted then the method just returns null.
   * 
   * If neither of the two scenarios described above are true, then it assigns
   * each of the tasks if possible to run on a specific VM.  Once those tasks 
   * are assigned it return a list of TaskInfo objects so they can be launched 
   * by the SchedulerDriver
   * 
   * @param req the request object containing the tasks to assign
   * 
   * @return as list of TaskInfo objects to launch by the SchedulerDriver
   */
  public List<Offer.Operation> assignOps( CcdpThreadRequest req )
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
    if( resources != null  )
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
          task.assigned();
          this.logger.info("CPU = " + cpu + " Assigning a Resource just for this task");
          List<String> ids = this.controller.startInstances(1, 1);
          for( String id : ids )
          {
            CcdpVMResource vm = new CcdpVMResource(id);
            vm.setSingleTask(task.getTaskId());
          }
        }
      }
      
      this.tasker.setExecutor(this.executor);
      List<TaskInfo> taskInfo = this.tasker.assignTasks(tasks, resources);
      this.logger.info("Launching " + taskInfo.size() + " tasks");
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
    
    // Getting all the resources for this session
    return this.sessions.get(sid);
  }
    
}
