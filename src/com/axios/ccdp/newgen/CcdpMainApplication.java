package com.axios.ccdp.newgen;

import java.io.File;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.log4j.Logger;

import com.axios.ccdp.connections.intfs.CcdpConnectionIntf;
import com.axios.ccdp.connections.intfs.CcdpMessageConsumerIntf;
import com.axios.ccdp.connections.intfs.CcdpStorageControllerIntf;
import com.axios.ccdp.connections.intfs.CcdpVMControllerIntf;
import com.axios.ccdp.factory.CcdpObjectFactory;
import com.axios.ccdp.fmwk.CcdpEngine;
import com.axios.ccdp.message.AssignSessionMessage;
import com.axios.ccdp.message.CcdpMessage;
import com.axios.ccdp.message.EndSessionMessage;
import com.axios.ccdp.message.KillTaskMessage;
import com.axios.ccdp.message.ResourceUpdateMessage;
import com.axios.ccdp.message.RunTaskMessage;
import com.axios.ccdp.message.StartSessionMessage;
import com.axios.ccdp.message.TaskUpdateMessage;
import com.axios.ccdp.message.ThreadRequestMessage;
import com.axios.ccdp.message.UndefinedMessage;
import com.axios.ccdp.message.CcdpMessage.CcdpMessageType;
import com.axios.ccdp.resources.CcdpVMResource;
import com.axios.ccdp.resources.CcdpVMResource.ResourceStatus;
import com.axios.ccdp.tasking.CcdpTaskRequest;
import com.axios.ccdp.tasking.CcdpTaskRequest.CcdpTaskState;
import com.axios.ccdp.tasking.CcdpThreadRequest;
import com.axios.ccdp.utils.CcdpImageInfo;
import com.axios.ccdp.utils.CcdpUtils;
import com.axios.ccdp.utils.NumberTasksComparator;
import com.axios.ccdp.utils.TaskEventIntf;
import com.axios.ccdp.utils.ThreadedTimerTask;
import com.axios.ccdp.utils.CcdpUtils.CcdpNodeType;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

public class CcdpMainApplication implements CcdpMessageConsumerIntf, TaskEventIntf
{
  /**
   * The number of cycles to wait before declaring an agent missing.  A cycle
   * is the time to wait between checking for allocation/deallocation
   */
  public static int NUMBER_OF_CYCLES = 4;
  /**
   * Generates debug print statements based on the verbosity level.
   */
  private Logger logger = Logger.getLogger(CcdpMainApplication.class.getName());
  /**
   * Parses and prints all the options or arguments used by this application
   */
  private static HelpFormatter helpFormatter = new HelpFormatter();
  /**
   * Stores all the options that can be used by this application
   */
  private static Options options = new Options();
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
  /**
   * Stores the object that determines the logic to assign tasks to VMs
   */
  private AvgLoadControllerImpl tasker = null;
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
  private Map<String, List<CcdpVMResource>> resources = new HashMap<>();
  /**
   * Stores the instance id of the EC2 running this framework
   */
  private String hostId = null;
  /**
   * Stores a list of host ids that should not be terminated
   */
  private List<String> skipTermination = new ArrayList<>();
  /**
   * Stores a list of the differnt node types
   */
  private List<String> nodeTypes = new ArrayList<>();
  /**
   * Continuously monitors the state of the system
   */
  private ThreadedTimerTask timer = null;
  /**
   * How many seconds before an agent is considered missing or no longer 
   * reachable 
   */
  private int agent_time_limit = 20;
  
