package com.axios.ccdp.fmwk;

import java.io.File;
import java.net.InetAddress;
import java.net.URL;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.log4j.Logger;

import com.axios.ccdp.factory.CcdpObjectFactory;
import com.axios.ccdp.impl.monitors.SystemResourceMonitorAbs;
import com.axios.ccdp.intfs.CcdpConnectionIntf;
import com.axios.ccdp.intfs.CcdpDatabaseIntf;
import com.axios.ccdp.intfs.CcdpMessageConsumerIntf;
import com.axios.ccdp.intfs.CcdpTaskLauncher;
import com.axios.ccdp.messages.AssignSessionMessage;
import com.axios.ccdp.messages.CcdpMessage;
import com.axios.ccdp.messages.KillTaskMessage;
import com.axios.ccdp.messages.RunTaskMessage;
import com.axios.ccdp.messages.ShutdownMessage;
import com.axios.ccdp.messages.ThreadRequestMessage;
import com.axios.ccdp.messages.CcdpMessage.CcdpMessageType;
import com.axios.ccdp.messages.ErrorMessage;
import com.axios.ccdp.resources.CcdpVMResource;
import com.axios.ccdp.resources.CcdpVMResource.ResourceStatus;
import com.axios.ccdp.tasking.CcdpTaskRequest;
import com.axios.ccdp.tasking.CcdpTaskRequest.CcdpTaskState;
import com.axios.ccdp.tasking.CcdpThreadRequest;
import com.axios.ccdp.utils.CcdpUtils;
import com.axios.ccdp.utils.TaskEventIntf;
import com.axios.ccdp.utils.ThreadController;
import com.axios.ccdp.utils.ThreadedTimerTask;
import com.fasterxml.jackson.databind.JsonNode;


public class CcdpAgent implements CcdpMessageConsumerIntf, TaskEventIntf, 
                                  CcdpTaskLauncher
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
  private SystemResourceMonitorAbs monitor = null; 
