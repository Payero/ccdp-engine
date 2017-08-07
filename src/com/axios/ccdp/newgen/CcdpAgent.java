package com.axios.ccdp.newgen;

import java.io.File;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

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
import com.axios.ccdp.connections.intfs.CcdpTaskLauncher;
import com.axios.ccdp.factory.CcdpObjectFactory;
import com.axios.ccdp.message.AssignSessionMessage;
import com.axios.ccdp.message.CcdpMessage;
import com.axios.ccdp.message.CcdpMessage.CcdpMessageType;
import com.axios.ccdp.message.KillTaskMessage;
import com.axios.ccdp.message.ResourceUpdateMessage;
import com.axios.ccdp.message.RunTaskMessage;
import com.axios.ccdp.message.ThreadRequestMessage;
import com.axios.ccdp.resources.CcdpVMResource;
import com.axios.ccdp.resources.CcdpVMResource.ResourceStatus;
import com.axios.ccdp.tasking.CcdpTaskRequest;
import com.axios.ccdp.tasking.CcdpTaskRequest.CcdpTaskState;
import com.axios.ccdp.tasking.CcdpThreadRequest;
import com.axios.ccdp.utils.CcdpUtils;
import com.axios.ccdp.utils.SystemResourceMonitor;
import com.axios.ccdp.utils.TaskEventIntf;
import com.axios.ccdp.utils.ThreadController;
import com.axios.ccdp.utils.ThreadedTimerTask;
import com.fasterxml.jackson.databind.node.ObjectNode;


public class CcdpAgent implements CcdpMessageConsumerIntf, TaskEventIntf, CcdpTaskLauncher
{
  /**
   * Parses and prints all the options or arguments used by this application
   */
  private static HelpFormatter formatter = new HelpFormatter();
  /**
   * Stores all the options that can be used by this application
   */
  private static Options options = new Options();
  /**
   * Stores all the information about this resource
   */
  private CcdpVMResource vmInfo;
  /**
   * Generates debug print statements based on the verbosity level.
   */
  private Logger logger = Logger.getLogger(CcdpAgent.class.getName());
  /**
   * Stores all the tasks assigned to this executor
   */
  private Map<CcdpTaskRequest, CcdpTaskRunner> tasks = new HashMap<>();
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
   * Instantiates a new instance of the agent responsible for running all the
   * tasks on a particular Mesos Agent
   */
  public CcdpAgent()
  {
    this.logger.info("Running the Agent");
    this.controller = new ThreadController();
    
    // creating the factory that generates the objects used by the scheduler
    CcdpObjectFactory factory = CcdpObjectFactory.newInstance();
    ObjectNode task_msg_node = 
        CcdpUtils.getJsonKeysByFilter(CcdpUtils.CFG_KEY_CONN_INTF);
    
    this.connection = factory.getCcdpConnectionInterface(task_msg_node);
    this.connection.configure(task_msg_node);
    this.connection.setConsumer(this);
    this.logger.debug("Done with the connections: " + task_msg_node.toString());

    
    String hostId = null;
    try
    {
      this.logger.debug("Retrieving Instance ID");
      hostId = CcdpUtils.retrieveEC2InstanceId();
    }
    catch( Exception e )
    {
      this.logger.error("Could not retrieve Instance ID");
      String[] items = UUID.randomUUID().toString().split("-");
      hostId = "i-test-" + items[items.length - 1];
    }
    this.logger.info("Using Host Id: " + hostId);
    this.vmInfo = new CcdpVMResource(hostId);
//    this.me.setAssignedSession("available");
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
    
    this.runMain();
  }