  /**
   * Instantiates a new object and if the 'jobs' argument is not null then
   * it executes all the tasks specified in the given file
   * 
   * @param json_file and optional file containing a series of tasks to execute
   *        at startup
   */
  public CcdpMainApplication( String json_file )
  {
    this.mapper.
        configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    
    // creating a list for each of the node types
    synchronized( this.resources )
    {
      for( CcdpNodeType type : CcdpNodeType.values() )
      {
        String name = type.toString();
        this.nodeTypes.add(name);
        this.resources.put(name, new ArrayList<CcdpVMResource>());
      }
    }
    
    // creating the factory that generates the objects used by the scheduler
    CcdpObjectFactory factory = CcdpObjectFactory.newInstance();
    ObjectNode task_msg_node = 
        CcdpUtils.getJsonKeysByFilter(CcdpUtils.CFG_KEY_CONN_INTF);
    ObjectNode task_ctr_node = 
        CcdpUtils.getJsonKeysByFilter(CcdpUtils.CFG_KEY_TASK_CTR);
    ObjectNode res_ctr_node = 
        CcdpUtils.getJsonKeysByFilter(CcdpUtils.CFG_KEY_RESOURCE);
    ObjectNode storage_node = 
        CcdpUtils.getJsonKeysByFilter(CcdpUtils.CFG_KEY_STORAGE);
    
    this.connection = factory.getCcdpConnectionInterface(task_msg_node);
    //this.tasker = factory.getCcdpTaskingController(task_ctr_node);
    this.tasker = new AvgLoadControllerImpl();
    this.tasker.configure(task_ctr_node);
    
    this.controller = factory.getCcdpResourceController(res_ctr_node);
    this.storage = factory.getCcdpStorageControllerIntf(storage_node);
    
    this.connection.configure(task_msg_node);
    this.connection.setConsumer(this);
    
    try
    {
      this.hostId = CcdpUtils.retrieveEC2Info("instance-id");
      this.logger.info("Framework running on instance: " + this.hostId );
    }
    catch( Exception e )
    {
      this.logger.warn("Could not get Instance ID, assigning one");
      String[] items = UUID.randomUUID().toString().split("-");
      this.hostId = "i-test-" + items[items.length - 1];
    }
    
    String toMain = CcdpUtils.getProperty(CcdpUtils.CFG_KEY_MAIN_CHANNEL);
    this.logger.info("Registering as " + this.hostId);
    this.connection.registerConsumer(this.hostId, toMain);
      
    // Skipping some nodes from termination
    String ids = CcdpUtils.getProperty(CcdpUtils.CFG_KEY_SKIP_TERMINATION);
    
    if( ids != null )
    {
      for( String id : ids.split(",") )
      {
        id = id.trim();
        this.logger.info("Skipping " + id + " from termination");
        this.skipTermination.add(id);
      }
    }// end of the do not terminate section
    
    

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
    this.agent_time_limit = cycle * CcdpEngine.NUMBER_OF_CYCLES;
    
    // wait twice the cycle time to allow time to the nodes to offer resources
    this.timer = new ThreadedTimerTask(this, 2*cycle, cycle);
    
    List<CcdpThreadRequest> requests = new ArrayList<CcdpThreadRequest>();
    File json_jobs = null;
    
    if( json_file != null )
    {
      json_jobs = new File(json_file);
      this.logger.debug("Loading File: " + json_file);
      
      // loading Jobs from the command line
      if( json_jobs.isFile() )
      {
        try
        {
          requests = CcdpUtils.toCcdpThreadRequest( json_jobs );
          this.logger.debug("Number of Jobs: " + requests.size() );
          for( CcdpThreadRequest req : requests )
            this.addRequest( req );
        }
        catch( Exception e )
        {
          this.logger.error("Could not parse Jobs file " + json_file);
          this.logger.error("Message: " + e.getMessage(), e);
        }
      }
      else
      {
        String msg = "The Json Jobs file (" + json_file + ") is invalid";
        this.logger.error(msg);
        CcdpMainApplication.usage(msg);
      }
    }
  }
  
  
  
  /**
   * Method invoked continuously by the ThreadedTimerTask object to monitor the 
   * state of the system .  It determines whether or not we need to launch free
   * resources and/or terminate unused ones  
   */
  public void onEvent()
  {
    synchronized( this.resources )
    {
      this.checkFreeVMRequirements();
      
      for( String sid : this.resources.keySet() )
      {
        // removes all resources that have failed to update
        this.removeUnresponsiveResources(sid);
        
        // don't need to check allocation or deallocation for free agents
        // as this is taken cared in the checkFreeVMRequirements() method
        if( !this.nodeTypes.contains(sid) )
        {
          this.checkAllocation( sid );
          this.checkDeallocation( sid );
        }
      }
    }
    
    this.allocateTasks();
  }
  
