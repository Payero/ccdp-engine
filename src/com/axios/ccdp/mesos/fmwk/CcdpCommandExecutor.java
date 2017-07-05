package com.axios.ccdp.mesos.fmwk;

import java.util.HashMap;
import java.util.UUID;

import org.apache.log4j.Logger;
import org.apache.mesos.Executor;
import org.apache.mesos.ExecutorDriver;
import org.apache.mesos.MesosExecutorDriver;
import org.apache.mesos.Protos.Attribute;
import org.apache.mesos.Protos.ExecutorInfo;
import org.apache.mesos.Protos.FrameworkInfo;
import org.apache.mesos.Protos.SlaveInfo;
import org.apache.mesos.Protos.TaskID;
import org.apache.mesos.Protos.TaskInfo;
import org.apache.mesos.Protos.TaskState;
import org.apache.mesos.Protos.TaskStatus;
import org.apache.mesos.Protos.Status;

import com.axios.ccdp.resources.CcdpVMResource;
import com.axios.ccdp.resources.CcdpVMResource.ResourceStatus;
import com.axios.ccdp.tasking.CcdpTaskRequest;
import com.axios.ccdp.utils.CcdpUtils;
import com.axios.ccdp.utils.SystemResourceMonitor;
import com.axios.ccdp.utils.TaskEventIntf;
import com.axios.ccdp.utils.ThreadedTimerTask;

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
   * Retrieves all the system's resources as a JSON object
   */
  private SystemResourceMonitor monitor = 
      new SystemResourceMonitor(SystemResourceMonitor.UNITS.MB);
  /**
   * Stores information about the VM hosting the executor
   */
  private CcdpVMResource vmInfo = null;
  
  /**
   * Instantiates a new instance of the agent responsible for running all the
   * tasks on a particular Mesos Agent
   */
  public CcdpCommandExecutor()
  {
    this.logger.info("Running the Executor");
    this.vmInfo = new CcdpVMResource(UUID.randomUUID().toString() );
    this.vmInfo.setStatus(ResourceStatus.RUNNING);
    this.vmInfo.setTotalMemory(this.monitor.getTotalPhysicalMemorySize());
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
    String tid = taskId.getValue();
    
    TaskStatus.Builder bldr = TaskStatus.newBuilder();
    bldr.setTaskId(taskId);
    bldr.setState(state);
    // if we have a message, send it
    if( message != null )
      bldr.setMessage(message);
    
    TaskStatus status  = bldr.build();
    this.driver.sendStatusUpdate(status);
    if( state.equals(TaskState.TASK_ERROR) || 
        state.equals(TaskState.TASK_FAILED) || 
        state.equals(TaskState.TASK_FINISHED) )
    {
      this.tasks.remove(taskId);
      CcdpTaskRequest rem = null;
      for( CcdpTaskRequest task : this.vmInfo.getTasks() )
      {
        if(task.getTaskId().equals( tid ) )
        {
          rem = task;
          break;
        }
      }
      if( rem != null )
        this.vmInfo.removeTask(rem);
//      if( this.tasks.isEmpty() )
//        this.driver.abort();
    }
  }
  
  /**
   * Sends a heartbeat back to the Mesos Master every 5 seconds
   */
  public void onEvent()
  {
    // if we have a driver then send heartbeat messages
    if( this.driver != null )
    {
      // just need to update what is free to use
      this.vmInfo.setMemLoad( this.monitor.getUsedPhysicalMemorySize() );
      this.vmInfo.setCPULoad(this.monitor.getSystemCpuLoad());
      this.vmInfo.setFreeDiskSpace(this.monitor.getFreeDiskSpace());
    
      this.driver.sendFrameworkMessage(this.vmInfo.toString().getBytes());
    }
  }
  
  /**
   * Sets all the initial parameters of the resource running the executor
   * 
   */
  private void setResourceParameters()
  {
    this.logger.debug("Setting the resource parameters");
    if( this.agentInfo != null )
    {
      String iid = null; 
      String sid = null;
      // Getting the attribute information
      for( Attribute attr : this.agentInfo.getAttributesList() )
      {
        switch( attr.getName() )
        {
        case CcdpUtils.KEY_INSTANCE_ID:
          iid = attr.getText().getValue();
          break;
        case CcdpUtils.KEY_SESSION_ID:
          sid = attr.getText().getValue();
          break;
        }
      }
      this.vmInfo.setInstanceId(iid);
      this.vmInfo.setAssignedSession(sid);
      this.vmInfo.setAgentId(this.agentInfo.getId().getValue() );
      this.vmInfo.setHostname(this.agentInfo.getHostname());
      this.vmInfo.setTotalMemory(this.monitor.getTotalPhysicalMemorySize());
      this.vmInfo.setFreeMemory(this.monitor.getFreePhysicalMemorySize());
      this.vmInfo.setCPU(this.monitor.getTotalNumberCpuCores());
      this.vmInfo.setCPULoad(this.monitor.getSystemCpuLoad());
      this.vmInfo.setDisk(this.monitor.getTotalDiskSpace());
      this.vmInfo.setFreeDiskSpace(this.monitor.getFreeDiskSpace());
    }
  }
  
  /**
   * This is invoked when the slave disconnects from the executor, which 
   * typically indicates a slave restart.  Rarely should an executor need to do 
   * anything special here
   * 
   * @param driver The executor driver that was disconnected.
   */
  @Override
  public void disconnected(ExecutorDriver driver)
  {
    this.logger.info("Disconnecting");
    String msg = "ERROR: Slave was disconnected";
    this.driver.sendFrameworkMessage(msg.getBytes());
    driver.abort();
  }

  /**
   * This callback is invoked after a fatal error occurs.  When this callback is
   * invoked the driver is no longer running.
   * 
   * @param driver The executor driver that was aborted due this error.
   * @param errorMsg the error message
   */
  @Override
  public void error(ExecutorDriver driver, String errorMsg)
  {
    this.logger.error("Driver no longer running, got an error: " + errorMsg);
//    this.driver.abort();
  }

  /**
   * Framework messages are a simple (but not guaranteed) way to communicate 
   * between the scheduler and its executors by sending arbitrary data 
   * serialized as bytes.  
   * 
   * Note that framework messages are not routed to particular tasks
   * 
   * @param driver The executor driver that was aborted due this error.
   * @param msg serialized message as bytes
   * 
   */
  @Override
  public void frameworkMessage(ExecutorDriver driver, byte[] msg)
  {
    this.vmInfo.setAssignedSession( new String(msg) ); 
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
    String tid = taskId.getValue();
    this.logger.info("Killing Task: " + tid);
    
    synchronized( this )
    {
//      CcdpTaskRunner task = this.tasks.get(taskId);
      CcdpTaskRunner task = this.tasks.remove(taskId);
      if( task != null )
      {
        task.killTask();
        for( CcdpTaskRequest t : this.vmInfo.getTasks() )
        {
          if( t.getTaskId().equals(tid) )
          {
            this.logger.info("Task " + tid + " was killed, removing it");
            this.vmInfo.getTasks().remove(t);
            break;
          }
        }
      }
    }
    
//    if( this.tasks.isEmpty() )
//      driver.abort();
  }

  /**
   * Invoked when a task has been launched on this executor (initiated via 
   * SchedulerDriver.launchTasks(OfferID, TaskInfo, Filters). Note that this 
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
        CcdpTaskRequest taskReq = new CcdpTaskRequest(taskId.getValue());
        
        this.vmInfo.addTask(taskReq);
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
    this.setResourceParameters();
    this.execInfo = exec;
    this.timer = new ThreadedTimerTask(this, 2000);
    
    
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
    this.agentInfo = slave;
    this.setResourceParameters();
  }

  /**
   * This callback informs the executor to gracefully shut down.  It is called 
   * when a slave restart fails to complete within the grace period or when the
   * executor's framework completes.  The executor will be forcibly killed if 
   * shutdown doesn't complete within 5 seconds (the default, configurable on 
   * the slave command line with --executor_shutdown_grace_period).
   * 
   * @param driver The executor driver that was aborted due this error.
   */
  @Override
  public void shutdown(ExecutorDriver driver)
  {
    this.logger.info("Shuting Down Executor");
    if( this.timer != null )
      this.timer.stop();
    driver.abort();
  }

  /**
   * Runs the show, but it cannot be used as it needs many components from 
   * mesos
   * 
   * @param args the command line arguments
   * @throws Exception an exception is thrown if an error occurs
   */
  public static void main(String[] args) throws Exception
  {
    CcdpUtils.configureProperties();
    CcdpUtils.configLogger();
    
    Executor executor = new CcdpCommandExecutor();
    ExecutorDriver driver = new MesosExecutorDriver(executor);
    int exitCode = driver.run() == Status.DRIVER_STOPPED ? 0 : 1;
    System.out.println("Executor ended with exit code: " + exitCode);
    driver.abort();
    System.exit(exitCode);
  }
}