//            new SystemResourceMonitorImpl(SystemResourceMonitorImpl.UNITS.MB);
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
   * Stores the object that interacts with the database
   */
  private CcdpDatabaseIntf dbClient = null;
  
  /**
   * Instantiates a new instance of the agent responsible for running all the
   * tasks on a particular Mesos Agent
   * 
   * @param type the type of node this agent is running
   */
  public CcdpAgent(String type)
  {
    this.logger.info("Running the Agent");

    this.controller = new ThreadController();
    
    // creating the factory that generates the objects used by the agent
    CcdpObjectFactory factory = CcdpObjectFactory.newInstance();
    
    JsonNode task_msg_node = CcdpUtils.getConnnectionIntfCfg();
    
    JsonNode res_mon_node = CcdpUtils.getResourceMonitorIntfCfg();
    JsonNode db_node = CcdpUtils.getDatabaseIntfCfg();
    
    this.monitor = factory.getResourceMonitorInterface(res_mon_node);
    
    this.connection = factory.getCcdpConnectionInterface(task_msg_node);
    this.connection.configure(task_msg_node);
    this.connection.setConsumer(this);
    this.dbClient = factory.getCcdpDatabaseIntf( db_node );
    this.dbClient.connect();
    
    this.logger.info("Done with the connections: " + task_msg_node.toString());
    
    String hostId = this.monitor.getUniqueHostId();
    String hostname = null;
    
    try
    {
      hostname = CcdpUtils.retrieveEC2Info("public-ipv4");
    }
    catch( Exception e )
    {
      this.logger.warn("Could not retrieve hostname from EC2");
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
    this.logger.info("Using Host Id: " + hostId + " and type " + type );
    this.vmInfo = new CcdpVMResource(hostId);
    this.vmInfo.setHostname(hostname);
    this.vmInfo.setNodeType(type);
    
//    this.me.setAssignedSession("available");
    this.vmInfo.setStatus(ResourceStatus.RUNNING);
    this.updateResourceInfo();
    this.vmInfo.setCPU(100.0);
    this.vmInfo.setTotalMemory(this.monitor.getTotalPhysicalMemorySize());
    this.vmInfo.setDisk(this.monitor.getTotalDiskSpace());
    // The default session id should it be the Node type?
    this.vmInfo.setAssignedSession(type.toString());

    JsonNode eng = CcdpUtils.getEngineCfg();
    long hb = 3000;
    try
    {
      hb = eng.get(CcdpUtils.CFG_KEY_HB_FREQ).asInt() * 1000;
    }
    catch( Exception e )
    {
      this.logger.warn("The heartbeat frequency was not set using 3 seconds");
    }
    
    this.toMain = task_msg_node.get(CcdpUtils.CFG_KEY_MAIN_CHANNEL).asText(); 
    this.logger.info("Registering as " + hostId);
    this.connection.registerConsumer(hostId, hostId);
    this.connection.registerProducer(this.toMain);
    
    boolean skip_hb = eng.get(CcdpUtils.CFG_KEY_SKIP_HEARTBEATS).asBoolean();
    if( !skip_hb )
    {
      // sends the heartbeat 
      this.timer = new ThreadedTimerTask(this, hb, hb);
    }
    else
    {
      this.logger.warn("Skipping Hearbeats");
      //this.connection.sendHeartbeat(this.toMain, this.vmInfo);
      this.dbClient.storeVMInformation(this.vmInfo);
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
    this.vmInfo.setCPULoad(this.monitor.getSystemCpuLoad());
    this.vmInfo.setDisk(this.monitor.getTotalDiskSpace());
    this.vmInfo.setFreeDiskSpace(this.monitor.getFreeDiskSpace());
    this.vmInfo.setLastUpdatedTime(System.currentTimeMillis());
    double availableCPU = 100.0 - (this.vmInfo.getCPULoad()* 100);
    if (availableCPU < 0)
    	availableCPU = 0;
    this.vmInfo.setCPU(availableCPU);
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
   * Sends an update to the Main Application with the status change provided
   * as an argument.  
   * 
   * @param task the task to send updates to the main application
   */
  public void statusUpdate(CcdpTaskRequest task)
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
    
    this.logger.info("Have " + this.tasks.size() + " tasks remaining");
  }
  
  /**
   * Notifies the launcher that an error has occurred while executing the given
   * task.  The error message is provided as a separate argument
   * 
   * @param task the task to send updates to the main application
   * @param message a message describing the error if a tasks fails to execute
   */
  public void onTaskError(CcdpTaskRequest task, String message)
  {
    ErrorMessage msg = new ErrorMessage();
    msg.setErrorMessage(message);
    this.connection.sendCcdpMessage(this.toMain, msg);
  }
  
  /**
   * Sends a heartbeat back to the Mesos Master every 5 seconds
   */
  public void onEvent()
  {
    this.logger.debug("Storing heartbeat");
    this.updateResourceInfo();
    //this.connection.sendHeartbeat(this.toMain, this.vmInfo);
    this.dbClient.storeVMInformation(this.vmInfo);
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
        
        CcdpTaskRunner runner = this.tasks.remove(task);
        this.vmInfo.removeTask(task);
        if( runner != null )
        {
          runner.killTask();
        }
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
    this.logger.info("Launching Task " + task.toPrettyPrint() );
    
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
        this.statusUpdate(task);
        
        ccdpTask.start();
        
        // If the task is a dedicated one, then need to make sure I capture it
        if( task.getCPU() >= 100 )
          this.vmInfo.isSingleTasked(true);
        
        task.setState(CcdpTaskState.RUNNING);
        this.logger.info("Task " + task.getTaskId() + " set to " + task.getState());
        this.statusUpdate(task);
      }
      catch( Exception e )
      {
        this.logger.error("Message: " + e.getMessage(), e);
        task.setState(CcdpTaskState.FAILED);
        this.logger.warn("Task " + task.getTaskId() + " set to " + task.getState());
        String txt = "Task " + task.getTaskId() + " failed to execute.  " +
         "Got an exception with the following errror message " + e.getMessage();
        this.logger.warn(txt);        
        this.onTaskError(task, txt);
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
    //this.connection.sendHeartbeat(this.toMain, this.vmInfo);
    this.dbClient.storeVMInformation(this.vmInfo);
    
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
    
    // A way to assign a node type
    String node_type = "The type of node this agent is running on";
    Option node = new Option("n", "node-type", true, node_type);
    config.setRequired(false);
    options.addOption(node);

    
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
      {
        cfg_file = fname;
        CcdpUtils.loadProperties(file);
        loaded = true;
      }
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
    
    String type = CcdpUtils.DEFAULT_RES_NAME;
    if( cmd.hasOption('n') )
    {
      String val = cmd.getOptionValue('n');
      try
      {
        if( CcdpUtils.getNodeTypes().indexOf(val) >= 0 )
          type = val;
      }
      catch( Exception e )
      {
        System.err.println("Invalid Node Type " + val + " using DEFAULT");
      }
    }
    else  // attempting to get it from the environment variable
    {
      String env = System.getenv("CCDP_NODE_TYPE");
      if( env != null )
      {
        try
        {
          if( CcdpUtils.getNodeTypes().indexOf(env) >= 0 )
            type = env;
        }
        catch( Exception e )
        {
          System.err.println("Invalid Node Type " + env + " using DEFAULT");
        }
      }
    }
    
    if( !loaded )
      CcdpUtils.loadProperties(cfg_file);
    
    CcdpUtils.configLogger();
    new CcdpAgent(type);

  }
}