  /**
   * Removes all the resources that have not been updated for a while.
   * 
   * @param sid the session id to check
   */
  private void removeUnresponsiveResources(String sid)
  {
    this.logger.debug("Checking for unresponsive VMs for session " + sid);
    
    List<CcdpVMResource> list = this.getResourcesBySessionId(sid);
    List<CcdpVMResource> remove = new ArrayList<>();
    // check the last time each VM was updated
    for( CcdpVMResource vm : list )
    {
      long now = System.currentTimeMillis();
      long resTime = vm.getLastUpdatedTime();
      long diff = now - resTime;
      if( diff >= this.agent_time_limit )
      {
        String txt = "The Agent " + vm.getAgentId() + 
                     " has not sent updates since " + resTime;
        this.logger.warn(txt);
        remove.add(vm);
      }
    }
    // now we remove all the resources that are not responding
    list.removeAll(remove);    
  }
  
  /**
   * Gets a message from an external entity
   *  
   * @param message the incoming message that needs to be consumed
   */
  public void onCcdpMessage( CcdpMessage message )
  {
    
    CcdpMessageType msgType = CcdpMessageType.get( message.getMessageType() );
    this.logger.debug("Got a new Event: " + message.toString());
    switch( msgType )
    {
      case START_SESSION:
        StartSessionMessage start = (StartSessionMessage)message;
        this.startSession( start );
        break;
      case END_SESSION:
        EndSessionMessage end = (EndSessionMessage)message;
        this.checkDeallocation( end.getSessionId() );
        break;
      case RESOURCE_UPDATE:
        ResourceUpdateMessage resMsg = (ResourceUpdateMessage)message;
        this.updateResource( resMsg.getCcdpVMResource() );
        break;
      case KILL_TASK:
        KillTaskMessage killMsg = (KillTaskMessage)message;
        this.killTaskRequest(killMsg);
        break;
      case TASK_UPDATE:
        TaskUpdateMessage taskMsg = (TaskUpdateMessage)message;
        this.updateTaskStatus(taskMsg.getTask());
        break;
      case THREAD_REQUEST:
        ThreadRequestMessage reqMsg = (ThreadRequestMessage)message;
        this.addRequest( reqMsg.getRequest() );
        break;
      case UNDEFINED:
      default:
        String msg = "CcdpAgent does not process events of type " + msgType;
        this.logger.warn(msg);
    }
  }

  
  /**
   * Checks the kill task message and determines to whether terminate a single
   * task based on the task id or a series of tasks based on the name.
   * 
   *  If the task-id is not provided, then it searches through all the resources
   *  for the session and terminates matching tasks.  The termination is done
   *  first to the VMs with the lowest number of running matching tasks to the
   *  highest.  This is in order to empty running VMs quicker.
   *  
   * @param killMsg the kill task request message
   */
  private void killTaskRequest( KillTaskMessage killMsg )
  {
    CcdpTaskRequest task = killMsg.getTask();
    String tid = task.getTaskId();
    
    // if it has a taskId, then don't need to do anything just pass it
    if( tid != null )
    {
      this.logger.info("Killing task " + tid);
      this.killTask(task);
    }
    else
    {
      String name = task.getName();
      if( name == null || name.isEmpty() )
      {
        this.logger.error("The name is required");
        return;
      }
      
      int number = killMsg.getHowMany();
      this.logger.info("Killing " + number + " " + name + " tasks");
      String sid = task.getSessionId();
      List<CcdpVMResource> resources = this.getResourcesBySessionId(sid);
      if( resources != null && !resources.isEmpty() )
      {
        // let's sort the list from lowest number of tasks to higher
        Collections.sort(resources, new NumberTasksComparator(name));
        Collections.reverse(resources);
        int remaining = 0;
        boolean done = false;
        // we need to go through the resources and kill from the lower amount
        // of matching running tasks to the highest.  This is so we can free
        // resources if possible
        for( CcdpVMResource vm : resources )
        {
          if( done )
          {
            this.logger.info("Done killing tasks");
            break;
          }
          // Get the matching tasks and send a request to kill it
          for( CcdpTaskRequest toKill : vm.getTasks() )
          {
            // if it matches the name, send a kill message to the scheduler
            if( name.equals(toKill.getName() ) )
            {
              this.logger.info("Found a matching task in " + vm.getAgentId());
              this.killTask(toKill);
              
              remaining--;
              if(remaining <= 0 )
              {
                done = true;
                break;
              }
            }// end of the name matching condition
          }// end of the tasks loop
        }// end of the resources loop
        
        // if we are not done then we got more requests to terminate tasks 
        // than available
        if( !done )
        {
          this.logger.error("Got a request to kill more tasks than are " +
                            "currently running");
        }
      }// resources is not null nor empty
    }// the task id was not provided
    
  }
  
  
  
  
  /**
   * Iterates through all the requests looking for the task to kill.  If the 
   * task is found then it sends a request to the appropriate agent to terminate
   * it.
   * 
   * @param task the task to kill
   */
  private void killTask( CcdpTaskRequest task )
  {
    String tid = task.getTaskId();  
    String sid = task.getSessionId();
    
    
    this.logger.info("Killing Task: " + tid );
    synchronized( this.requests )
    {
      boolean found = false;
      for( CcdpThreadRequest req : this.requests )
      {
        if( found )
          break;
        for( CcdpTaskRequest t : req.getTasks() )
        {
          if( t.getTaskId().equals( tid ) )
          {
            String iid = t.getHostId();
            this.logger.info("Found Task to kill " + tid + " at " + iid);
            KillTaskMessage msg = new KillTaskMessage();
            msg.setTask(t);
            this.connection.sendCcdpMessage(iid, msg);
            t.killed();
            found = true;
            break;
          }
        }// end of the tasks loop
      }// end of the requests loop
    }// end of the sync block
  }
  
