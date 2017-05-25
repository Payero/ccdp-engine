/**
 * 
 */
package com.axios.ccdp.fmwk;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;

import org.apache.log4j.Logger;

import com.axios.ccdp.connections.intfs.CcdpMessageConsumerIntf;
import com.axios.ccdp.connections.intfs.CcdpStorageControllerIntf;
import com.axios.ccdp.connections.intfs.CcdpTaskConsumerIntf;
import com.axios.ccdp.connections.intfs.CcdpTaskingControllerIntf;
import com.axios.ccdp.connections.intfs.CcdpTaskingIntf;
import com.axios.ccdp.connections.intfs.CcdpVMControllerIntf;
import com.axios.ccdp.factory.CcdpObjectFactory;
import com.axios.ccdp.message.AssignSessionMessage;
import com.axios.ccdp.message.CcdpMessage;
import com.axios.ccdp.message.ResourceUpdateMessage;
import com.axios.ccdp.message.RunTaskMessage;
import com.axios.ccdp.message.TaskUpdateMessage;
import com.axios.ccdp.message.ThreadRequestMessage;
import com.axios.ccdp.message.UndefinedMessage;
import com.axios.ccdp.message.CcdpMessage.CcdpMessageType;
import com.axios.ccdp.newgen.CcdpConnectionIntf;
import com.axios.ccdp.resources.CcdpVMResource;
import com.axios.ccdp.resources.CcdpVMResource.ResourceStatus;
import com.axios.ccdp.tasking.CcdpTaskRequest;
import com.axios.ccdp.tasking.CcdpThreadRequest;
import com.axios.ccdp.tasking.CcdpTaskRequest.CcdpTaskState;
import com.axios.ccdp.tasking.CcdpThreadRequest.TasksRunningMode;
import com.axios.ccdp.utils.CcdpImageInfo;
import com.axios.ccdp.utils.CcdpUtils;
import com.axios.ccdp.utils.TaskEventIntf;
import com.axios.ccdp.utils.ThreadedTimerTask;
import com.axios.ccdp.utils.CcdpUtils.CcdpNodeType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
 *      0 &gt; CPU &lt; 100:  Use the first VM with enough resources to run the task
 *      CPU = 100:      Run this task by itself on a new VM
 *  
 * @author Oscar E. Ganteaume
 *
 */
//public class CcdpEngine implements CcdpTaskConsumerIntf, TaskEventIntf, CcdpMessageConsumerIntf
public class CcdpEngine implements TaskEventIntf, CcdpMessageConsumerIntf
{
//  /**
//   * Stores the name of the session with available resources
//   */
//  private static final String FREE_SESSION = "available";
  
  /**
   * Generates debug print statements based on the verbosity level.
   */
  private Logger logger = 
      Logger.getLogger(CcdpEngine.class.getName());
  
  /**
   * Stores the default channel to report tasking status updates
   */
  private String def_channel = null;
  
  /**
   * Provides a consolidated way to format dates
   */
  private SimpleDateFormat formatter = 
      new SimpleDateFormat("yyyy-mm-dd hh:mm:ss");
  /**
   * Creates all the ArrayNode and ObjectNode
   */
  private ObjectMapper mapper = new ObjectMapper();
  /**
   * Stores a list of requests to process.  Each request is a processing thread
   * containing one or more processing task.
   */
  private ConcurrentLinkedQueue<CcdpThreadRequest> 
                requests = new ConcurrentLinkedQueue<>();

  /**
   * Object used to send and receive messages such as incoming tasks to 
   * process, heartbeats, and tasks updates
   */
  private CcdpConnectionIntf connection;
  
//  /**
//   * Stores the object responsible for sending and receiving tasking information
//   */
//  private CcdpTaskingIntf taskingInf = null;
  /**
   * Stores the object that determines the logic to assign tasks to VMs
   */
  private CcdpTaskingControllerIntf tasker = null;
  /**
   * Controls all the VMs
   */
  private CcdpVMControllerIntf controller =null;
  /**
   * Object responsible for creating/deleting files
   */
  private CcdpStorageControllerIntf storage = null;
  /**
   * Stores all the VMs allocated to different sessions
   */
  private Map<String, CcdpVMResource> resources = new HashMap<>();
  /**
   * The unique identifier for this engine
   */
  private String engineId = UUID.randomUUID().toString();
//  /**
//   * Stores the instance id of the EC2 running this framework
//   */
//  private String instanceId = null;
  /**
   * Continuously monitors the state of the system
   */
  private ThreadedTimerTask timer = null;
  /**
   * Stores a list of host ids that should not be terminated
   */
  private List<String> skipTermination = new ArrayList<>(); 
  
