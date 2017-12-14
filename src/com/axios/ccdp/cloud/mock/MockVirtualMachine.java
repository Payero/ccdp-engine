package com.axios.ccdp.cloud.mock;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.apache.log4j.Logger;

import com.axios.ccdp.connections.intfs.CcdpConnectionIntf;
import com.axios.ccdp.connections.intfs.CcdpMessageConsumerIntf;
import com.axios.ccdp.connections.intfs.CcdpTaskLauncher;
import com.axios.ccdp.factory.CcdpObjectFactory;
import com.axios.ccdp.fmwk.CcdpMainApplication;
import com.axios.ccdp.messages.AssignSessionMessage;
import com.axios.ccdp.messages.CcdpMessage;
import com.axios.ccdp.messages.ErrorMessage;
import com.axios.ccdp.messages.KillTaskMessage;
import com.axios.ccdp.messages.RunTaskMessage;
import com.axios.ccdp.messages.ShutdownMessage;
import com.axios.ccdp.messages.ThreadRequestMessage;
import com.axios.ccdp.messages.CcdpMessage.CcdpMessageType;
import com.axios.ccdp.resources.CcdpVMResource;
import com.axios.ccdp.resources.CcdpVMResource.ResourceStatus;
import com.axios.ccdp.tasking.CcdpTaskRequest;
import com.axios.ccdp.tasking.CcdpThreadRequest;
import com.axios.ccdp.tasking.CcdpTaskRequest.CcdpTaskState;
import com.axios.ccdp.utils.CcdpUtils;
import com.axios.ccdp.utils.CcdpUtils.CcdpNodeType;
import com.axios.ccdp.utils.SystemResourceMonitor;
import com.axios.ccdp.utils.TaskEventIntf;
import com.axios.ccdp.utils.ThreadController;
import com.axios.ccdp.utils.ThreadedTimerTask;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Class used to simulate a Virtual Machine to simplify development and testing
 * It runs as a thread and accepts Tasks without actually executing them
 * 
 * @author Oscar E. Ganteaume
 *
 */