  /**
   * Starts a new session and allocates resources to the session if available.
   * It looks at the node type so we can reassign a node of the same type
   * 
   * @param start the message containing the information required to start a
   *        new session
   */
  private void startSession( StartSessionMessage start )
  {
    String sid = start.getSessionId();
    CcdpNodeType node = start.getNodeType();
    
    synchronized( this.resources )
    {
      if( !this.resources.containsKey(sid) )
      {
        this.logger.info("Starting new Session: " + sid);
        
        List<CcdpVMResource> list = this.resources.get( node.toString() );
        if( !list.isEmpty() )
        {
          CcdpVMResource vm = list.get(0);
          vm.setAssignedSession(sid);
         // vm.setStatus(ResourceStatus.REASSIGNED);
          String iid = vm.getInstanceId();
          List<CcdpVMResource> sidList = new ArrayList<>();
          sidList.add(vm);
          this.logger.info("Reassigning VM " + iid + " to session " + sid);
          this.resources.put(sid, sidList);
          this.checkFreeVMRequirements();
        }// found empty VM
      }
      else
      {
        this.logger.warn("Session " + sid + 
                         " already exists, ignoring start session request");
      }// it does contain a session for it
    }// end of the sync block
  }

  
  /**
   * Updates the assigned and free resources allocated to this VM.  This 
   * information is sent by the agents as heartbeats
   * 
   * @param vm the object containing the updated information
   */
  private void updateResource( CcdpVMResource vm )
  {
    String sid = vm.getAssignedSession();
    
    
    if( sid == null )
    {
      String type = vm.getNodeTypeAsString();
      String iid = vm.getInstanceId();
      
      this.logger.info(iid + " -> Session ID is null, assign VM to available as " + type);
      vm.setAssignedSession(type);
      this.connection.registerProducer(iid);
      AssignSessionMessage msg = new AssignSessionMessage();
      msg.setSessionId(type);
      this.connection.sendCcdpMessage(iid, msg);
      
      List<CcdpVMResource> list = this.getResourcesBySessionId(type);
      boolean found = false;
      for( CcdpVMResource res : list )
      {
        this.logger.info("Comparing [" + res.getInstanceId() + 
                         "] and [" + vm.getInstanceId() +"]" );
        if( res.getInstanceId().equals( vm.getInstanceId() ) )
        {
          found = true;
          break;
        }
      }
      if( !found )
      {
        this.logger.info("Was not found adding it");
        this.resources.get(type).add(vm);
      }
      return;
    }
    else  // found a session-id
    {
      for( CcdpVMResource res : this.getResourcesBySessionId(sid) )
      {
        this.logger.debug("Comparing " + res.getInstanceId() + 
                         " and " + vm.getInstanceId() );
        if( res.getInstanceId().equals(vm.getInstanceId()) )
        {
          this.logger.info("Found Resource");
 
          res.setFreeDiskSpace(vm.getFreeDiskspace());
          res.setTotalMemory(vm.getTotalMemory());
          res.setMemLoad(vm.getMemLoad());
          res.setCPULoad(vm.getCPULoad());
          // resetting all the tasks by first removing them all and then 
          // adding them

          res.removeAllTasks();
          res.getTasks().addAll(vm.getTasks());
          res.setLastUpdatedTime(System.currentTimeMillis());
          
          return;
        }
      }
      this.logger.info("Was not found adding it");
      this.resources.get(sid).add(vm);
    }// the sid is not null
    
    this.logger.info("Could not find VM " + vm.getInstanceId() );
  }
  
