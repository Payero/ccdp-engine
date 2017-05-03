package com.axios.ccdp.newgen;

import java.io.File;
import java.net.URL;
import java.util.HashMap;
import java.util.Iterator;
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

import com.axios.ccdp.connections.intfs.CcdpEventConsumerIntf;
import com.axios.ccdp.connections.intfs.CcdpObjectFactoryAbs;
import com.axios.ccdp.tasking.CcdpTaskRequest;
import com.axios.ccdp.tasking.CcdpTaskRequest.CcdpTaskState;
import com.axios.ccdp.utils.CcdpUtils;
import com.axios.ccdp.utils.CcdpUtils.EventType;
import com.axios.ccdp.utils.SystemResourceMonitor;
import com.axios.ccdp.utils.TaskEventIntf;
import com.axios.ccdp.utils.ThreadController;
import com.axios.ccdp.utils.ThreadedTimerTask;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;


public class CcdpAgent implements CcdpEventConsumerIntf, TaskEventIntf
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
   * Generates all the different JSON objects
   */
  private ObjectMapper mapper = new ObjectMapper();
  /**
   * Retrieves all the system's resources as a JSON object
   */
  private SystemResourceMonitor monitor = new SystemResourceMonitor();
  /**
   * The unique identifier for this agent
   */
  private String instanceId = null;
  /**
   * Object used to send and receive messages such as incoming tasks to process
   * and sending heartbeats and tasks updates
   */
  private CcdpConnectionIntf connection;
  /**
   * Stores the time when the last task was assigned to it
   */
  private long lastAssignment = System.currentTimeMillis();
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
    String clazz = CcdpUtils.getProperty(CcdpUtils.CFG_KEY_FACTORY_IMPL);
    if( clazz != null )
    {
      CcdpObjectFactoryAbs factory = CcdpObjectFactoryAbs.newInstance(clazz);
      ObjectNode task_msg_node = 
          CcdpUtils.getJsonKeysByFilter(CcdpUtils.CFG_KEY_CONN_INTF);
      
      this.connection = factory.getCcdpConnectionInterface(task_msg_node);
      this.connection.configure(task_msg_node);
      this.connection.setConsumer(this);
      
    }
    else
    {
      String txt = "Could not find factory.  Please check configuration." +
                   "The key " + CcdpUtils.CFG_KEY_FACTORY_IMPL + " is missing";
      this.logger.error(txt);
      System.exit(-1);
    }
    
    
    try
    {
      this.instanceId = CcdpUtils.retrieveEC2InstanceId();
    }
    catch( Exception e )
    {
      this.logger.error("Could not retrieve Instance ID");
      this.instanceId = UUID.randomUUID().toString();
    }
    
    
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
    this.logger.info("Registering as " + this.instanceId);
    this.connection.registerConsumer(this.instanceId, this.instanceId);
    this.connection.registerProducer(this.toMain);
    
    // sends the heartbeat 
    //this.timer = new ThreadedTimerTask(this, hb, hb);
    
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
   * Gets a JsonNode event of the form:
   *  {
   *    config: dictionary with configuration to add,
   *    body:
   *      {
   *        event-type: the event type (TASK_REQUEST, HEARTBEAT, TASKS_STATUS },
   *        event: the actual event such as the JSON representation of at task
   *      }
   *  }
   *  
   *  @param event the JSON object as described above
   */
  public void onEvent( Object event )
  {
    
    if( event instanceof JsonNode )
    {
      JsonNode node = (JsonNode)event;
      
      if( node.has("body") )
        node = node.get("body");
      
      this.logger.debug("Got a new Event: " + node.toString());
      Iterator<String> names = node.fieldNames(); 
      while( names.hasNext() )
      {
        String name = names.next();
        
        this.logger.debug("Name " + name );
      }
      
      if( !node.has("event-type") )
      {
        this.logger.error("Cannot procees an event without 'event-type' set");
        return;
      }
      
      if( !node.has("event") )
      {
        this.logger.error("Cannot procees an event without 'event' set");
        return;
      }
      
      EventType type = EventType.valueOf( node.get("event-type").asText() );
      this.logger.debug("Processing an event of type " + type);
      
      switch( type )
      {
      case TASK_REQUEST:
        this.launchTask(node.get("event"));
        break;
      case KILL_TASK:
        this.killTask(node.get("event"));
        break;
        default:
          String msg = "CcdpAgent does not process events of type " + type +
                       "  It only accepts TASK_REQUEST and KILL_TASK ";
          this.logger.warn(msg);
      }
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
    this.logger.debug("Got a new Task Status for " + task.getTaskId());
    this.connection.sendTaskUpdate(this.toMain, task.toJSON());
    
    CcdpTaskState state = task.getState();
    
    if( state.equals(CcdpTaskState.FAILED) || 
        state.equals(CcdpTaskState.SUCCESSFUL) )
    {
      this.tasks.remove(task);
    }
    
    this.logger.debug("Have " + this.tasks.size() + " tasks remaining");
  }
  
  /**
   * Sends a heartbeat back to the Mesos Master every 5 seconds
   */
  public void onEvent()
  {
    this.logger.debug("Sending Heartbeat");
    ObjectNode node = this.mapper.createObjectNode();
    node.put("instance-id", this.instanceId);
    node.put("number-tasks", this.tasks.size());
    node.put("last-assignment", this.lastAssignment);
    node.set("resources", this.monitor.toJSON());
    
    this.connection.sendHeartbeat(this.toMain, node);
  }
  

  /**
   * Kills the task referenced by the taskId argument if is running.
   * 
   * @param task the object containing enough information to identify the
   *        task to kill
   */
  public void killTask(JsonNode node)
  {
    try
    {
      CcdpTaskRequest task = 
          mapper.treeToValue(node, CcdpTaskRequest.class);
      
      this.logger.info("Killing Task: " + task.getTaskId() );
      
      synchronized( this )
      {
        CcdpTaskRunner runner = this.tasks.remove(task);
        if( runner != null )
        {
          runner.killTask();
        }
      }
    }
    catch( Exception e )
    {
      this.logger.error("Message: " + e.getMessage(), e);
    }
  }

  /**
   * Invoked when a task has been launched on this executor (initiated via 
   * SchedulerDriver.launchTasks(OfferID, TaskInfo, Filters). Note that this 
   * task can be realized with a thread, a process, or some simple computation, 
   * however, no other callbacks will be invoked on this executor until this 
   * callback has returned.
   * 
   * @param node describes the task to launch
   * 
   */
  public void launchTask(JsonNode node)
  {
    // mutex to protect all global variables
    synchronized( this )
    {
      try
      {
        CcdpTaskRequest task = 
            this.mapper.treeToValue(node, CcdpTaskRequest.class);
        
        CcdpTaskRunner ccdpTask = new CcdpTaskRunner(task, this);
        this.tasks.put(task, ccdpTask);
        task.setState(CcdpTaskState.STAGING);
        this.statusUpdate(task, null);
        
        ccdpTask.start();
        
        task.setState(CcdpTaskState.RUNNING);
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
    {
      System.err.println("The Config File: " + cfg_file);
      CcdpUtils.loadProperties(cfg_file);
    }
    
    CcdpUtils.configLogger();
    new CcdpAgent();

  }
}