  /**
   * It just keeps this application running until the shutdown() method is 
   * called
   */
  private void runMain()
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
      case ASSIGN_SESSION:
        AssignSessionMessage sessionMsg = (AssignSessionMessage)message;
        this.setSessionId(sessionMsg.getSessionId());
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
      case TASK_UPDATE:
      case UNDEFINED:
      default:
        String msg = "CcdpAgent does not process events of type " + msgType;
        this.logger.warn(msg);
    }
  }
  
  
  /**
   * Sends an update to the ExecutorDriver with the status change provided
   * as an argument.  If the message is not null then is set using the 
   * setMessage() method in the TaskStatus.Builder object
   * 
   * @param task the task to send updates to the main application
   * @param message a message (optional) to be sent back to the ExecutorDriver
   */
  public void statusUpdate(CcdpTaskRequest task, String message)
  {
    CcdpTaskState state = task.getState();
    this.connection.sendTaskUpdate(this.toMain, task);
    
    if( state.equals(CcdpTaskState.FAILED) || 
        state.equals(CcdpTaskState.SUCCESSFUL) )
    {
      this.tasks.remove(task);
      this.vmInfo.removeTask(task);
    }
    
    this.logger.info("Have " + this.tasks.size() + " tasks remaining");
  }
  
  /**
   * Sends a heartbeat back to the Mesos Master every 5 seconds
   */
  public void onEvent()
  {
    this.logger.debug("Sending Heartbeat to " + this.toMain);
    this.updateResourceInfo();
    this.connection.sendHeartbeat(this.toMain, this.vmInfo);
  }
  

  /**
   * Assigns a session-id to this agent.
   * 
   * @param sid the session id of this resource
   */
  public void setSessionId( String sid )
  {
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
        CcdpTaskRunner runner = this.tasks.remove(task);
        if( runner != null )
        {
          runner.killTask();
          this.vmInfo.getTasks().remove(task);
        }
      }
    }
    catch( Exception e )
    {
      this.logger.error("Message: " + e.getMessage(), e);
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
    for( CcdpTaskRequest task : request.getTasks() )
    {
      this.launchTask(task);
    }
  }
  
  /**
   * Invoked when a task has been launched on this executor (initiated via 
   * SchedulerDriver.launchTasks(OfferID, TaskInfo, Filters). Note that this 
   * task can be realized with a thread, a process, or some simple computation, 
   * however, no other callbacks will be invoked on this executor until this 
   * callback has returned.
   * 
   * @param task describes the task to launch
   * 
   */
  public void launchTask( CcdpTaskRequest task)
  {
    // mutex to protect all global variables
    synchronized( this )
    {
      try
      {
        CcdpTaskRunner ccdpTask = new CcdpTaskRunner(task, this);
        this.tasks.put(task, ccdpTask);
        this.vmInfo.addTask(task);
        
        task.setState(CcdpTaskState.STAGING);
        this.logger.info("Task " + task.getTaskId() + " set to " + task.getState());
        this.statusUpdate(task, null);
        
        ccdpTask.start();
        
        task.setState(CcdpTaskState.RUNNING);
        this.logger.info("Task " + task.getTaskId() + " set to " + task.getState());
        this.statusUpdate(task, null);
      }
      catch( Exception e )
      {
        this.logger.error("Message: " + e.getMessage(), e);
      }
    }
  }


  /**
   * This callback informs the executor to gracefully shut down.  It is called 
   * when a slave restart fails to complete within the grace period or when the
   * executor's framework completes.  The executor will be forcibly killed if 
   * shutdown doesn't complete within 5 seconds (the default, configurable on 
   * the slave command line with --executor_shutdown_grace_period).
   * 
   */
  public void shutdown()
  {
    this.logger.info("Shuting Down Executor");
    if( this.timer != null )
      this.timer.stop();
    
    this.controller.set();
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
    
    formatter.printHelp(CcdpAgent.class.toString(), options);
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
      formatter.printHelp("utility-name", options);

      System.exit(1);
      return;
    }
    // if help is requested, print it an quit
    if( cmd.hasOption('h') )
    {
      formatter.printHelp(CcdpAgent.class.toString(), options);
      System.exit(0);
    }
    String cfg_file = null;
    String key = CcdpUtils.CFG_KEY_CFG_FILE;
    
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
    new CcdpAgent();

  }
}