  /**
   * Updates the status of a given task.  This method is invoked when an agent
   * sends an update message.
   * 
   * @param task the object with the updated status of a running task
   */
  private void updateTaskStatus( CcdpTaskRequest task )
  {
    
    String tid = task.getTaskId();   
    CcdpTaskState state = task.getState();
    CcdpTaskRequest delTask = null;
    CcdpThreadRequest delThread = null;
    
    this.logger.info("Updating Task: " + tid + " Current State: " + state);
    synchronized( this.requests )
    {
      boolean found = false;
      
      for( CcdpThreadRequest req : this.requests )
      {
        if( found )
          break;
        
        CcdpTaskRequest current = req.getTask(tid);
        if( current != null )
        {
          found = true;
          switch ( state )
          {
            case STAGING:
            
            case RUNNING:
              task.started();
              this.sendUpdateMessage(task);
              break;
            case SUCCESSFUL:
              task.succeed();
              delTask = task;
              this.resetDedicatedHost(task);
              this.sendUpdateMessage(task);
              this.requests.remove();
              this.logger.info("Job (" + task.getTaskId() + ") Finished");
              break;
            case FAILED:
              task.fail();
              // if tried enough times, then remove it
              if( task.getState().equals(CcdpTaskState.FAILED))
              {
                this.logger.info("Task Failed after enough tries, removing");
                this.requests.remove();
                this.resetDedicatedHost(task);
                this.sendUpdateMessage(task);
              }
              else
              {
                this.logger.info("Status changed to " + task.getState());
              }
              
              delTask = task;
              if( req.isDone() )
              {
                delThread = req;
              }
              
              break;
            default:
              break;
          }// end of switch statement
        }// found the task
        
        // removing the task from the thread request
        if( delTask != null )
        {
          this.logger.info("Removing Task " + delTask.getTaskId() );
          req.removeTask(delTask);
          String sid = delTask.getSessionId();
          synchronized( this.resources )
          {
            for( CcdpVMResource vm : this.resources.get(sid) )
            {
              vm.removeTask(delTask);
            }
          }// end of sync block
        }// deleting task
      }// for request loop
      
      // removing the thread request
      if( delThread != null )
      {
        this.logger.info("Thread Request complete: " + delThread.getThreadId());
        this.requests.remove(delThread);
      }
    }// end of the sync request
  }
  
  private void sendUpdateMessage(CcdpTaskRequest task)
  {
    String reply = task.getReplyTo();
    if( reply != null && reply.length() > 0)
    {
      this.logger.info("Sending Status Update to " + reply);
      this.connection.registerProducer(reply);
      TaskUpdateMessage msg = new TaskUpdateMessage();
      msg.setTask(task);
      this.connection.sendCcdpMessage(reply, msg);
    }
    
  }
  
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
      String sid = task.getSessionId();
      String hid = task.getHostId();
      String tid = task.getTaskId();
      
