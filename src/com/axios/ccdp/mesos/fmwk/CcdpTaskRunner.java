package com.axios.ccdp.mesos.fmwk;

import java.io.File;
import java.lang.ProcessBuilder.Redirect;
import java.util.ArrayList;
import java.util.List;

import org.apache.log4j.Logger;
import org.apache.mesos.Protos.TaskID;
import org.apache.mesos.Protos.TaskInfo;
import org.apache.mesos.Protos.TaskState;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;


/**
 * Simple class that is used by a Mesos Executor and is intended to run on a 
 * Mesos Agent node.  The main reason of this class is to allow the Executor
 * to run multiple tasks simultaneously.
 * 
 * @author Oscar E. Ganteaume
 *
 */
public class CcdpTaskRunner extends Thread
{
  /**
   * Generates debug print statements based on the verbosity level.
   */
  private Logger logger = Logger.getLogger(CcdpTaskRunner.class.getName());
  /**
   * Stores the object requesting the task execution
   */
  private CcdpCommandExecutor executor = null;
  /**
  * Stores a reference to the process we launched so that we can wait for it 
  * to complete
  */
  private Process process;
  /**
  * Used to communicate the task;s status to Mesos.  The driver API to 
  * communicate with the scheduler requires the TaskID as an argument
  */
  private TaskID taskId;
  /**
   * Stores all the information related to this task
   */
  private TaskInfo taskInfo;
  /**
   * Contains all the initial commands to be able to run in a bash shell.  The
   * actual command is added in the startProcess()
   */
  private List<String> cmdArgs = new ArrayList<String>();
  
  /**
   * Creates all the ObjectNode and ArrayNode objects
   */
  private ObjectMapper mapper = new ObjectMapper();
  
  /**
   * Creates a new Task to be executed by a Mesos Executor 
   * 
   * @param taskInfo the name of the task to run
   * @param executor the actual process to execute the task
   */
  public CcdpTaskRunner(TaskInfo taskInfo, CcdpCommandExecutor executor)
  {
    this.taskId = taskInfo.getTaskId();
    this.logger.info("Creating a new CCDP Task: " + this.taskId.getValue());
    this.taskInfo = taskInfo;
    this.executor = executor;
    
    // adding the basic commands to run it on a shell
    this.cmdArgs.add("/bin/bash");
    this.cmdArgs.add("-c");
  }

  /**
   * Runs the actual command.
   */
  @Override
  public void run()
  {
    this.logger.info("Executing the Task");
    try
    {
      byte[] taskData = this.taskInfo.getData().toByteArray();
      String msg = "The Task JSON Configuration: " + new String(taskData);
      this.logger.debug(msg);
      
      JsonNode node = this.mapper.readTree( new String( taskData, "UTF-8") );
      
      this.process = this.startProcess(node);
      
      int exitCode;
      try
      {
        exitCode = this.process.waitFor();
      }
      catch (Exception e)
      {
        this.logger.error("Message: " + e.getMessage(), e);
        exitCode = -99;
      }
      
      synchronized( this )
      {
        if ( this.process == null )
        {
          this.logger.info("The process is null");
          return;
        }
        this.process = null;
        if( exitCode == 0 )
        {
          this.logger.info("Task Finished properly");
          this.executor.statusUpdate(this.taskId, TaskState.TASK_FINISHED, 
                                     null);
        }
        else
        {
          msg = "Task finished with a non-zero value (" + exitCode + ")";
          this.logger.info(msg);
          
          this.executor.statusUpdate(this.taskId, TaskState.TASK_FAILED, msg);
        }
      }
    }
    catch( Exception e )
    {
      this.logger.error("Message: " + e.getMessage(), e);
      this.executor.statusUpdate(this.taskId, TaskState.TASK_ERROR, 
                                 e.getMessage());
    }
  }
  
  /**
   * Starts a new Process by executing the command stored in the 'cmd' key in
   * the given JsonObject.  The final command would look as follow:
   * 
   *    bash -c <command to run>
   *    
   * The standard out and standard error are stored as files in the 
   * 'MESOS_DIRECTORY' path.  This environment variable is set by the slave
   *    
   * @param job the object containing the command to run in the shell
   * @return a Process object with the process running
   * 
   * @throws Exception an exception is thrown if an error is found while 
   *         attempting to execute the command
   */
  private Process startProcess( JsonNode job  ) throws Exception
  {
    this.logger.info("Launching a new Process: " + job);
    
    this.cmdArgs.add(job.get("cmd").asText());
    String mesosDir = System.getenv("MESOS_DIRECTORY");
    
    ProcessBuilder pb = new ProcessBuilder(this.cmdArgs);
    this.logger.info("The Mesos Directory is set at: " + mesosDir);
    File stdout = new File(mesosDir, "child.stdout");
    File stderr = new File(mesosDir, "child.stderr");
    
    pb.redirectOutput(Redirect.to(stdout));
    pb.redirectError(Redirect.to(stderr));
    return pb.start();
  }
  
  /**
   * Kills the current running task and sends an update to the Executor
   */
  public void killTask()
  {
    if( this.process != null )
    {
      this.process.destroy();
      this.executor.statusUpdate( this.taskId, TaskState.TASK_KILLED, null );
      this.process = null;
    }
  }
}