  /**
   * Instantiates a new executors and starts the jobs assigned as the jobs
   * argument.  If the jobs is null then it ignores them
   * 
   * @param jobs an optional list of jobs
   */
  public CcdpEngine( List<CcdpThreadRequest> jobs )
  {
    this.def_channel = 
        CcdpUtils.getProperty(CcdpUtils.CFG_KEY_RESPONSE_CHANNEL);
    // creating the factory that generates the objects used by the scheduler
    CcdpObjectFactory factory = CcdpObjectFactory.newInstance();
    ObjectNode task_msg_node = 
        CcdpUtils.getJsonKeysByFilter(CcdpUtils.CFG_KEY_TASK_MSG);
    ObjectNode task_ctr_node = 
        CcdpUtils.getJsonKeysByFilter(CcdpUtils.CFG_KEY_TASK_CTR);
    ObjectNode res_ctr_node = 
        CcdpUtils.getJsonKeysByFilter(CcdpUtils.CFG_KEY_RESOURCE);
    ObjectNode storage_node = 
        CcdpUtils.getJsonKeysByFilter(CcdpUtils.CFG_KEY_STORAGE);
    
//    this.taskingInf = factory.getCcdpTaskingInterface(task_msg_node);
    this.connection = factory.getCcdpConnectionInterface(task_msg_node);
    this.tasker = factory.getCcdpTaskingController(task_ctr_node);
    this.controller = factory.getCcdpResourceController(res_ctr_node);
    this.storage = factory.getCcdpStorageControllerIntf(storage_node);
    
//    this.taskingInf.setTaskConsumer(this);
//    this.taskingInf.register(this.engineId);
    this.connection.configure(task_msg_node);
    this.connection.setConsumer(this);
    if( this.def_channel != null )
      this.connection.registerProducer(this.def_channel);
    
    String toMain = CcdpUtils.getProperty(CcdpUtils.CFG_KEY_MAIN_CHANNEL);
    this.logger.info("Registering as " + this.engineId);
    this.connection.registerConsumer(this.engineId, toMain);
    
    // Skipping some nodes from termination
    String ids = CcdpUtils.getProperty(CcdpUtils.CFG_KEY_SKIP_TERMINATION);
    
    if( ids != null )
    {
      for( String id : ids.split(",") )
      {
        this.logger.info("Skipping " + id + " from termination");
        this.skipTermination.add(id);
      }
    }// end of the do not terminate section
    
//    try
//    {
//      this.instanceId = CcdpUtils.retrieveEC2Info("instance-id");
//      this.logger.info("Framework running on instance: " + this.instanceId );
//    }
//    catch( Exception e )
//    {
//      this.logger.warn("Could not get Instance ID, assigning one");
//    }

    // Let's check what is out there....
    int cycle = 5;;
    try
    {
      cycle = CcdpUtils.getIntegerProperty(CcdpUtils.CFG_KEY_CHECK_CYCLE);
    }
    catch( Exception e )
    {
      this.logger.warn("Could not parse the cycle, using default (5)");
    }
    
    cycle *= 1000;
    // wait twice the cycle time to allow time to the nodes to offer resources
    this.timer = new ThreadedTimerTask(this, cycle);
    
    // Now that some resources has been allocated, we can add the tasks
    if( jobs != null )
    {
      this.logger.info("Adding initial jobs");
      for( CcdpThreadRequest request : jobs )
      {
        this.logger.info("Adding Thread Request: " + request.getThreadId() );
        this.onTask(request);
      }
    }
  }
  
