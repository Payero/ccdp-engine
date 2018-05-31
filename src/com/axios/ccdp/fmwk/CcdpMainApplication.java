package com.axios.ccdp.fmwk;

import java.io.File;
import java.net.URL;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
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
import com.axios.ccdp.controllers.CcdpVMControllerAbs;
import com.axios.ccdp.factory.CcdpObjectFactory;
import com.axios.ccdp.messages.AssignSessionMessage;
import com.axios.ccdp.messages.CcdpMessage;
import com.axios.ccdp.messages.EndSessionMessage;
import com.axios.ccdp.messages.ErrorMessage;
import com.axios.ccdp.messages.KillTaskMessage;
import com.axios.ccdp.messages.ResourceUpdateMessage;
import com.axios.ccdp.messages.RunTaskMessage;
import com.axios.ccdp.messages.ShutdownMessage;
import com.axios.ccdp.messages.StartSessionMessage;
import com.axios.ccdp.messages.TaskUpdateMessage;
import com.axios.ccdp.messages.ThreadRequestMessage;
import com.axios.ccdp.messages.CcdpMessage.CcdpMessageType;
import com.axios.ccdp.resources.CcdpImageInfo;
import com.axios.ccdp.resources.CcdpVMResource;
import com.axios.ccdp.resources.CcdpVMResource.ResourceStatus;
import com.axios.ccdp.tasking.CcdpTaskRequest;
import com.axios.ccdp.tasking.CcdpTaskRequest.CcdpTaskState;
import com.axios.ccdp.tasking.CcdpThreadRequest;
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
   * Defines the prefix to be appended to the instance id of a testing VM node
   */
  public static final String VM_TEST_PREFIX = "i-test";
  /**
   * The number of cycles to wait before declaring an agent missing.  A cycle
   * is the time to wait between checking for allocation/deallocation
   */
  public static int NUMBER_OF_CYCLES = 16;
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
      new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
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
  //private AvgLoadControllerImpl tasker = null;
  private CcdpVMControllerAbs tasker = null;
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
   * How many milliseconds before an agent is considered missing or no longer
   * reachable
   */
  private int agent_time_limit = 80000; //1 min 20 sec
  /**
   * Flag indicating whether or not heartbeats are being ignored
   */
  private boolean skip_hb = false;
  /**
   * Formats a double to show only three decimal places
   */
  private DecimalFormat decFmt = new DecimalFormat("###.##");

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

    this.tasker = factory.getCcdpTaskingController(task_ctr_node);

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
      this.hostId =
          CcdpMainApplication.VM_TEST_PREFIX + "-" + items[items.length - 1];
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

    this.skip_hb =
        CcdpUtils.getBooleanProperty(CcdpUtils.CFG_KEY_SKIP_HEARTBEATS);

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
    this.agent_time_limit = cycle * CcdpMainApplication.NUMBER_OF_CYCLES;

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

    this.logger.info("System ready, waiting for events...");
  }



  /**
   * Method invoked continuously by the ThreadedTimerTask object to monitor the
   * state of the system .  It determines whether or not we need to launch free
   * resources and/or terminate unused ones
   */
  public void onEvent()
  {
    this.logger.trace("Checking Resources");
    synchronized( this.resources )
    {
      this.checkFreeVMRequirements();

      for( String sid : this.resources.keySet() )
      {
        if( !this.skip_hb )
        {
          // removes all resources that have failed to update
          this.removeUnresponsiveResources(sid);
        }

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
    this.logger.trace("Checking for unresponsive VMs for session " + sid);

    List<CcdpVMResource> list = this.getResourcesBySessionId(sid);
    List<CcdpVMResource> remove = new ArrayList<>();
    // check the last time each VM was updated
    for( CcdpVMResource vm : list )
    {
      try
      {
        // we only care for those running
        if( !ResourceStatus.RUNNING.equals(vm.getStatus()) )
        {
          String id = vm.getInstanceId();
          //String st = vm.getStatus().toString();
          //this.logger.trace("Ignoring unresponsive VM " + id + ", Status: " + st);
          continue;
        }
        long now = System.currentTimeMillis();
        long resTime = vm.getLastUpdatedTime();
        long diff = now - resTime;
        if( diff >= this.agent_time_limit )
        {
          String txt = "The Agent " + vm.getInstanceId() + " Status " + vm.getStatus() +
                       " has not sent updates since " + this.formatter.format(new Date(resTime));
          this.logger.debug(txt);
          this.logger.debug("It has been " + (diff/1000) + " seconds since we got last heartbeat");
          remove.add(vm);
        }
      }
      catch( Exception e)
      {
        this.logger.warn("unresponsive VM caused exception, removing it " + e.getMessage() );
        e.printStackTrace();
        remove.add(vm);
      }
    }

    // now we remove all the resources that are not responding and update the
    // corresponding list
    list.removeAll(remove);
    this.resources.put(sid, list);

  }

  /**
   * Gets a message from an external entity
   *
   * @param message the incoming message that needs to be consumed
   */
  public void onCcdpMessage( CcdpMessage message )
  {

    CcdpMessageType msgType = CcdpMessageType.get( message.getMessageType() );
    this.logger.trace("Got a new Event: " + message.toString());
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
      case ERROR_MSG:
        ErrorMessage errorMsg = (ErrorMessage)message;
        this.logger.error(errorMsg.getErrorMessage());
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
            msg.setTask(task);
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
   * Checks if there are any available default resources and assigns it
   * to the session that needs it if possible
   *
   * @param type the node type to allocate
   * @param sid the session we're giving a resource to
   */
  private CcdpVMResource giveAvailableResource(CcdpNodeType type, String sid)
  {
    this.logger.debug("Given away available resource for " + sid);

    //Check if there are any available resources in default
    List<CcdpVMResource> def = this.getResourcesBySessionId(type.toString());
    if (def.size() > 0)
    {
      // Get the first available DEFAULT resource and give it to the session
      for (CcdpVMResource res : def)
      {
        if (res.isFree())
        { //Found a free resource
            this.changeSession(res, sid);
          return res;
        }
      }//end check default resources
    }
    else
    {
      this.logger.info("Failed to assign a resource. None available.");
    }
    return null;
  }

  /**
   * Changes a resource's session to who is using it. Updates the resource
   * list to reflect what is available.
   *
   * @param vm the resource that is changing its assigned session
   * @param sid the session id to be changed to
   */
  private void changeSession(CcdpVMResource vm, String sid)
  {
    synchronized( this.resources )
    {
      //removing from default resource and assigning to the new session
      String currSid = vm.getAssignedSession();
      boolean removed = this.resources.get(currSid).remove(vm);
      this.logger.debug("VM Was removed from " + currSid + ": " + removed);
      vm.setAssignedSession(sid);
      this.resources.get(sid).add(vm);
    }

    //Update the agent of the change
    String iid = vm.getInstanceId();
    this.logger.info(iid + " -> Assign VM to available for session " + sid);
    AssignSessionMessage msg = new AssignSessionMessage();
    CcdpImageInfo img = CcdpUtils.getImageInfo(vm.getNodeType());

    msg.setSessionId(sid);
    msg.setAssignCommand(img.getAssignmentCommand());
    this.connection.sendCcdpMessage(iid, msg);
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
    String cpu = this.decFmt.format( vm.getCPULoad() * 100 );
    
    this.logger.debug("Updating " + vm.getInstanceId() + " Session ID " + sid +
                     " status " + vm.getStatus() + " CPU: " + cpu + 
                     "% MEM: " + vm.getMemLoad() );

    if( sid == null )
    {
      String type = vm.getNodeTypeAsString();
      String iid = vm.getInstanceId();

      this.logger.info(iid + " -> Session ID is null, assign VM to available as " + type);

      vm.setAssignedSession(type);
      this.connection.registerProducer(iid);
     // this.connection.registerConsumer(iid, iid);
      AssignSessionMessage msg = new AssignSessionMessage();
      CcdpImageInfo img = CcdpUtils.getImageInfo(vm.getNodeType());
      String cmd = img.getAssignmentCommand();
      msg.setSessionId(type);
      msg.setAssignCommand(cmd);

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
      String iid = vm.getInstanceId();
      this.logger.trace("Updating Agent " + iid);
      for( CcdpVMResource res : this.getResourcesBySessionId(sid) )
      {
        this.logger.trace("Comparing " + res.getInstanceId() +
                         " and " + iid );
        if( res.getInstanceId().equals(iid) )
        {
          this.logger.trace("Updating Agent " + iid);

          res.setFreeDiskSpace(vm.getFreeDiskspace());
          res.setTotalMemory(vm.getTotalMemory());
          res.setMemLoad(vm.getMemLoad());
          res.setCPULoad(vm.getCPULoad());
          
          if( ResourceStatus.LAUNCHED.equals(res.getStatus() ) )
          {
            this.logger.info("Changing Status from LAUNCHED to RUNNING");
            res.setStatus(ResourceStatus.RUNNING);
          }
          // resetting all the tasks by first removing them all and then
          // adding them
          res.removeAllTasks();
          res.getTasks().addAll(vm.getTasks());

          //TODO: Clean up any tasks that are successful or failed here

          res.setLastUpdatedTime(System.currentTimeMillis());

          return;
        }
      }
      this.logger.info(iid + " was not found in SID: " + sid + " adding it");
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
    boolean delTask = false;
    CcdpThreadRequest delThread = null;
    this.logger.info("Updating Task: " + tid + " Current State: " + state);
    synchronized( this.requests )
    {
      boolean found = false;
      for( CcdpThreadRequest req : this.requests )
      {
        if( found ) {
          break;
        }

        CcdpTaskRequest current = req.getTask(tid);
        if( current != null )
        {
          found = true;
          switch ( state )
          {
            case STAGING:
              break;
            case RUNNING:
              task.started();
              this.sendUpdateMessage(task);
              break;
            case SUCCESSFUL:
              task.succeed();
              delTask = true;
              this.resetDedicatedHost(task);
              this.sendUpdateMessage(task);
              req.removeTask(task);

              if (req.threadRequestCompleted())
              {
                this.requests.remove(req);
              }

              this.logger.info("Job (" + task.getTaskId() + ") Finished");
              break;
            case FAILED:
              task.fail();
              // if tried enough times, then remove it
              if( task.getState().equals(CcdpTaskState.FAILED) )
              {
                this.logger.info("Task Failed after enough tries, removing");
                delTask = true;
                req.removeTask(task);
                //this.requests.remove(req);
                this.resetDedicatedHost(task);
                this.sendUpdateMessage(task);
              }
              else
              {
                this.logger.info("Status changed to " + task.getState());
              }

              delTask = true;
              if( req.isDone() )
              {
                delThread = req;
                this.logger.info("Deleted thread :::: " + delThread.getName());
              }

              break;
            default:
              break;
          }// end of switch statement
        }// found the task

        String rid = req.getThreadId();
        if( req.isDone() )
        {
          this.logger.info("Request " + rid + " is done");
          delThread = req;
        }
        else
        {
          this.logger.info("Thread Request " + rid + " still has " +
                           req.getPendingTasks() + " tasks pending");
        }


        // updating task state and removing the task from the thread request
        List<CcdpVMResource> update = new ArrayList<>();
        synchronized( this.resources )
        {
          for( CcdpVMResource vm : this.resources.get(task.getSessionId()) )
          {
            if (vm.getTasks().contains(task))
            {
              vm.updateTaskState(task);
              if (delTask) {
                this.logger.info("Removing Task " + task.getTaskId() );
                req.removeTask(task);
                vm.removeTask(task);
                //If there are no more tasks, mark the resource as available again
                if (vm.getNumberTasks() == 0)
                {
                  update.add(vm);
                }
              }
            }
          }// end of updating resource blocks
        }// end of sync block
        // Mark any resources with 0 tasks left as available
        for (CcdpVMResource res : update)
        {
          this.logger.debug("Marking vm " + res.getInstanceId() + " available as: " + res.getNodeTypeAsString());
          // saw a case when a VM was started for a specific SID and reassigned
          // to default before the first HB was sent which made the status be
          // LAUNCHED
          res.setStatus(ResourceStatus.RUNNING);
          this.changeSession(res, res.getNodeTypeAsString());
        }

      }// for request loop

      // removing the thread request
      if( delThread != null )
      {
        this.logger.info("Thread Request complete: " + delThread.getThreadId());
        this.requests.remove(delThread);
      }
    }// end of the sync request

    this.showSystemChange();

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
    // adding the request
    synchronized( this.requests )
    {
      // it checks the validity of the request
      if( this.isValidRequest( request ) )
      {
        // do we need to assign a new resource to this session?
        this.logger.debug("Got a new Request: " + request.toPrettyPrint() );

        // need to check for the special case when a Task needs a whole VM
        for( CcdpTaskRequest task: request.getTasks() )
        {
          if( task.isSubmitted() )
            continue;

          String tid = task.getTaskId();
          CcdpNodeType type = task.getNodeType();

          double cpu = task.getCPU();
          this.logger.info("Checking Task " + tid + " CPU " + cpu );
          boolean fail = true;

          if( cpu >= 100 )
          {
            this.logger.info("Allocating whole VM to Task " + tid);
            List<CcdpVMResource> list = this.getResources(request);
            String requestSID = request.getSessionId();
            //check if list is empty or not
            if (list.size() == 0)
            {
              //Check on node type for resources and give it one if available
              CcdpVMResource vm =
                    this.giveAvailableResource(type, requestSID);
              //For some reason when trying to add the vm to list. the vm is added twice to the session list in this.resources
              //But this changes does not reflects in the actual variable list. 
              //For this reason the issue is resolve by assigning list equals to the list in the resources.
              if( vm != null )
                list = this.resources.get(requestSID);
            }

            for( CcdpVMResource vm : list )
            {
              // It has not been assigned yet and there is nothing running
              this.logger.debug("The number of task in the vm is " + vm.getNumberTasks());
              this.logger.debug("The vm is single tasked: " + vm.isSingleTasked());
              if( vm.getNumberTasks() == 0 && !vm.isSingleTasked() )
              {
                String iid = vm.getInstanceId();
                vm.setSingleTask(tid);
                vm.setAssignedSession(task.getSessionId());
                task.setHostId( iid );
                this.logger.debug("The task HostID was set to " + task.getHostId());
                if( vm.getStatus().equals(ResourceStatus.RUNNING) ||
                    vm.getStatus().equals(ResourceStatus.LAUNCHED))
                {
                  fail = false;
                  this.sendTaskRequest(task, vm );
                  continue;
                }
                else
                  this.logger.info("Resource wasn't running :(  the resource was " + vm.getStatus());
              }
            }

            // Couldn't find any resources assigned to the request
            // Check available resources or create a new one.
            if (fail)
            {
              CcdpImageInfo imgCfg = CcdpUtils.getImageInfo(type);
              //imgCfg.setSessionId( task.getSessionId() );
              list = this.allocateResource(imgCfg,task.getSessionId() ); //updated list
              this.logger.debug("The size of the list after allocating Resources is " + list.size());
              //get the new resource we created
              for( CcdpVMResource vm : list )
              {
                this.logger.debug("The vm NodeType is " + vm.getNodeType() + "the task type is " + type);
                // need to make sure we are running on the appropriate node
                if( !vm.getNodeType().equals( type ) )
                  continue;
                // It has not been assigned yet and there is nothing running
                this.logger.debug("The vm " + vm.getInstanceId() + "is running " + vm.getNumberTasks() + "taks and is single tasked =  " + vm.isSingleTasked());
                if( vm.getNumberTasks() == 0 && !vm.isSingleTasked() )
                {
                  String iid = vm.getInstanceId();
                  vm.setSingleTask(tid);
                  task.setHostId( iid );
                  this.logger.debug("The task HostID was set to " + task.getHostId());
                  if( vm.getStatus().equals(ResourceStatus.RUNNING) ||
                      vm.getStatus().equals(ResourceStatus.LAUNCHED))
                  {
                    this.logger.info("Successfully assigned VM: " + iid + " to Task: " + task.getTaskId());
                    this.sendTaskRequest(task, vm );
                    continue;
                  }
                }
              }// end of for loop iteration
            }// if we couldn't find a resource
          }// the CPU is >= 100
        }// for each task
        this.requests.add( request );
      }
    }// end of the synchronization block

    // who can run what now???
    this.allocateTasks();
  }

  /**
   * Makes sure the request is valid.  it checks for null values, session id,
   * tasks completed, and so on
   *
   * @param req the thread request to check
   * @return true if the request is valid and contains tasks to be executed
   */
  private boolean isValidRequest( CcdpThreadRequest req )
  {
    boolean is_valid = true;
    if( req == null )
    {
      this.logger.error("Cannot get resource for a NULL request");
      is_valid = false;;
    }

    String sid = req.getSessionId();
    String id = req.getThreadId();

    // is there a problem with the session-id?
    if( sid == null )
    {
      this.logger.error("The Thread " + id + " does not have Session ID");
      is_valid = true;;
    }

    // have all the tasks been submitted already?
    if( req.areTasksSubmitted() )
    {
      this.logger.info("All the tasks have been submitted for " + id);
      is_valid = true;;
    }

    // Is this thread done?
    if( req.threadRequestCompleted() )
    {
      this.logger.info("Thread " + id + " for Session " + sid + " Complete");
      synchronized( this.requests )
      {
        this.requests.remove(req);
      }
      is_valid = true;;
    }

    return is_valid;
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
    if(task.getCPU() >= 100 )
    {
      resource.isSingleTasked(true);
      resource.setSingleTask(tid);
    }
    this.connection.sendCcdpMessage(iid, msg);
    this.logger.info("Launching Task " + tid + " " + task.getState() + " on " + iid);
    task.setSubmitted(true);
    if (!resource.getTasks().contains(task))
    {
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
        // if all the tasks have been submitted, then continue
        if( req.areTasksSubmitted() )
          continue;

        // check to see if all the tasks have been submitted, if so then set
        // the flag in the request so next time is skipped by the if above
        boolean all_submitted = true;
        for( CcdpTaskRequest t : req.getTasks() )
        {
          if( !t.isSubmitted() )
          {
            all_submitted = false;
            break;
          }
        }

        if( all_submitted )
        {
          req.setTasksSubmitted(true);
          continue;
        }

        String sid = req.getSessionId();
        this.logger.debug("Allocating resources to " + sid);
        List<CcdpVMResource> resources = this.getResourcesBySessionId(sid);
        // could not find any available resource
        if ( resources.isEmpty() )
        {
          this.logger.info("No resources available for Session:: " + sid +
                           ". Assigning one from available resources.");

          // Need to assign VMs based on the node type
          List<CcdpNodeType> types = new ArrayList<>();
          for(CcdpTaskRequest task: req.getTasks() )
          {
            CcdpNodeType type = task.getNodeType();
            //Don't want to find resources for a task that already has one
            if (task.getHostId() != null && !task.getHostId().equals("")) {
              continue;
            }
            // if we have not done it before, let's check/create one
            if( !types.contains(type) )
            {
              this.logger.debug("Checking for free node of type " + type);
              types.add(type);
              //Check if there are any available resources in free pool
              List<CcdpVMResource> free =
                                this.getResourcesBySessionId(type.toString());

              // only launch one if there are none available
              if( free.isEmpty() )
              {
                this.logger.info("No VMs Available, launching one " + type);
                CcdpImageInfo imgCfg = CcdpUtils.getImageInfo(type);

                resources = this.startInstances(imgCfg);

              }// end of no free resource
              else
              {
                this.logger.info("Reassigning VM to " + sid);
                // Get the first available FREE resource and give it to the sid
                for (CcdpVMResource res : free)
                {
                  if (res.isFree())
                  {
                    //Found a free resource
                    this.logger.debug("Reassigning " + res.getInstanceId() +
                                " " + res.getAssignedSession() + " to " + sid);
                    this.changeSession(res, sid);
                    // resource is added in the changeSession method, so need
                    // to update list
                    resources = this.resources.get(sid);
                    break;
                  }
                }//end of for loop
              }// have some free resources
            }// end of the node type check
          }// end of the task checking
        }//end reassigning resources

        this.logger.debug("Found " + resources.size() + " assigned VMs");
        for( CcdpVMResource v : resources )
          this.logger.info("Resource ID " + v.getInstanceId());

        Map<CcdpVMResource, List<CcdpTaskRequest>> map =
            this.tasker.assignTasks(req.getTasks(), resources);

        int num_tasks = req.getPendingTasks();
        int num_map = map.size();
        if( num_tasks > 0 && num_map == 0 )
        {
          List<CcdpNodeType> typesLaunched = new ArrayList<>();
          this.logger.info("Need to run " + num_tasks + " but no VM available");
          for( CcdpTaskRequest task : req.getTasks() )
          {
            CcdpNodeType type = task.getNodeType();
            if( typesLaunched.contains(type) )
            {
              this.logger.info("Already launched one of same type, skipping it");
              continue;
            }
            typesLaunched.add(type);

            // Getting a copy rather than the actual configured object so I can
            // modify it without affecting the initial configuration
            CcdpImageInfo imgInfo =
                CcdpImageInfo.copyImageInfo(CcdpUtils.getImageInfo(type));
            this.logger.info("Did not find an available resource, creating one");
            imgInfo.setSessionId(sid);
            imgInfo.setMinReq(1);
            imgInfo.setMaxReq(1);
            this.startInstances(imgInfo);
          }
          // Once some new VMs are started we need to start all over again
          this.allocateTasks();
        }

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
    String id = req.getThreadId();
    String sid = req.getSessionId();
    this.logger.info("Attempting to assign resources to Request " + id +
                     " Session " + sid);

    List<CcdpVMResource> list = this.getResourcesBySessionId(sid);
    // now need to filter by NodeType
    List<CcdpVMResource> listByNode = new ArrayList<>();
    for( CcdpVMResource vm : list )
    {
      if( vm.getNodeType().equals(req.getNodeType()) )
        listByNode.add(vm);
    }

    //Allocating resources to meet min req
    CcdpImageInfo imgCfg = this.tasker.allocateResources(listByNode);
    if ( imgCfg != null  )
    {
      //imgCfg.setSessionId(sid);
      list = this.allocateResource(imgCfg, sid);
    }
    this.logger.info("Returning a list of resources size " + list.size());
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
              for (int i = 0; i < need; i++)
              {
                this.logger.info("Starting vm number: " + i  + 
                            " free agents to meet the minreq of " + free_vms);
                this.logger.debug("The node type is " + typeStr + " and the image session is " + imgCfg.getSessionId());
                this.startInstances(imgCfg);
              }
            }// need to deploy agents
          }// I do need free agents

          // Now checking to make sure there are no more free agents than needed
          this.logger.trace("Making sure we deallocate free nodes as well");

          int over = available - free_vms;
          int done = 0;
          List<String> terminate = new ArrayList<>();
          List<CcdpVMResource> remove = new ArrayList<>();
          // Do it only if we have more available VMs than needed
          if( over > 0 )
          {
            ShutdownMessage shutdownMsg = new ShutdownMessage();

            for( CcdpVMResource res : avails )
            {
              if( done == over )
                break;

              // making sure we do not shutdown the framework node
              String id = res.getInstanceId();
              if( ResourceStatus.RUNNING.equals( res.getStatus() ))
              {
                // it is not in the 'do not terminate' list
                if( !this.skipTermination.contains(id) )
                {
                  if( !id.startsWith(CcdpMainApplication.VM_TEST_PREFIX) )
                  {
                    if (res.getNumberTasks() > 0) {
                      this.logger.info(res.getInstanceId() +
                          " still has " + res.getNumberTasks() + " tasks, skipping termination");
                      continue;
                    }
                    res.setStatus(ResourceStatus.SHUTTING_DOWN);
                    terminate.add(id);
                    this.connection.sendCcdpMessage(id, shutdownMsg);
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

              // Remove the ones 'SHUTTING_DOWN' and 'TERMINATED'
              ResourceStatus status = res.getStatus();
              if( ResourceStatus.SHUTTING_DOWN.equals( status ) ||
                  ResourceStatus.TERMINATED.equals( status ) )
              {
                long now = System.currentTimeMillis();
                if( now - res.getLastAssignmentTime() >= 18000 )
                {
                  remove.add(res);
                }
              }// is it shutting down?
            }// done with the VMs
          }

          if( terminate.size() > 0 )
          {
            this.logger.info("Terminating " + terminate.toString() );
            this.controller.terminateInstances(terminate);
            this.showSystemChange();
          }

          // remove old pending 'SHUTTING_DOWN' nodes from map
          for( CcdpVMResource res : remove )
          {
            this.logger.debug("Removing " + typeStr + " from resources map");
            this.resources.get(typeStr).remove(res);

            this.showSystemChange();
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
    this.logger.trace("Checking resource allocation");
    List<CcdpVMResource> sid_vms = this.getResourcesBySessionId(sid);
    CcdpImageInfo imgCfg = this.tasker.allocateResources(sid_vms);
    if( imgCfg != null )
    {
      imgCfg.setSessionId(sid);
      this.allocateResource(imgCfg, sid);
    }
  }

  /**
   * Adds more resources to the given session-id.  It first attempts to get it
   * from the available pool, if it cannot find one then it launches one.
   *
   * @param req the request that needs resources
   * @return the updated list of resources assigned to the session
   */
  private List<CcdpVMResource> allocateResource( CcdpImageInfo imgCfg , String sid)
  {
    //String sid = imgCfg.getSessionId();
    String typeStr = imgCfg.getNodeTypeAsString();
    this.logger.debug("the node type is " + typeStr );
    //Changing sid null to DEFAULT type
    if (sid == null)
      sid = typeStr;

    this.logger.info("Trying to allocate resources to Session " + sid);
    List<CcdpVMResource> free_vms = this.getResourcesBySessionId(typeStr);
    if( free_vms.size() > 0 )
    {
      CcdpVMResource res = free_vms.get(0);
      ResourceStatus stat = res.getStatus();
      if( ResourceStatus.LAUNCHED.equals(stat) ||
          ResourceStatus.RUNNING.equals(stat))
      {
        this.logger.info("Success: Assigning VM " + res.getInstanceId() + " to " + sid);
        //res.setAssignedSession(sid);
        //temporarily commented out since we're not using REASSIGNED
        //res.setStatus(ResourceStatus.REASSIGNED);
        synchronized( this.resources )
        {
          this.printResources();
          this.logger.debug("Adding " + res.getInstanceId() + " to " + sid);
          // need to remove it from the free pool otherwise is just a copy
          //CcdpVMResource removed = this.resources.get(typeStr).remove(0);
          //this.resources.get(sid).add(removed);
          
          this.changeSession(res, sid);
        }
        // took one resource, check minimum requirement again
        //this.checkFreeVMRequirements();
      }
      else
        this.logger.info("Resource was not assigned due to: " + stat);

    }
    else  // there are no free vm available
    {
      CcdpNodeType type = imgCfg.getNodeType();
      // Getting a copy rather than the actual configured object so I can
      // modify it without affecting the initial configuration
      CcdpImageInfo imgInfo =
          CcdpImageInfo.copyImageInfo(CcdpUtils.getImageInfo(type));
      this.logger.info("Did not find an available resource, creating one");
      imgInfo.setSessionId(sid);
      imgInfo.setMinReq(1);
      imgInfo.setMaxReq(1);
      this.startInstances(imgInfo);
    }

    return this.resources.get(sid);
  }

  /**
   * Creates new instances based on the information given in the CcdpImageinfo
   * object
   *
   * @param imgInfo the configuration used to launch new Virtual Machines.
   */
  private List<CcdpVMResource> startInstances( CcdpImageInfo imgInfo )
  {

    String sid = imgInfo.getSessionId();
    String typeStr = imgInfo.getNodeTypeAsString();
    if( sid == null )
      sid = typeStr;

    this.logger.info("Starting New Resources of type " + typeStr + " For Session " + sid);

    synchronized( this.resources )
    {
      if( !this.resources.containsKey(sid) )
        this.resources.put(sid, new ArrayList<CcdpVMResource>());
    }

    List<String> launched = this.controller.startInstances(imgInfo);

    for( String id : launched )
    {
      CcdpVMResource resource = new CcdpVMResource(id);
      resource.setStatus(ResourceStatus.LAUNCHED);
      resource.setAssignedSession(sid);
      resource.setNodeType(typeStr);
      this.connection.registerProducer(resource.getInstanceId());

      // assign the session
      AssignSessionMessage asm = new AssignSessionMessage();
      asm.setSessionId(sid);
      asm.setAssignCommand(imgInfo.getAssignmentCommand());
      this.connection.sendCcdpMessage(id, asm);


      // need to add to the list that is used down below
      synchronized( this.resources )
      {
        this.logger.debug("Adding resource " + resource.toString());
        this.resources.get(sid).add(resource);
      }
    }

    this.showSystemChange();
    return this.resources.get(sid);
  }

  private void printResources()
  {
    synchronized( this.resources )
    {
      for( String sid : this.resources.keySet() )
      {
        List<CcdpVMResource> vms = this.resources.get(sid);
        this.logger.debug("**************************        Session " + sid + "    **************************");
        for( CcdpVMResource vm : vms )
        {
          this.logger.debug("\tVM: " + vm.getInstanceId());
        }
      }

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
    this.logger.trace("Checking Deallocation for " + sid);
    List<CcdpVMResource> sid_vms = this.getResourcesBySessionId(sid);

    // Do we need to deallocate resources?
    List<CcdpVMResource> vms = this.tasker.deallocateResources(sid_vms);
    this.logger.trace("Deallocation " + vms.size() + " vms");
    for( CcdpVMResource vm : vms )
    {
      vm.setSingleTask(null);
      String sessId = vm.getNodeTypeAsString();
      this.changeSession(vm, sessId);

      this.showSystemChange();
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
    this.logger.trace("Getting resources for [" + sid +"]");
    if( sid == null )
    {
      this.logger.error("The Session ID cannot be null");
      return null;
    }

    List<CcdpVMResource> list = new ArrayList<>();
    List<CcdpVMResource> delete = new ArrayList<>();
    synchronized( this.resources )
    {
      if( this.resources.containsKey( sid ) )
      {
        this.logger.trace("SID found");
        int available = 0;
        List<CcdpVMResource> assigned = this.resources.get(sid);
        this.logger.trace("Number of assigned resources " + assigned.size());
        for( CcdpVMResource res : assigned )
        {
          //Updates the status if the resource (from pending to running)
          String iid = res.getInstanceId();
          if( !iid.startsWith( CcdpMainApplication.VM_TEST_PREFIX ) )
            res.setStatus(this.controller.getInstanceState( iid ));
          else
            res.setStatus(ResourceStatus.RUNNING);

          if( !ResourceStatus.SHUTTING_DOWN.equals(res.getStatus() ) )
          {
            this.logger.trace("Found Resource " + res.getInstanceId() +
                              " based on SID, adding it to list");
            list.add(res);
            available++;
          }
          else
          {
            this.logger.info(res.getInstanceId() + " :::: Status was " +
                             res.getStatus());
            // Remove the ones 'SHUTTING_DOWN'
            long now = System.currentTimeMillis();
            if( now - res.getLastAssignmentTime() >= 18000 )
            {
              delete.add(res);;
            }
          }
        }

        this.logger.trace(sid + " --> Resources available: " + available);
      }// found a list of sessions
      else
      {
        this.logger.info("Can't find session-id in resources, adding it");
        this.resources.put(sid,  list);
      }

      //removing resources with SHUTTING_DOWN status
      for (CcdpVMResource res : delete)
      {
        this.resources.get(sid).remove(res);
      }

    }// end of synch list

    return list;
  }

  /**
   * Sends a summary of the system to the logger handlers
   */
  private void showSystemChange()
  {
    StringBuffer buf = new StringBuffer();
    buf.append("\nNodeType:\n");

    String header = String.format("\n\t%-20s %-15s %-10s %-13s\n",
                      "Instance ID", "Session ID", "State", "Single Tasked");
    for( String type : this.resources.keySet() )
    {
      if( type == null )
        continue;

      // there is no need to show an empty set of resources
      List<CcdpVMResource> list = this.resources.get(type);
      if( list.size() == 0 )
        continue;

      buf.append(type);

      buf.append(header);
      buf.append("=======================================================================================\n");
      for( CcdpVMResource info : list )
      {
        String id = info.getInstanceId();
        String sid = info.getAssignedSession();
        ResourceStatus stat = info.getStatus();
        if( stat == null )
          stat = ResourceStatus.FAILED;
        String status = stat.toString();

        boolean single = info.isSingleTasked();
        String line = String.format("\t%-20s %-15s %-10s %3s %-5b %35s\n",
            id, sid, status, "   ", single, "Tasks");
        buf.append(line);
        String taskHead = String.format("\t%80s %18s %-15s\n", "Task ID", " ", "State");
        String sep = String.format("%66s %s\n", " ", "--------------------------------------------------");
        buf.append(taskHead);
        buf.append(sep);
        List<CcdpTaskRequest> tasks = info.getTasks();
        for( CcdpTaskRequest task : tasks )
        {
          String tid = task.getTaskId();
          String state = task.getState().toString();
          buf.append(String.format("%69s %-34s  %-10s\n", "*", tid, state));
        } // end of the tasks
        buf.append("\n");

      }// end of the VMs
    }// end of the Node Types


    this.logger.debug( buf.toString() );
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