public class MockVirtualMachine implements Runnable, CcdpMessageConsumerIntf, 
                                  TaskEventIntf, CcdpTaskLauncher
{
  /**
   * Generates debug print statements based on the verbosity level.
   */
  private Logger logger = Logger.getLogger(MockVirtualMachine.class.getName());
  /**
   * Stores all the information about this resource
   */
  private CcdpVMResource vmInfo;
  /**
   * Invokes a method periodically to send heartbeats back to the Mesos Master
   */
  private ThreadedTimerTask timer = null;
  /**
   * Retrieves all the system's resources as a JSON object
   */
  private SystemResourceMonitor monitor = 
            new SystemResourceMonitor(SystemResourceMonitor.UNITS.MB);
  /**
   * Object used to send and receive messages such as incoming tasks to process
   * and sending heartbeats and tasks updates
   */
  private CcdpConnectionIntf connection;
  /**
   * Stores the name of the queue used by the main application to receive 
   * heartbeats and tasks updates
   */
  private String toMain = null;
  /**
   * Stores the object responsible for keeping this application running
   */
  private ThreadController controller = null;
  /**
   * Stores all the tasks assigned to this executor
   */
  private Map<CcdpTaskRequest, MockCcdpTaskRunner> tasks = new HashMap<>();
  
  /**
   * Instantiates a new object and establishes all the required connections
   */
  public MockVirtualMachine(CcdpNodeType type)
  {
    this.logger.info("Running the Agent");
    this.controller = new ThreadController();
    
    // creating the factory that generates the objects used by the agent
    CcdpObjectFactory factory = CcdpObjectFactory.newInstance();
    ObjectNode task_msg_node = 
        CcdpUtils.getJsonKeysByFilter(CcdpUtils.CFG_KEY_CONN_INTF);
    
    this.connection = factory.getCcdpConnectionInterface(task_msg_node);
    this.connection.configure(task_msg_node);
    this.connection.setConsumer(this);
    this.logger.debug("Done with the connections: " + task_msg_node.toString());
    
    String hostId = null;
    String hostname = null;
    
    try
    {
      this.logger.debug("Retrieving Instance ID");
      hostId = CcdpUtils.retrieveEC2InstanceId();
      hostname = CcdpUtils.retrieveEC2Info("public-ipv4");
    }
    catch( Exception e )
    {
      this.logger.error("Could not retrieve Instance ID");
      String[] uid = UUID.randomUUID().toString().split("-");
      hostId = CcdpMainApplication.VM_TEST_PREFIX + "-" + uid[uid.length - 1];
      try
      {
        InetAddress addr = CcdpUtils.getLocalHostAddress();
        hostname = addr.getHostAddress();
      }
      catch(UnknownHostException uhe)
      {
        this.logger.warn("Could not get the IP address");
      }
    }
    this.logger.info("Using Host Id: " + hostId + " and type " + type.name());
    this.vmInfo = new CcdpVMResource(hostId);
    this.vmInfo.setHostname(hostname);
    this.vmInfo.setNodeType(type);
    
    this.vmInfo.setStatus(ResourceStatus.RUNNING);
    this.updateResourceInfo();
    
    this.vmInfo.setCPU(this.monitor.getTotalNumberCpuCores());
    this.vmInfo.setTotalMemory(this.monitor.getTotalPhysicalMemorySize());
    this.vmInfo.setDisk(this.monitor.getTotalDiskSpace());

    
    long hb = 3000;
    try
    {
      hb = CcdpUtils.getIntegerProperty(CcdpUtils.CFG_KEY_HB_FREQ) * 1000;
    }
    catch( Exception e )
    {
      this.logger.warn("The heartbeat frequency was not set using 3 seconds");
    }
    
    this.toMain = CcdpUtils.getProperty(CcdpUtils.CFG_KEY_MAIN_CHANNEL);
    this.logger.info("Registering as " + hostId);
    this.connection.registerConsumer(hostId, hostId);
    this.connection.registerProducer(this.toMain);
    
    boolean skip_hb = 
        CcdpUtils.getBooleanProperty(CcdpUtils.CFG_KEY_SKIP_HEARTBEATS);
    if( !skip_hb )
    {
      // sends the heartbeat 
      this.timer = new ThreadedTimerTask(this, hb, hb);
    }
    else
    {
      this.logger.warn("Skipping Hearbeats");
      this.connection.sendHeartbeat(this.toMain, this.vmInfo);
    }
  }

  /**
   * It just keeps this application running until the shutdown() method is 
   * called
   */
  @Override
  public void run()
  {
    this.logger.info("Running Continuously until shutdown() is called");
    while( !this.controller.isSet() )
    {
      CcdpUtils.pause(1);
    }
  }

  /**
   * Updates the resource information by getting the CPU, Memory, and Disk space
   * currently used by the system.
   */
  private void updateResourceInfo()
  {
    this.vmInfo.setMemLoad( this.monitor.getUsedPhysicalMemorySize() );
    this.vmInfo.setTotalMemory(this.monitor.getTotalPhysicalMemorySize());
    this.vmInfo.setFreeMemory(this.monitor.getFreePhysicalMemorySize());
    this.vmInfo.setCPU(this.monitor.getTotalNumberCpuCores());
    this.vmInfo.setCPULoad(this.monitor.getSystemCpuLoad());
    this.vmInfo.setDisk(this.monitor.getTotalDiskSpace());
    this.vmInfo.setFreeDiskSpace(this.monitor.getFreeDiskSpace());
  }
  
  
  /**
   * Sends a heartbeat back to the Mesos Master every 5 seconds
   */
  @Override
  public void onEvent()
  {
    this.logger.trace("Sending Heartbeat to " + this.toMain);
    this.updateResourceInfo();
    this.connection.sendHeartbeat(this.toMain, this.vmInfo);
  }

  /**
   * Gets a message from an external entity
   *  
   * @param message the incoming message that needs to be consumed
   */
  @Override
  public void onCcdpMessage( CcdpMessage message )
  {
    CcdpMessageType msgType = CcdpMessageType.get( message.getMessageType() );
    this.logger.debug("Got a new Event: " + message.toString());
    switch( msgType )
    {
      case ASSIGN_SESSION:
        AssignSessionMessage sessionMsg = (AssignSessionMessage)message;
        this.setSessionId(sessionMsg.getSessionId());
        this.runAssignmentTask(sessionMsg.getAssignCommand());
        break;
      case RESOURCE_UPDATE:
      case RUN_TASK:
        RunTaskMessage taskMsg = (RunTaskMessage)message;
        this.launchTask(taskMsg.getTask());
        break;
      case KILL_TASK:
        KillTaskMessage killMsg = (KillTaskMessage)message;
        this.killTask(killMsg.getTask());
        break;
      case THREAD_REQUEST:
        ThreadRequestMessage threadMsg = (ThreadRequestMessage)message;
        this.threadRequest(threadMsg.getRequest());
        break;
      case SHUTDOWN:
        ShutdownMessage shutdownMsg = (ShutdownMessage)message;
        this.shutdown(shutdownMsg.getMessage());
        break;
      case TASK_UPDATE:
      case UNDEFINED:
      default:
        String msg = "CcdpAgent does not process events of type " + msgType;
        this.logger.warn(msg);
    }
  }
  
  
  /**
   * Sends an update to the ExecutorDriver with the status change provided
   * as an argument.  If there was an error executing the task then a message
   * is provided back to the caller.
   * 
   * @param task the task to send updates to the main application
   * @param message a message describing the error if a tasks fails to execute
   */
  public void statusUpdate(CcdpTaskRequest task, String message)
  {
    CcdpTaskState state = task.getState();
    task.setHostName(this.vmInfo.getHostname());
    
    this.connection.sendTaskUpdate(this.toMain, task);
    
    if( state.equals(CcdpTaskState.FAILED) || 
        state.equals(CcdpTaskState.SUCCESSFUL) )
    {
      this.tasks.remove(task);
      this.vmInfo.removeTask(task);
    }
    
    if( message != null )
    {
      ErrorMessage msg = new ErrorMessage();
      msg.setErrorMessage(message);
      this.connection.sendCcdpMessage(this.toMain, msg);
    }
    
    this.logger.info("Have " + this.tasks.size() + " tasks remaining");
  }
  

  /**
   * Assigns a session-id to this agent.
   * 
   * @param sid the session id of this resource
   */
  public void setSessionId( String sid )
  {
    this.logger.info("Setting Session to " + sid);
    this.vmInfo.setAssignedSession(sid);
  }
  
  /**
   * Kills the task referenced by the taskId argument if is running.
   * 
   * @param task the object containing enough information to identify the
   *        task to kill
   */
  public void killTask(CcdpTaskRequest task)
  {
    try
    {
      this.logger.info("Killing Task: " + task.getTaskId() );
      
      synchronized( this )
      {
        // if there is a command to run, do it
        if( !task.getCommand().isEmpty() )
          this.launchTask(task);
        
        this.tasks.remove(task);
        this.vmInfo.removeTask(task);
        task.setState(CcdpTaskState.KILLED);
        this.statusUpdate( task, null );
      }
    }
    catch( Exception e )
    {
      String txt = "Task " + task.getTaskId() + " could not be killed.  " +
        "Got an exception with the following errror message " + e.getMessage();
      this.logger.error(txt, e);
      ErrorMessage msg = new ErrorMessage();
      msg.setErrorMessage(txt);
      this.connection.sendCcdpMessage(this.toMain, msg);
    }
  }

  /**
   * Handles a ThreadRequest by just getting all the CcdpTaskRequest objects 
   * and launching them all.  It does not consider or takes into account if the
   * thread should run sequentially or in parallel.
   * 
   * @param request the request with all the tasks to launch.
   */
  private void threadRequest( CcdpThreadRequest request )
  {
    this.logger.info("Got a Thread Request Message");
    // Updating the session based on the request to be always updated
    this.setSessionId(request.getSessionId());
    for( CcdpTaskRequest task : request.getTasks() )
    {
      this.launchTask(task);
    }
  }
  
  /**
   * Invoked when the resource has been reassigned to a new session.
   * 
   * @param command describes the task to launch
   * 
   */
  private void runAssignmentTask( String command )
  {
    if( command == null || command.length() == 0 )
    {
      this.logger.debug("Assignment command is null, ignoring it");
      return;
    }
    
    this.logger.info("Running assignment command " + command );
    
    CcdpTaskRequest task = new CcdpTaskRequest();
    String[] items = command.split(" ");
    List<String> cmd = new ArrayList<>();
    for( String item : items )
      cmd.add(item);
    
    task.setCommand(cmd);
    task.setSessionId(this.vmInfo.getAssignedSession());
    task.setHostId(this.vmInfo.getInstanceId());
    this.launchTask(task);
  }
  
  /**
   * Invoked when a task has been launched on this Virtual Machine.  The tasks
   * are received through the connection channel used by the framework
   * 
   * @param task describes the task to launch
   * 
   */
  public void launchTask( CcdpTaskRequest task)
  {
    this.logger.info("Launching Task " + task.toPrettyPrint() );
    
    // mutex to protect all global variables
    synchronized( this )
    {
      try
      {
        MockCcdpTaskRunner ccdpTask = new MockCcdpTaskRunner(task, this);
        this.tasks.put(task, ccdpTask);
        this.vmInfo.addTask(task);
        
        task.setState(CcdpTaskState.STAGING);
        this.logger.info("Task " + task.getTaskId() + " set to " + task.getState());
        this.statusUpdate(task, null);
        
        ccdpTask.start();
        
        // If the task is a dedicated one, then need to make sure I capture it
        if( task.getCPU() >= 100 )
          this.vmInfo.isSingleTasked(true);
        
        task.setState(CcdpTaskState.RUNNING);
        this.logger.info("Task " + task.getTaskId() + " set to " + task.getState());
        this.statusUpdate(task, null);
      }
      catch( Exception e )
      {
        this.logger.error("Message: " + e.getMessage(), e);
        task.setState(CcdpTaskState.FAILED);
        this.logger.warn("Task " + task.getTaskId() + " set to " + task.getState());
        String txt = "Task " + task.getTaskId() + " failed to execute.  " +
         "Got an exception with the following errror message " + e.getMessage();
        this.logger.warn(txt);        
        this.statusUpdate(task, txt);
      }
    }
  }


  /**
   * This callback informs the agent to gracefully shut down.  
   * 
   * @param message any message to print for debugging purposes
   * 
   */
  public void shutdown( String message )
  {
    if( message != null )
      this.logger.info("Shuting Down Agent, given message: " + message);
    else
      this.logger.info("Shuting Down Agent");
    this.vmInfo.setStatus(ResourceStatus.SHUTTING_DOWN);
    
    if( this.timer != null )
      this.timer.stop();
    
    this.controller.set();
  }
  
}