  /**
   * Gets invoke by an external entity requesting to allocate tasks to this 
   * particular resource.  The tasks are allocated based on the resource session
   * id.  If the session id has not been set or there are no tasks for this 
   * particular session, then the method returns an empty list.  If there are 
   * tasks for this particular session that needs to be executed then a list
   * containing those tasks is returned
   * 
   * @param resource the VM that is available for tasking
   * @return a list of tasks if there are pending ones
   */
  public List<CcdpTaskRequest> allocateTasks( CcdpVMResource resource )
  {
    List<CcdpTaskRequest> tasks = new ArrayList<>();
    String id = resource.getInstanceId();
    String sid = resource.getAssignedSession();
    
    this.logger.info("Allocating Tasks to " + id );
    this.logger.debug(this.getSummarizedRequests());
    
    synchronized( this.resources )
    {
      if( this.resources.containsKey(id) )
      {
        this.logger.info("Found Resource, updating assigned Values");
        CcdpVMResource stored = this.resources.get(id);
        resource = this.updateAssignedValues( resource, stored );
      }
      else
      {
        if( sid == null )
        {
          sid = resource.getNodeTypeAsString();
          this.logger.info("Resource does not have SID, adding it to " +
                           "free pool: " + sid);
          resource.setAssignedSession(sid);
        }
        else
        {
          this.logger.info("Resource not found, adding it to resources");
        }
      }
      
      ResourceStatus stat = resource.getStatus();
      if( stat.equals(ResourceStatus.LAUNCHED) || 
          stat.equals(ResourceStatus.REASSIGNED) ||
          stat.equals(ResourceStatus.RUNNING) )
      {
        // store updated record
        this.resources.put(id,  resource);
      }
      else
      {
        this.logger.info("Resource is not operational: " + stat );
        this.resources.remove(id);
        return tasks;
      }
      
    }// end of resources synchronization
    
    // Is this resource single tasked and is already running?
    String rst = resource.getSingleTask();
    if( rst != null && resource.getTasks().size() >= 1 )
    {
      this.logger.info("Resource " + id + " assigned to a single task: " + rst);
      return tasks;
    }
    
    sid = resource.getAssignedSession();
    CcdpNodeType type = resource.getNodeType();
    
    // Assign the tasks for each request
    synchronized( this.requests )
    {
      for( CcdpThreadRequest req : this.requests )
      {
        this.logger.info("Checking Request " + req.getThreadId() );
        if( req.getSessionId().equals( sid ) && 
            req.getNodeType().equals( type ) )
        {
          this.logger.info("Request has the same session-id: " + sid);
          List<CcdpTaskRequest> tmp = this.assignTasks( resource, req);
          this.logger.debug("Adding " + tmp.size() + " for Request " + req.getThreadId());
          tasks.addAll( tmp );
        }
        else
        {
          String txt = "Ignoring request due to session id missmatch (" + 
                       sid + " vs " + req.getSessionId() + 
                       " or different Node Type " + type + " vs " + 
                       req.getNodeTypeAsString() + ")";
          this.logger.info(txt);
        }
      }
    }
    
    this.logger.info(tasks.size() + " Tasks were allocated to run");
    return tasks;
  }
  
  
  /**
   * Invoked when the status of a task has changed (e.g., a slave is lost and 
   * so the task is lost, a task finishes and an executor sends a status update 
   * saying so, etc). If implicit acknowledgement is being used, then 
   * returning from this callback _acknowledges_ receipt of this status update! 
   * If for whatever reason the scheduler aborts during this callback (or the 
   * process exits) another status update will be delivered (note, however, 
   * that this is currently not true if the slave sending the status update is 
   * lost/fails during that time). If explicit acknowledgements are in use, the 
   * scheduler must acknowledge this status on the driver.
   * 
   * @param taskId the unique identifier of the task that changed
   * @param state the state of the task that changed
   * 
   */
  public void taskUpdate( String taskId, CcdpTaskState state )
  {
    this.logger.info("Updating TaskStatus for: " + taskId );
    this.logger.info("State: " + state.toString() );
    
    synchronized( this.requests )
    {
      List<CcdpThreadRequest> doneThreads = new ArrayList<>();
      for( CcdpThreadRequest req : this.requests)
      {
        List<CcdpTaskRequest> toRemove = new ArrayList<>();
        for( CcdpTaskRequest task : req.getTasks() )
        {
          String jid = task.getTaskId();
          this.logger.debug("Comparing Task: " + taskId + " against " + jid);
          if( jid.equals( taskId ) )
          {
            this.logger.debug("Found Task I was looking for");
            
            boolean changed = false;
            switch ( state )
            {
              case STAGING:
              
              case RUNNING:
                task.started();
                changed = true;
                break;
              case SUCCESSFUL:
                task.succeed();
                toRemove.add(task);
                this.resetDedicatedHost(task);
                changed = true;
                this.logger.debug("Job (" + jid + ") Finished");
                break;
              case FAILED:
                task.fail();
                // if tried enough times, then remove it
                if( task.getState().equals(CcdpTaskState.FAILED))
                {
                  this.logger.info("Task Failed after enough tries, removing");
                  toRemove.add(task);
                  this.resetDedicatedHost(task);
                  changed = true;
                }
                else
                {
                  this.logger.debug("Status changed to " + task.getState());
                }
                break;
              default:
                break;
            }// end of switch statement
            
            // if there is a change in the status, send a message back
            if( changed )
            {
              this.logger.debug("Status changed to " + task.getState());
              String channel = task.getReplyTo();
              if( channel == null )
                channel = req.getReplyTo();
              
              task.setReplyTo(channel);
              // notify changes on a task
              this.handleStatusUpdate(task);
            }
            
          }// found the job
        }// for task loop
        req.removeAllTasks( toRemove );
        if( req.isDone() )
          doneThreads.add(req);
        
      }// end of the thread request loop
      
      // now need to delete all the threads that are done
      this.logger.info("Removing " + doneThreads.size() + " done Threads");
      this.requests.removeAll(doneThreads);
    }// end of synch block
  }
  
  
//  /**
//   * Allows an external entity to notify the framework that one of the resources
//   * is having issues and needs to be removed.
//   * 
//   * @param identifier the agent id or identifier to determine which resource 
//   *        is having issues
//   * 
//   */
//  public void resourceLost( String identifier )
//  {
//    this.logger.warn("Resource Lost: " + identifier);
//    
//    synchronized( this.resources )
//    {
//      String to_remove = null;
//      for( String key : this.resources.keySet() )
//      {
//        CcdpVMResource vm = this.resources.get(key);
//        String id = vm.getAgentId();
//        this.logger.debug("Comparing " + identifier + " against " + id);
//        if( identifier.equals( id ) )
//        {
//          this.logger.info("Found lost resource " + key + ", removing it");
//          to_remove = key;
//          break;
//        }
//      }
//      
//      // need to remove it outside of the iterator
//      if( to_remove != null )
//        this.resources.remove(to_remove);
//      
//    }// end of synchronized block
//    
//  }
//  
  
  /**
   * Once a Task using a dedicated host ends this method is called to reset
   * the single-tasked field in the VM.
   * 
   * @param task the tasks that ended processing and was used a dedicated VM
   */
  private void resetDedicatedHost(CcdpTaskRequest task)
  {
    synchronized( this.resources )
    {
      String hid = task.getHostId();
      String tid = task.getTaskId();
      if( hid != null && this.resources.containsKey(hid) )
      {
        CcdpVMResource res = this.resources.get(hid);
        if( res.isSingleTasked() && tid.equals(res.getSingleTask()))
        {
          this.logger.info("Resetting Dedicated Host " + hid);
          res.isSingleTasked(false);
          res.setSingleTask(null);
        }
        else
        {
          this.logger.warn("Resource " + hid + " was not single tasked!!");
        }
      }
      else
      {
        this.logger.warn("Could not find resource " + hid);
      }
    }// end of the sync block
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
      this.connection.registerProducer(channel);
      this.logger.debug("Status change, sending message to " + channel);
      TaskUpdateMessage taskMsg = new TaskUpdateMessage();
      taskMsg.setTask(task);
//      this.taskingInf.sendCcdpMessage(channel, null, taskMsg);
      this.connection.sendCcdpMessage(channel, taskMsg);
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
    String tid = task.getTaskId();
    String id = task.getHostId();
    String sid = task.getSessionId();
    this.logger.debug("Removing Task " + tid + " from session " + sid);
    
    CcdpVMResource resource = this.resources.get(id);
    if( resource != null )
    {
      // if the task was a single task, free the resource for potential uses
      if( task.getCPU() >= 100 )
        resource.setSingleTask(null);
      
      this.logger.info("Found task in " + id + " removing it");
      boolean was_removed = resource.removeTask(task);
      this.logger.debug("The Task was removed " + was_removed );
      this.checkDeallocation( sid );
    }
  }
  