      if( this.resources.containsKey(sid) )
      {
        for( CcdpVMResource res : this.resources.get(sid) )
        {
          if( res.getInstanceId().equals(hid))
          {
            if( res.isSingleTasked() && tid.equals(res.getSingleTask()))
            {
              this.logger.info("Resetting Dedicated Host " + hid);
              res.isSingleTasked(false);
              res.setSingleTask(null);
            }
            break;
          }
        }// resource iteration
      }// contains session-id
    }// end of the sync block
  }
  
  /**
   * First it makes sure the request is valid by testing that is not null.  If
   * is not null then it checks if the session from where this request came 
   * from has resources assigned to it.  If it does not then it gets some
   * resources allocated.  Once all that is taken cared then it allocates the
   * tasks from all the requests received until now.
   * 
   * @param request a single tasks or a series of tasks to execute
   */
  public void addRequest( CcdpThreadRequest request )
  {
    if( request == null )
    {
      this.logger.error("The request cannot be null!!");
      return;
    }
    
    // do we need to assign a new resource to this session?
    this.logger.debug("Got a new Request: " + request.toString() );
    
    // adding the request
    synchronized( this.requests )
    {
      // it checks the validity of the request and allocates resources if needed
      if( this.getResources( request ) != null )
      {
        // need to check for the special case when a Task needs a whole VM
        for( CcdpTaskRequest task: request.getTasks() )
        {
          if( task.isSubmitted() )
            continue;
          
          String tid = task.getTaskId();
          double cpu = task.getCPU();
          this.logger.info("Checking Task " + tid + " CPU " + cpu );

          if( cpu >= 100 )
          {
            this.logger.info("Allocating whole VM to Task " + tid);
            List<CcdpVMResource> list = this.getResources(request);
            for( CcdpVMResource vm : list )
            {
              // need to make sure we are running on the appropriate node
              if( !vm.getNodeType().equals(task.getNodeType() ) )
                continue;
              
              // It has not been assigned yet and there is nothing running
              if( vm.getNumberTasks() == 0 && !vm.isSingleTasked() )
              {
                String iid = vm.getInstanceId();
                vm.setSingleTask(tid);
                task.setHostId( iid );
                if( vm.getStatus().equals(ResourceStatus.RUNNING) )
                {
                  this.sendTaskRequest(task, vm );
                  continue;
                }
              }
            }
          }// the CPU is >= 100
        }// for each task
        this.requests.add( request );
      }
    }// end of the synchronization block
    
    // who can run what now???
    this.allocateTasks();
  }

  /**
   * Sends a Task request to the given resource.  It flags the Task as 
   * "submitted" and adds it to the resource's tasks list
   * 
   * @param task the task to assign to the agent
   * @param resource the resource where the agent is running
   */
  public void sendTaskRequest( CcdpTaskRequest task, CcdpVMResource resource )
  {
    String tid = task.getTaskId();
    String iid = resource.getInstanceId();
    RunTaskMessage msg = new RunTaskMessage();
    msg.setTask(task);
    this.connection.sendCcdpMessage(iid, msg);
    this.logger.info("Launching Task " + tid + " on " + iid);
    task.setSubmitted(true);
    if (!resource.getTasks().contains(task)) {
      resource.getTasks().add(task);
    }
  }
  
