package com.axios.ccdp.mesos.fmwk;

import java.util.HashMap;

import org.apache.log4j.Logger;
import org.apache.mesos.Executor;
import org.apache.mesos.ExecutorDriver;
import org.apache.mesos.MesosExecutorDriver;
import org.apache.mesos.Protos.ExecutorInfo;
import org.apache.mesos.Protos.FrameworkInfo;
import org.apache.mesos.Protos.SlaveID;
import org.apache.mesos.Protos.SlaveInfo;
import org.apache.mesos.Protos.TaskID;
import org.apache.mesos.Protos.TaskInfo;
import org.apache.mesos.Protos.TaskState;
import org.apache.mesos.Protos.TaskStatus;
import org.apache.mesos.Protos.Status;

import com.axios.ccdp.mesos.utils.CcdpUtils;
import com.axios.ccdp.mesos.utils.TaskEventIntf;
import com.axios.ccdp.mesos.utils.ThreadedTimerTask;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

public class CcdpCommandExecutor implements Executor, TaskEventIntf
{
  /**
   * Generates debug print statements based on the verbosity level.
   */
  private Logger logger = Logger.getLogger(CcdpCommandExecutor.class.getName());
  /**
   * Stores all the tasks assigned to this executor
   */
  private HashMap<TaskID, CcdpTaskRunner> 
                              tasks = new HashMap<TaskID, CcdpTaskRunner>();
  /**
   * Stores a reference to the Mesos Driver requesting the task execution
   */
  private ExecutorDriver driver;
  /**
   * Invokes a method periodically to send heartbeats back to the Mesos Master
   */
  private ThreadedTimerTask timer = null;
  /**
   * Stores basic information about the node this executor is running on
   */
  private SlaveInfo agentInfo = null;
  /**
   * Stores basic information about the executor running on this node
   */
  private ExecutorInfo execInfo = null;
  /**
   * Generates all the different JSON objects
   */
  private ObjectMapper mapper = new ObjectMapper();
  
  /**
   * Instantiates a new instance of the agent responsible for running all the
   * tasks on a particular Mesos Agent
   */
  public CcdpCommandExecutor()
  {
    this.logger.info("Running the Executor");
    this.timer = new ThreadedTimerTask(this, 2000);
  }

  /**
   * Sends an update to the ExecutorDriver with the status change provided
   * as an argument.  If the message is not null then is set using the 
   * setMessage() method in the TaskStatus.Builder object
   * 
   * @param taskId the task unique identifier
   * @param state the current state of the task
   * @param message a message (optional) to be sent back to the ExecutorDriver
   */
  public void statusUpdate(TaskID taskId, TaskState state, String message)
  {
    this.logger.debug("Setting the status to " + state);
    TaskStatus.Builder bldr = TaskStatus.newBuilder();
    bldr.setTaskId(taskId);
    bldr.setState(state);
    // if we have a message, send it
    if( message != null )
      bldr.setMessage(message);
    
    TaskStatus status  = bldr.build();
    this.driver.sendStatusUpdate(status);
  }
  
  /**
   * Sends a heartbeat back to the Mesos Master every 5 seconds
   */
  public void onEvent()
  {
    this.logger.debug("Sending Heartbeat");
    ObjectNode node = this.mapper.createObjectNode();
    node.put("agent-id", this.agentInfo.getId().getValue() );
    node.put("hostname", this.agentInfo.getHostname() );
    node.put("executor-id", this.execInfo.getExecutorId().getValue() );
    
    // if we have a driver then send heartbeat messages
    if( this.driver != null )
      this.driver.sendFrameworkMessage(node.asText().getBytes());
  }
  
  /**
   * This is invoked when the slave disconnects from the executor, which 
   * typically indicates a slave restart.  Rarely should an executor need to do 
   * anything special here
   * 
   * @param driver
   */
  @Override
  public void disconnected(ExecutorDriver driver)
  {
    this.logger.info("Disconnecting");
    String msg = "ERROR: Slave was disconnected";
    this.driver.sendFrameworkMessage(msg.getBytes());
  }

  /**
   * This callback is invoked after a fatal error occurs.  When this callback is
   * invoked the driver is no longer running.
   * 
   * @param driver
   * @param errorMsg
   */
  @Override
  public void error(ExecutorDriver driver, String errorMsg)
  {
    this.logger.error("Driver no longer running, got an error: " + errorMsg);
  }