  /**
   * Determines whether or not there are resources that need to be terminated
   * for a specific session id.  This method does not actually terminates any
   * of the resources it simply sets them as available.  The VMs are terminated
   * in the onEvent() method after determining whether or not the system needs
   * free resources or not.
   * 
   * @param sid the session id that has some activity and whose resources need
   *        need to be checked
   */
  private void checkDeallocation( String sid )
  {
    List<CcdpVMResource> sid_vms = this.getResourcesBySessionId(sid);
    
    // Do we need to deallocate resources?
    List<CcdpVMResource> vms = this.tasker.deallocateResource(sid_vms);
    for( CcdpVMResource vm : vms )
    {
      vm.setSingleTask(null);
      vm.setAssignedSession(vm.getNodeTypeAsString());
    }
  }
  
  
  /**
   * Implementation of the TaskingIntf interface used to receive event 
   * asynchronously.
   * 
   * It checks for available resources to execute this request.  It launches
   * new resources based on the following:
   *  
   * If the request has Session Id
   *    - Moves one Resource from the free pool and assign it to this session
   *    - Matches the minimum number of free VMs to be running at any given
   *      time.
   * 
   * If the request does not have a Session Id:
   *    - Assigns the request to the public-session and re-post it
   *    - If there is at least one public VM running, then it assigns the task
   *      to that VM
   *    - If not then it takes one of the free VM just as described above
   * 
   * @param request the CcdpThreadRequest to execute
   * 
   */
  public void onTask( CcdpThreadRequest request )
  {
    if( request == null )
    {
      this.logger.error("The request cannot be null!!");
      return;
    }
    
    // Let't take care of the CPU >= 100 unique case first
    this.logger.info("Got a new Request: " + request.toPrettyPrint() );
    
    boolean allSet = true;
    for( CcdpTaskRequest task : request.getTasks() )
    {
      String tid = task.getTaskId();
      this.logger.info("Checking Task: " + tid );
      double cpu = task.getCPU();
      if( cpu >= 100 )
      {
        this.logger.info("Working on deddicated VM task");
        CcdpVMResource res = this.getSingleResource(task);
        if( res != null )
        {
          task.setHostId(res.getInstanceId());
//          task.setSubmitted(true);
          res.setSingleTask(tid);
          synchronized(this.resources)
          {
            this.resources.put(res.getInstanceId(), res);
          }
          break;
        }
      }
      else
      {
        allSet = false;
      }
    }
    
    if( !allSet )
    {
    
    String sid = request.getSessionId(); 
    if( sid != null && sid.length() > 0 )
    {
      this.logger.info("Checking for resources assigned to " + sid);
      List<CcdpVMResource> list = this.getResources(request);
      
      int sz = list.size();
      this.logger.info("Session " + sid + " has " + sz + " VMs assigned");
      if( sz == 0 )
      {
        CcdpNodeType type = request.getNodeType();
        this.logger.info("Zero VMs available, launching one of type " + type);
        // Getting a copy rather than the actual configured object so I can 
        // modify it without affecting the initial configuration 
        CcdpImageInfo imgCfg = 
            new CcdpImageInfo(CcdpUtils.getImageInfo(type));
        imgCfg.setSessionId(sid);
        imgCfg.setMinReq(1);
        imgCfg.setMaxReq(1);
        
        List<String> launched = this.controller.startInstances(imgCfg);
        for( String id : launched )
        {
          CcdpVMResource vm = new CcdpVMResource(id);
          vm.setStatus(ResourceStatus.LAUNCHED);
          vm.setAssignedSession(sid);
          synchronized( this.resources )
          {
            this.logger.info("Adding new VM " + id);
            this.resources.put(id, vm);
          }
        }
      }
    }
    
    }
    // adding the request
    synchronized( this.requests )
    {
      this.requests.add( request );
    }
  }
  
  
  /**************************************************************************/
  /***************************************************************************/
  /***************************************************************************/
  