//  /**
//   * Sends a message to the specified queue.  The event type determines the
//   * type of message and the event is the actual event or message to send
//   * 
//   * @param queue the destination where to send the message
//   * @param type the type of message
//   * @param event the actual message to send
//   */
//  private void sendMessage(String queue, EventType type, ObjectNode event )
//  {
//    ObjectNode body = this.mapper.createObjectNode();
//    body.put("event-type", type.toString());
//    body.set("event", event);
//    this.connection.send
//    this.connection.sendMessage(queue, body);
//  }
  
  /**
   * It does the actual task allocation by going through all the requests and
   * determining where to execute them.  
   */
  public void allocateTasks()
  {
    synchronized( this.requests )
    {
      for( CcdpThreadRequest req : this.requests )
      {
        List<CcdpVMResource> resources = 
            this.getResourcesBySessionId(req.getSessionId());
        Map<CcdpVMResource, List<CcdpTaskRequest>> map = 
            this.tasker.assignTasks(req.getTasks(), resources);
        for( CcdpVMResource resource : map.keySet() )
        {
          List<CcdpTaskRequest> tasks = map.get(resource);
          this.logger.info("Tasking " + tasks.size() + 
                           " tasks to " + resource.getInstanceId());
          for( CcdpTaskRequest task : tasks )
          {
            if (task.isSubmitted())
              this.logger.warn("Task " + task.getTaskId() + " has already been submitted.");
            else
              this.sendTaskRequest(task, resource);
          }
        }
      }
    }
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
    {
      this.logger.error("Cannot get resource for a NULL request");
      return null;
    }
    
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
      synchronized( this.requests )
      {
        this.requests.remove(req);
      }
      return null;
    }
    
    List<CcdpVMResource> list = this.getResourcesBySessionId(sid);
    // now need to filter by NodeType
    List<CcdpVMResource> listByNode = new ArrayList<>();
    for( CcdpVMResource vm : list )
    {
      if( vm.getNodeType().equals(req.getNodeType()) )
        listByNode.add(vm);
    }
    
    CcdpImageInfo imgCfg = this.tasker.needResourceAllocation(listByNode); 
    if ( imgCfg != null  )
    {
      imgCfg.setSessionId(sid);
      list = this.allocateResource(imgCfg);
    }
    this.logger.debug("Returning a list of resources size " + list.size());
    // Getting all the resources for this session
    return list;
  }
  
  /**
   * Checks the amount of free or available resources that can be used.  If the
   * number of resources is below the minimum required then it starts as many 
   * resources as needed to meet this requirement.  Also if the number of 
   * resources exceeds the minimum required it terminates the unused resources.
   * 
   */
  private void checkFreeVMRequirements()
  {
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
                this.resources.get(typeStr).add(resource);
              }
            }// need to deploy agents
          }// I do need free agents
        
          // Now checking to make sure there are no more free agents than needed        
          this.logger.debug("Making sure we deallocate free nodes as well");
          int over = available - free_vms;
          
          int done = 0;
          List<String> terminate = new ArrayList<>();
          List<String> remove = new ArrayList<>();
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
                this.logger.info("Flagging VM " + id + " for termination");
                
                // it is not in the 'do not terminate' list
                if( !this.skipTermination.contains(id) )
                {
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
              
              
              // Remove the ones 'SHUTTING_DOWN'
              if( ResourceStatus.SHUTTING_DOWN.equals( res.getStatus() ) )
              {
                long now = System.currentTimeMillis();
                if( now - res.getLastAssignmentTime() >= 18000 )
                {
                  remove.add(id);
                }
              }// is it shutting down?
            }// done with the VMs
          }
        
          int sz = terminate.size();
          if( sz > 0 )
          {
            this.logger.info("Terminating " + terminate.toString() );
            this.controller.terminateInstances(terminate);
          }
          
          // remove old pending 'SHUTTING_DOWN' nodes from map
          for( String iid : remove )
          {
            this.logger.debug("Removing " + iid + " from resources map");
            this.resources.remove(iid);
          }
          
        }// end of the NodeType for loop
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
   * Determines whether or not we need to launch additional resources based 
   * on what we have currently assigned to a particular session
   * 
   * @param sid the session id that has some activity and whose resources need
   *        need to be checked
   */
  private void checkAllocation( String sid )
  {
    List<CcdpVMResource> sid_vms = this.getResourcesBySessionId(sid);
    CcdpImageInfo imgCfg = this.tasker.needResourceAllocation(sid_vms);
    if( imgCfg != null )
    {
      imgCfg.setSessionId(sid);
      this.allocateResource(imgCfg);
    }
  }
  
  /**
   * Adds more resources to the given session-id.  It first attempts to get it
   * from the available pool, if it cannot find one then it launches one.
   * 
   * @param req the request that needs resources
   * @return the updated list of resources assigned to the session
   */
  private List<CcdpVMResource> allocateResource( CcdpImageInfo imgCfg )
  {
    String sid = imgCfg.getSessionId();
    String typeStr = imgCfg.getNodeTypeAsString();
    
    this.logger.info("Allocating resources to Session " + sid);
    List<CcdpVMResource> free_vms = this.getResourcesBySessionId(typeStr);
    if( free_vms.size() > 0 )
    {
      CcdpVMResource res = free_vms.get(0);
      this.logger.info("Assigning VM " + res.getInstanceId() + " to " + sid);
      ResourceStatus stat = res.getStatus();
      if( ResourceStatus.LAUNCHED.equals(stat) || 
          ResourceStatus.RUNNING.equals(stat))
      {
        res.setAssignedSession(sid);
        //temporarily commented out since we're not using REASSIGNED for much
        //res.setStatus(ResourceStatus.REASSIGNED);
        synchronized( this.resources )
        {
          this.resources.get(sid).add(res);
        }
        // took one resource, check minimum requirement again
        this.checkFreeVMRequirements();
      }
      else
        this.logger.info("Resource was not assigned due to: " + stat);

    }
    else
    {
      CcdpNodeType type = imgCfg.getNodeType();
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
        this.logger.info("Adding resource " + resource.toString());
        synchronized( this.resources )
        {
          this.resources.get(sid).add(resource);
        }
        // had to create one, is this OK?
        this.checkFreeVMRequirements();
      }        
    }
    
    return this.resources.get(sid);
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
   * Gets all the resources belonging to the given Session Id.  If the session
   * does not contain any resource allocated then it returns an empty list. 
   * VMs with the status of SHUTTING_DOWN are excluded from the list
   * 
   * @param sid the session id to get the resources from
   * 
   * @return a list of resources allocated to the session or an empty list 
   *         otherwise
   */
  private List<CcdpVMResource> getResourcesBySessionId( String sid )
  {
    this.logger.debug("Getting resources for [" + sid +"]");
    if( sid == null )
    {
      this.logger.error("The Session ID cannot be null");
      return null;
    }
    
    List<CcdpVMResource> list = new ArrayList<>();
    synchronized( this.resources )
    {
      if( this.resources.containsKey( sid ) )
      {
        this.logger.debug("Found them, checking status");
        List<CcdpVMResource> assigned = this.resources.get(sid);
        for( CcdpVMResource res : assigned )
        {
          if( !ResourceStatus.SHUTTING_DOWN.equals(res.getStatus() ) )
          {
            this.logger.debug("Found Resource based on SID, adding it to list");
            list.add(res);
          }
          else
          {
            this.logger.debug("Status was " + res.getStatus());
          }
        }
      }// found a list of sessions
      else
      {
        this.logger.info("Can't find session-id in resources, adding it");
        this.resources.put(sid,  list);
      }
    }// end of synch list
    
    return list;
  }
  
  
  /**
   * Prints a message indicating how to use this framework and then quits
   * 
   * @param msg the message to display on the screen along with the usage
   */
  private static void usage(String msg) 
  {
    if( msg != null )
      System.err.println(msg);
    
    helpFormatter.printHelp(CcdpMainApplication.class.toString(), options);
    System.exit(1);
  }
  
  /**
   * Starts an agent to execute commands sent through the CcdpConnectionIntf
   * protocol
   * 
   * @param args the command line arguments
   * @throws Exception an exception is thrown if an error occurs
   */
  public static void main(String[] args) throws Exception
  {
    // building all the options available
    String txt = "Path to the configuration file.  This can also be set using "
        + "the System Property 'ccdp.config.file'";
    Option config = new Option("c", "config-file", true, txt);
    config.setRequired(false);
    options.addOption(config);
    
    Option jobs = new Option("f", "file-jobs", true, 
        "Optional JSON file with the jobs to run");
    jobs.setRequired(false);
    options.addOption(jobs);

    Option help = new Option("h", "help", false, "Shows this message");
    help.setRequired(false);
    options.addOption(help);

    
    CommandLineParser parser = new DefaultParser();
    
    CommandLine cmd;

    try 
    {
      cmd = parser.parse(options, args);
    } 
    catch (ParseException e) 
    {
      System.out.println(e.getMessage());
      helpFormatter.printHelp("utility-name", options);

      System.exit(1);
      return;
    }
    // if help is requested, print it an quit
    if( cmd.hasOption('h') )
    {
      helpFormatter.printHelp(CcdpAgent.class.toString(), options);
      System.exit(0);
    }
    String cfg_file = null;
    String key = CcdpUtils.CFG_KEY_CFG_FILE;
    String jobs_file = null;
    
    // do we have a valid job file?
    if( cmd.hasOption('f') )
    {
      String fname = CcdpUtils.expandVars( cmd.getOptionValue('f') );
      File file = new File( fname );
      if( file.isFile() )
        jobs_file = fname;
      else
        usage("The jobs file (" + fname + ") was provided, but is invalid");
    }
    
    // do we have a configuration file? if not search for the System Property
    boolean loaded = false;
    if( cmd.hasOption('c') )
    {
      cfg_file = cmd.getOptionValue('c');
    }
    else if( System.getProperty( key ) != null )
    {
      String fname = CcdpUtils.expandVars(System.getProperty(key));
      File file = new File( fname );
      if( file.isFile() )
        cfg_file = fname;
      else
        usage("The config file (" + fname + ") is invalid");
    }
    
    // If it was not specified, let's try as part of the classpath using the
    // default name stored in CcdpUtils.CFG_FILENAME
    if( cfg_file == null )
    {
      String name = CcdpUtils.CFG_FILENAME;
      URL url = CcdpUtils.class.getClassLoader().getResource(name);
      
      // making sure it was found
      if( url != null )
      {
        System.out.println("Configuring CCDP using URL: " + url);
        CcdpUtils.loadProperties( url.openStream() );
        loaded = true;
      }
      else
      {
        System.err.println("Could not find " + name + " file");
        usage("The configuration is null, but it is required");
      }
    }
    
    if( !loaded )
      CcdpUtils.loadProperties(cfg_file);
    
    CcdpUtils.configLogger();
    new CcdpMainApplication(jobs_file);
    
  }
}