  /**
   * Framework messages are a simple (but not guaranteed) way to communicate 
   * between the scheduler and its executors by sending arbitrary data 
   * serialized as bytes.  
   * 
   * Note that framework messages are not routed to particular tasks
   * 
   * @param driver
   * @param msg serialized message as bytes
   * 
   */
  @Override
  public void frameworkMessage(ExecutorDriver driver, byte[] msg)
  {
    this.logger.info("Got a message from the Framework, not too reliable...");
    this.logger.info("The Message: " + new String(msg) );
  }

  /**
   * Kills the task referenced by the taskId argument if is running.
   * 
   * @param driver the executor sending the request
   * @param taskId the object containing enough information to identify the
   *        task
   */
  @Override
  public void killTask(ExecutorDriver driver, TaskID taskId)
  {
    this.logger.info("Killing Task: " + taskId.toString());
    
    synchronized( this )
    {
      CcdpTaskRunner task = this.tasks.get(taskId);
      if( task != null )
      {
        task.killTask();
      }
    }
  }

  /**
   * Invoked when a task has been launched on this executor (initiated via 
   * SchedulerDriver.launchTasks(OfferID>, TaskInfo>, Filters). Note that this 
   * task can be realized with a thread, a process, or some simple computation, 
   * however, no other callbacks will be invoked on this executor until this 
   * callback has returned.
   * 
   * @param driver The executor driver that launched the task.
   * @param task Desribes the task that was launched
   * 
   */
  @Override
  public void launchTask(ExecutorDriver driver, TaskInfo task)
  {
    this.logger.info("Launching Task: " + task.toString());
    // mutex to protect all global variables
    synchronized( this )
    {
      try
      {
        TaskID taskId = task.getTaskId();
        CcdpTaskRunner ccdpTask = new CcdpTaskRunner(task, this);
        this.tasks.put(taskId, ccdpTask);
        
        this.driver = driver;
        
        this.statusUpdate(taskId, TaskState.TASK_STARTING, null);
        
        ccdpTask.start();
        
        this.statusUpdate(taskId, TaskState.TASK_RUNNING, null);
      }
      catch( Exception e )
      {
        this.logger.error("Message: " + e.getMessage(), e);
      }
    }
  }

  /**
   * This is invoked the first time that an executor connects to the slave.  The
   * most common thing to do is get data from the ExecutorInfo since that can 
   * carry executor configuration information in its data field which contain
   * arbitrary bytes
   * 
   * @param driver The executor driver that was registered and connected to the 
   *        Mesos cluster.
   * @param exec Describes information about the executor that was registered.
   * @param fmwk Describes the framework that was registered.
   * @param slave Describes the slave that will be used to launch the tasks for 
   *        this executor.
   */
  @Override
  public void registered(ExecutorDriver driver, ExecutorInfo exec,
      FrameworkInfo fmwk, SlaveInfo slave)
  {
    String msg = "Executor Registered: " + exec.toString() + " in " + 
                 slave.toString();
    this.logger.info(msg);
    this.agentInfo = slave;
    this.execInfo = exec;
  }

  /**
   * This callback is invoked after a successful slave restart.  It contains the 
   * new slave's information
   * 
   * @param driver The executor driver that was re-registered and connected to 
   *        the Mesos cluster.
   * @param slave slave Describes the slave that will be used to launch the 
   *        tasks for this executor.
   */
  @Override
  public void reregistered(ExecutorDriver driver, SlaveInfo slave)
  {
    this.logger.info("Executor Re-registered: " + slave.toString() );
  }

  /**
   * This callback informs the executor to gracefully shut down.  It is called 
   * when a slave restart fails to complete within the grace period or when the
   * executor's framework completes.  The executor will be forcibly killed if 
   * shutdown doesn't complete within 5 seconds (the default, configurable on 
   * the slave command line with --executor_shutdown_grace_period).
   * 
   * @param driver
   */
  @Override
  public void shutdown(ExecutorDriver driver)
  {
    this.logger.info("Shuting Down Executor");
    if( this.timer != null )
      this.timer.stop();
  }

  public static void main(String[] args) throws Exception
  {
    CcdpUtils.configureProperties();
    CcdpUtils.configLogger();
    
    System.out.println("Creating a new Executor from main");
    System.err.println("Creating a new Executor from main");
    Executor executor = new CcdpCommandExecutor();
    ExecutorDriver driver = new MesosExecutorDriver(executor);
    int exitCode = driver.run() == Status.DRIVER_STOPPED ? 0 : 1;
    System.out.println("Executor ended with exit code: " + exitCode);
    System.exit(exitCode);
  }
}