  /**
   * Gets a String object representing a summary of the pending requests
   * 
   * @return String object representing a summary of the pending requests
   */
  public String getSummarizedRequests()
  {
    StringBuffer buf = new StringBuffer();
    synchronized( this.requests )
    {
      if( this.requests.isEmpty() )
      {
        buf.append("No Tasks pending to run");
      }
      else
      {
        for( CcdpThreadRequest req : this.requests )
        {
          buf.append("\n++++++++++++++++++++++++++++++++++++++++++++++++++++\n");
          buf.append("Thread ID:  " + req.getThreadId() + "\n");
          buf.append("Session ID: " + req.getSessionId() + "\n");
          buf.append("Tasks:\n");
          buf.append("---------------------------------------------------------\n");
          for( CcdpTaskRequest task : req.getTasks() )
          {
            Date date = new Date(task.getLaunchedTimeMillis());
            
            buf.append("\tTask ID:     " + task.getTaskId() + "\n");
            buf.append("\tState:       " + task.getState() + "\n");
            buf.append("\tHostID:      " + task.getHostId() + "\n");
            buf.append("\tSubmitted:   " + task.isSubmitted() + "\n");
            buf.append("\tLaunched at: " + this.formatter.format(date) + "\n");
            buf.append("\tCommand:     " + task.getCommand() + "\n");
          }
          buf.append("---------------------------------------------------------\n");
        }  
      }
    }
    buf.append("\n------=============  CcdpVMResources =============------\n");
    
    synchronized( this.resources )
    {
      for( String iid : this.resources.keySet() )
      {
        CcdpVMResource res = this.resources.get(iid);
        buf.append("====================================================\n");
        buf.append("Instance ID:     " + res.getInstanceId() + "\n");
        buf.append("Session ID:      " + res.getAssignedSession() + "\n");
        buf.append("Single Task:     " + res.getSingleTask() + "\n");
        buf.append("Hostname:        " + res.getHostname() + "\n");
        buf.append("CPU Load:        " + res.getCPULoad() + "\n");
        buf.append("Free Mem:        " + ( res.getFreeMemory() / 1024 ) + "\n");
        buf.append("Status:          " + res.getStatus() + "\n");
        buf.append("Number of Tasks: " + res.getNumberTasks() + "\n");
        Date date = new Date(res.getLastAssignmentTime());
        buf.append("Last Assignment: " + this.formatter.format(date) + "\n");
        buf.append("\n----------------------------------------------------\n");
        for( CcdpTaskRequest t : res.getTasks() )
        {
          buf.append("\tTask Id:          " + t.getTaskId() + "\n");
          buf.append("\tTask State:       " + t.getState() + "\n");
          buf.append("\n----------------------------------------------------\n");
        }
        
        buf.append("\n====================================================\n");
      }
    }
    return buf.toString();
  }
  
  
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
  private List<CcdpTaskRequest> assignTasks( CcdpVMResource target, 
                                          CcdpThreadRequest req )
  {
    this.logger.debug("Assinging Tasks from Request " + req.getThreadId() );
    
    String thid = req.getThreadId();
    List<CcdpTaskRequest> assignedTasks = new ArrayList<>();
    if( req.threadRequestCompleted() )
    {
      this.logger.info("Thread " + thid + " is Complete!!");
      this.requests.remove(req);
      return assignedTasks;
    }
    
    
    // is this VM tasked to one of the tasks?
    String tasked = target.getSingleTask();
    this.logger.debug("Was VM Single Tasked? " + tasked);
//    for( CcdpTaskRequest task : req.getTasks() )
//    {
//      if( !task.isSubmitted() )
//      {
//        if( tasked != null && !tasked.equals( task.getTaskId() ) )
//        {
//          this.logger.debug("Adding task (" + task.getTaskId() + ") to list ==> " + tasked);
//          return assignedTasks;
//        }
//      }
//    }
    
    List<CcdpTaskRequest> tasks = new ArrayList<>();
    // if we have resources to run the task
    if( target != null )
    {
      if( TasksRunningMode.PARALLEL.equals( req.getTasksRunningMode() ) )
      {
        // adding all tasks
        for( CcdpTaskRequest task : req.getTasks() )
        {
          String hid = task.getHostId();
          double cpu = task.getCPU();
          String iid = target.getInstanceId();
          String tid = task.getTaskId();
          
          String txt = "Comparing Vm tasked (" + tasked +") and task "+ hid +
              " and cpu " + cpu + " on Instance " + iid + " and Task " + tid;
          this.logger.info(txt);
          
          if( !task.isSubmitted() )
          {
            // if this VM is ST
            if( tasked != null && iid.equals( hid ) )
            {
              this.logger.info("Adding Task (VM is ST) " + tid);
              tasks.add(task);
            }
            else if( hid == null && cpu < 100 )
            {
              this.logger.info("Adding Task (VM is NOT ST) " + tid);
              tasks.add(task);
            }
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
          String hid = task.getHostId();
          double cpu = task.getCPU();
          String iid = target.getInstanceId();
          String tid = task.getTaskId();
          
          String txt = "Comparing Vm tasked (" + tasked +") and task "+ hid +
              " and cpu " + cpu + " on Instance " + iid + " and Task " + tid;
          this.logger.info(txt);
          // if this VM is ST
          if( tasked != null && iid.equals( hid ) )
          {
            this.logger.info("Adding Task (VM is ST) " + tid);
            tasks.add(task);
          }
          else if( hid == null && cpu < 100 )
          {
            this.logger.info("Adding Task (VM is NOT ST) " + tid);
            tasks.add(task);
          }
        }
        else if( task == null )
        {
          this.logger.info("All tasks completed for " + thid);
          // all the tasks are submitted
          req.setTasksSubmitted(true);
        }
      }
      
//      this.logger.debug("Assigning pending tasks");
//      // Do I need to assign a whole node to the task?
//      for( CcdpTaskRequest task : tasks )
//      {
//        double cpu = task.getCPU();
//        if( cpu == 100 )
//        {
//          String hid = task.getHostId(); 
//          String tid = task.getTaskId();
//          if( hid != null )
//          {
//            this.logger.debug("Task " + tid + " previously assigned to " + hid );
//            continue;
//          }
//          this.logger.info("CPU = " + cpu + 
//                           " Assigning a Resource just for this task");
//          if( target.getTasks().size() == 0 )
//          {
//            this.logger.info("Resource " + target.getInstanceId() + 
//                             " is empty, using it");
//            target.setSingleTask( tid );
////            target.addTask(task);
//            task.setHostId(target.getInstanceId());
//          }
//          else
//          {
//            CcdpVMResource vm = this.getSingleResource( req, true );
//            if( vm != null )
//            {
//              synchronized( this.resources )
//              {
//                this.resources.put(vm.getInstanceId(), vm);
//              }
//              task.setHostId(vm.getInstanceId());
//            }
//            else
//              throw new RuntimeException("Could not start a new VM");
//          }
//          task.assigned();
//          
//        }// end of the cpu = 100 if condition
//      }// end of the tasks loop
      
      int sz = tasks.size(); 
      if( sz > 0 )
      {
        List<CcdpVMResource> vms = this.getResources(req);
        this.logger.info("Have " + sz + " Tasks to run");
        assignedTasks = this.tasker.assignTasks(tasks, target, vms);
      }
    }// the target is not null
    
    return assignedTasks;
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
    List<CcdpVMResource> list = new ArrayList<>();
    if( req == null )
    {
      this.logger.error("Cannot get resource for a NULL request");
      return list;
    }
    
    String id = req.getThreadId();
    String sid = req.getSessionId();
    
    // is there a problem with the session-id?
    if( sid == null )
    {
      this.logger.error("The Thread " + id + " does not have Session ID");
      return list;
    }

    // Is this thread done?
    if( req.threadRequestCompleted() )
    {
      this.logger.info("Thread " + id + " for Session " + sid + " Complete");
      synchronized( this.requests )
      {
        this.requests.remove(req);
      }
      return null;
    }
    
    this.logger.info("Assigning Resources to Request " + id + " Session " + sid);
    list = this.getResourcesBySessionId(sid);
    
    
    if ( this.tasker.needResourceAllocation(list) )
    {
      this.logger.info("The session id " + sid + 
                       " does not have resources, checking free");
      String typeStr = req.getNodeTypeAsString();
      this.logger.info("Looking for VM of type " + typeStr);
      List<CcdpVMResource> free_vms = this.getResourcesBySessionId(typeStr);
      
      if( free_vms.size() > 0 )
      {
        CcdpVMResource res = free_vms.get(0);
        String iid = res.getInstanceId();
        this.logger.info("Assigning VM " + res.getInstanceId() + " to " + sid);
        ResourceStatus stat = res.getStatus();
        if( ResourceStatus.LAUNCHED.equals(stat) || 
            ResourceStatus.RUNNING.equals(stat))
        {
          res.setAssignedSession(sid);
          res.setStatus(ResourceStatus.REASSIGNED);
          synchronized( this.resources )
          {
            this.resources.put(iid, res);
          }
          list.add(res);
          // took one resource, check minimum requirement again
          this.checkMinVMRequirements();
        }
        else
          this.logger.info("Reource was not assigned due to: " + stat);
      }// end of the resources loop
      else
      {
        CcdpNodeType type = req.getNodeType();
        // Getting a copy rather than the actual configured object so I can 
        // modify it without affecting the initial configuration 
        CcdpImageInfo imgInfo = 
            new CcdpImageInfo(CcdpUtils.getImageInfo(type));
        this.logger.info("Did not find an available resource, creating one");
        imgInfo.setSessionId(sid);
        imgInfo.setMinReq(1);
        imgInfo.setMaxReq(1);
        List<String> launched = this.controller.startInstances(imgInfo);
        
        for( String iid : launched )
        {
          CcdpVMResource resource = new CcdpVMResource(iid);
          resource.setStatus(ResourceStatus.LAUNCHED);
          resource.setAssignedSession(sid);
          this.logger.debug("Adding resource " + resource.toString());
          synchronized( this.resources )
          {
            this.resources.put(iid, resource);
          }
          list.add(resource);
          // had to create one, is this OK?
          this.checkMinVMRequirements();
        }        
      }
    }
    
    this.logger.debug("Returning a list of resources size " + list.size());
    // Getting all the resources for this session
    return list;
  }
  
  /**
   * Checks the minimum number of available VMs required by the framework and
   * deploy as many instances as needed.
   * 
   */
  private void checkMinVMRequirements()
  {
    this.logger.debug("Checking minimum VM requirements");
    
    // Starting the minimum number of free resources needed to run
    try
    {
      synchronized(this.resources)
      {
        for( CcdpNodeType type : CcdpNodeType.values() )
        {
          String typeStr = type.toString();
          
          CcdpImageInfo imgCfg = CcdpUtils.getImageInfo(type);
          int free_vms = imgCfg.getMinReq();
          List<CcdpVMResource> avails = this.getResourcesBySessionId( typeStr );
          int available = avails.size();
          if( free_vms > 0 )
          {
            int need = free_vms - available;
            if( need > 0 )
            {
              this.logger.info("Starting " + need + " free agents");
              List<String> launched = this.controller.startInstances(imgCfg);
            
              for( String id : launched )
              {
                CcdpVMResource resource = new CcdpVMResource(id);
                resource.setStatus(ResourceStatus.LAUNCHED);
                resource.setAssignedSession(typeStr);
                this.logger.debug("Adding resource " + resource.toString());
                this.resources.put(id, resource);
              }
              
            }// need to deploy agents
          
          }// I do need free agents
        
          // Now checking to make sure there are no more free agents than needed        
          int over = available - free_vms;
          
          int done = 0;
          List<String> terminate = new ArrayList<>();
          
          // Do it only if we have more available VMs than needed
          if( over > 0 )
          {
            for( CcdpVMResource res : avails )
            {
              if( done == over )
                break;
              
              // making sure we do not shutdown the framework node
              String id = res.getInstanceId();
              if( ResourceStatus.RUNNING.equals( res.getStatus() ) )
              {
                // it is not in the 'do not terminate' list
                if( !this.skipTermination.contains(id) )
                {
                  this.logger.info("Flagging VM " + id + " for termination");
                  if( !id.startsWith("i-test-") )
                  {
                    res.setStatus(ResourceStatus.SHUTTING_DOWN);
                    terminate.add(id);
                    done++;
                  }
                  else
                  {
                    this.logger.info("VM " + id + " is a test node, skipping termination");
                  }
                }
                else
                {
                  this.logger.info("Skipping termination " + id);
                }
                
              }// done searching for running VMs
              
            }// done with the VMs
          }
          int sz = terminate.size();
          if( sz > 0 )
          {
            this.logger.info("Terminating " + terminate.toString() );
            this.controller.terminateInstances(terminate);
          }
        
        }// end of the node types loop
         
      }// end of the sync block
    }
    catch( Exception e )
    {
      String msg = "Error parsing the integer containing initial agents. "
          + "Message " + e.getMessage();
      this.logger.error(msg);
      e.printStackTrace();
    }
  }
  
  /**
   * Searches for a resource that meets the session-id and availability based
   * on the number of tasks running.  It first look into all the resources 
   * assigned to the session-id.  If there are resources allocated, then checks
   * whether or not it has tasks running.  If the 'empty' argument is set to 
   * true then it returns a VM that is not running any task otherwise it returns
   * the first resource.
   * 
   * If it could not find a resource it checks for an available one in the
   * FREEE_SESSION list. If one is found then it re-assign that VM to the given
   * session-id and checks for the minimum VM requirements.
   * 
   * If there are none available VMs, then it launches one and checks for the 
   * minimum VMs requirements to make sure and returns a reference to the new
   * launched VM
   * 
   * @param task the job that needs to be performed alone in a VM
   *        
   * @return a resource to run tasks if found or null otherwise
   */
  private CcdpVMResource getSingleResource( CcdpTaskRequest task )
  {
    this.logger.info("Getting a dedicated VM for " + task.getTaskId());
    String sid = task.getSessionId();
    List<CcdpVMResource> list = this.getResourcesBySessionId(sid);
    this.logger.debug("Got " + list.size() + " VMs for " + sid);
    
    for( CcdpVMResource res : list )
    {
      if( res.isFree() )
      {
        this.logger.info("Found a suitable empty VM " + res.getInstanceId());
        return res;
      }
    }
    
    this.logger.info("Could not find an empty VM, checking available");
    CcdpNodeType type = task.getNodeType();
    list = this.getResourcesBySessionId( type.toString() );
    this.logger.debug("Got " + list.size() + " VMs for " + type );
    for( CcdpVMResource res : list )
    {
      ResourceStatus status = res.getStatus();
      this.logger.debug("Checking VM Status " + status);
      if( ( status.equals(ResourceStatus.LAUNCHED) ||  
            status.equals(ResourceStatus.RUNNING) ) && res.isFree() )
      {
        this.logger.info("Assigning " + res.getInstanceId() + " to " + sid);
        res.setAssignedSession(sid);
        synchronized( this.resources )
        {
          this.resources.put(res.getInstanceId(), res);
          // now that we have swapped things around, let's check empty
          this.checkMinVMRequirements();
        }
        return res;
      }
    }// end of looking for a free VM
    
    this.logger.info("Was not able to find an empty VM, launching one");
    // we should have one or more if set, so let's see...
    this.checkMinVMRequirements();
    // Getting a copy rather than the actual configured object so I can 
    // modify it without affecting the initial configuration 
    CcdpImageInfo imgCfg = 
        new CcdpImageInfo(CcdpUtils.getImageInfo(type));
    imgCfg.setSessionId(sid);
    imgCfg.setMinReq(1);
    imgCfg.setMaxReq(1);
    
    List<String> ids = this.controller.startInstances(imgCfg);
    if( ids.size() >= 1 )
    {
      CcdpVMResource vm = new CcdpVMResource( ids.get(0) );
      vm.setAssignedSession(sid);
      vm.setStatus(ResourceStatus.LAUNCHED);
      return vm;
    }
    else
    {
      this.logger.error("Was not able to launch a VM!!");
    }
    
    return null;
  }
  
  
  private boolean isSingleTaskedAssigned( CcdpThreadRequest req )
  {
    this.logger.info("Checking Single Tasking for Request " + req.getThreadId());
    
    for( CcdpTaskRequest task: req.getTasks() )
    {
      double cpu = task.getCPU();
      String hid = task.getHostId();
      String tid = task.getTaskId();
      this.logger.debug("Task " + tid + " ==> CPU " + cpu + " Host " + hid);
      if( cpu >= 100 && hid == null )
        return false;
    }
    
    this.logger.info("checkSingleTasked is returning true");
    return true;
  }
  
  
  /**
   * Gets a list of all the tasks assigned to this engine matching the given
   * state
   * 
   * @param state the desired state to find the tasks
   * 
   * @return a list of all the tasks assigned to this engine matching the given
   *         state
   */
  public List<CcdpTaskRequest> getTasksByState( CcdpTaskState state)
  {
    List<CcdpTaskRequest> tasks = new ArrayList<>();
    for( CcdpThreadRequest req : this.requests )
    {
      for( CcdpTaskRequest task : req.getTasks() )
      {
        if( task.getState().equals( state ) );
          tasks.add(task);
      }
    }
    
    return tasks;
  }
  
  /**
   * Gets all the resources belonging to the given Session Id.  If the session
   * does not contain any resource allocated then it returns an empty list.
   * 
   * @param sid the session id to get the resources from
   * 
   * @return a list of resources allocated to the session or an empty list 
   *         otherwise
   */
  private List<CcdpVMResource> getResourcesBySessionId( String sid )
  {
    List<CcdpVMResource> list = new ArrayList<>();
    if( sid == null )
      return list;
    
    for( CcdpVMResource res : this.resources.values() )
    {
      String asid = res.getAssignedSession();
      if( sid.equals(asid) && 
          !ResourceStatus.SHUTTING_DOWN.equals(res.getStatus() ) )
      {
        this.logger.debug("Found Resource based on SID, adding it to list");
        list.add(res);
      }
    }
    
    return list;
  }

  /**
   * Updates all the fields that are modified by the external resource 
   * management systems to the corresponding CCDP resource.  If either the 
   * source or the destination is null, the the method prints an error message
   * and returns the 'to' object.
   * 
   * @param from the resource whose fields were modified externally from CCDP
   * @param to the resource stored in CCDP data structure that needs to be 
   *        updated
   *        
   * @return the resource with all the fields updated
   */
  private CcdpVMResource updateAssignedValues( CcdpVMResource from ,  
                                               CcdpVMResource to )
  {
    if( from == null || to == null )
    {
      this.logger.error("Either the source or the destination object is null");
      return to;
    }
    
    to.setAssignedCPU(from.getAssignedCPU());
    to.setAssignedMEM(from.getAssignedMemory());
    to.setAgentId(from.getAgentId());
    to.setAssignedDisk(from.getAssignedDisk());
    to.setCPU(from.getCPU());
    to.setTotalMemory(from.getTotalMemory());
    ResourceStatus stat = to.getStatus();
    // only update if it was LAUNCHED
    if( ResourceStatus.LAUNCHED.equals(stat ) || 
        ResourceStatus.REASSIGNED.equals(stat ))
      to.setStatus(ResourceStatus.RUNNING);
      
    return to;
  }
  
  /**
   * Method invokes continuously to monitor the state of the system by the
   * ThreadedTimerTask object.  It determines whether or not we need to launch
   * free resources and/or terminate unused ones  
   */
  public void onEvent()
  {
    synchronized( this.resources )
    {
      this.checkMinVMRequirements();
      
      for( String key : this.resources.keySet() )
      {
        CcdpVMResource res = this.resources.get(key);
        this.checkDeallocation(res.getAssignedSession());
      }
    }
  }
  
  public void onCcdpMessage(CcdpMessage message)
  {
    CcdpMessageType msgType = CcdpMessageType.get(message.getMessageType());
    this.logger.debug("Got a " + msgType + " Message");
    switch( msgType )
    {
      case UNDEFINED:
        UndefinedMessage undMsg = (UndefinedMessage)message;
        this.logger.info("Undefined Msg: " + undMsg.getPayload().toString());
        break;
      case RESOURCE_UPDATE:
        ResourceUpdateMessage resMsg = (ResourceUpdateMessage)message;
        CcdpVMResource vm = resMsg.getCcdpVMResource();
        this.updateVMResourceUtilization(vm);
        break;
      case THREAD_REQUEST:
        ThreadRequestMessage reqMsg = (ThreadRequestMessage)message;
        CcdpThreadRequest req = reqMsg.getRequest();
        this.onTask(req);
        break;
      default:
        this.logger.error("Message Type not found");
    }
  }
  
  /**
   * Searches all the resources looking for the one whose agent-id matches
   * the given one.  If found, it returns its session-id otherwise it returns 
   * null
   * 
   * @param agentId the agent-id of the CcdpVMResource whose session-id is 
   *        needed
   * @return the session-id of the resource if found or null otherwise
   */
  public String getSessionIdFromAgentId( String agentId )
  {
    if( agentId == null )
      return null;
    
    synchronized( this.resources )
    {
      for( String iid : this.resources.keySet() )
      {
        CcdpVMResource res = this.resources.get(iid);
        if( res != null && agentId.equals( res.getAgentId() ) )
          return res.getAssignedSession();
      }
    }
    return null;
  }
  
  /**
   * Removes the resource from it's list of available resources.  This method
   * provides a way to external entities to notify the engine that this 
   * particular resource should no longer be considered part of the system.
   * 
   * If the item is found then it returns the item being removed from the list.
   * If the item is not found the method returns null.
   *  
   * @param id the unique id of the resource to remove.
   * @return the item that was removed from the resource list or null otherwise
   */
  public CcdpVMResource removeResource( String id )
  {
    this.logger.info("Resource removal request for " + id );
    synchronized( this.resources )
    {
      Set<String> keys = this.resources.keySet();
      if( keys.contains(id) )
      {
        this.logger.info("Removing " + id + " from resource list");
        return this.resources.remove(id);
      }
      
      String vmId = null;
      // was not an instance Id, let's try agent id
      for( String iid : this.resources.keySet() )
      {
        CcdpVMResource resource = this.resources.get(iid);
        String aid = resource.getAgentId();
        if( aid != null && aid.equals( id ) )
        {
          this.logger.info("Removing resource " + id);
          vmId = resource.getInstanceId();
          break;
        }
      }// end of the for loop so now we can remove the item
      
      if( vmId != null )
        return this.resources.remove(vmId);
      else
        return null;
    }// end of the synchronized block
    
  }// end of the removeResource method
  
  /**
   * Updates a resource's utilization parameters such as memory, CPU, and disk
   * 
   * @param resource The VM to update
   */
  private void updateVMResourceUtilization( CcdpVMResource resource )
  {
    synchronized( this.resources )
    {
      String iid = resource.getInstanceId();
      if( this.resources.containsKey( iid ) )
      {
        this.logger.debug("Received a heartbeat from " + iid );
        CcdpVMResource res = this.resources.get(iid);
        res.setFreeDiskSpace(resource.getFreeDiskspace());
        res.setFreeMemory(resource.getFreeMemory());
        res.setCPULoad(resource.getCPULoad());
      }
    }
  }
  
}

class AvailableVM
{
  int minReq = 0;
  CcdpNodeType nodeType = CcdpNodeType.EC2;
  String sessionId = CcdpNodeType.EC2.toString();
}