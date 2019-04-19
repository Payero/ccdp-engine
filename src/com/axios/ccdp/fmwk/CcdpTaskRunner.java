package com.axios.ccdp.fmwk;

import java.io.File;
import java.lang.ProcessBuilder.Redirect;
import java.util.ArrayList;
import java.util.List;

import org.apache.log4j.Logger;

import com.axios.ccdp.intfs.CcdpTaskLauncher;
import com.axios.ccdp.tasking.CcdpTaskRequest;
import com.axios.ccdp.tasking.CcdpTaskRequest.CcdpTaskState;
import com.axios.ccdp.utils.CcdpUtils;


/**
 * Simple class that is used by a CCDP Agent to execute multiple tasks at the
 * same time.
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
  private CcdpTaskLauncher launcher = null;
  /**
  * Stores a reference to the process we launched so that we can wait for it 
  * to complete
  */
  private Process process;
  /**
   * Stores all the information related to this task
   */
  private CcdpTaskRequest task;
  /**
   * Contains all the initial commands to be able to run in a bash shell.  The
   * actual command is added in the startProcess()
   */
  private List<String> cmdArgs = new ArrayList<String>();
  
  /**
   * Creates a new Task to be executed by a CcdpAgent 
   * 
   * @param task the name of the task to run
   * @param agent the actual process to execute the task
   */
  public CcdpTaskRunner(CcdpTaskRequest task, CcdpTaskLauncher agent)
  {
    this.task = task;
    this.logger.info("Creating a new CCDP Task: " + this.task.getTaskId());
    this.launcher = agent;
    
    // adding the basic commands to run it on a shell
    this.cmdArgs.add("/bin/bash");
    this.cmdArgs.add("-c");
    StringBuffer buf = new StringBuffer();
    for( String cmd : task.getCommand() )
    {
      buf.append(cmd);
      buf.append(" ");
    }
    this.cmdArgs.add( buf.toString().trim() );
  }

  /**
   * Runs the actual command.
   */
  @Override
  public void run()
  {
    String tid = this.task.getTaskId();
    this.logger.info("Executing the Task " + tid);
    
    try
    {
      CcdpTaskState state = this.task.getState();
      int exitCode = -10;
      // Tasks have number of retries so need to do that
      while( !CcdpTaskState.FAILED.equals(state) )
      {
        this.process = this.startProcess();
        
        try
        {
          exitCode = this.process.waitFor();
          if( exitCode == 0 )
          {
            this.logger.info("Task " + tid + " executed successfully");
            break;
          }
          else
          {
            String msg = "Task " + tid + " failed to execute, retrying";
            this.launcher.onTaskError(this.task, msg);
            this.task.fail();
          }
        }
        catch (Exception e)
        {
          this.logger.error("Message: " + e.getMessage(), e);
          exitCode = -99;
        }  
      }
      
      synchronized( this )
      {
        if( exitCode == 0 )
        {
          this.task.setState(CcdpTaskState.SUCCESSFUL);
          this.logger.info("Task Finished properly, State: " + 
                            this.task.getState());
          this.launcher.statusUpdate(this.task);
        }
        else
        {
          this.task.setState(CcdpTaskState.FAILED);
          String msg = "Task finished with a non-zero value (" + 
                       exitCode + "), State: " + this.task.getState();
          this.logger.info(msg);
          this.launcher.onTaskError(this.task, msg);
        }
      }
    }
    catch( Exception e )
    {
      this.logger.error("Message: " + e.getMessage(), e);
      this.task.setState(CcdpTaskState.FAILED);
      this.launcher.onTaskError(this.task, e.getMessage());
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
   * @return a Process object with the process running
   * 
   * @throws Exception an exception is thrown if an error is found while 
   *         attempting to execute the command
   */
  private Process startProcess( ) throws Exception
  {
    this.logger.info("Launching a new Process: " + this.cmdArgs.toString() );
    
    ProcessBuilder pb = new ProcessBuilder(this.cmdArgs);
    pb.redirectOutput(ProcessBuilder.Redirect.INHERIT);
    pb.redirectErrorStream(true);
    
    if( this.task.getConfiguration().containsKey(CcdpUtils.CFG_KEY_LOG_DIR) )
    {
      String log_dir = 
          this.task.getConfiguration().get(CcdpUtils.CFG_KEY_LOG_DIR);
      
      this.logger.info("The Mesos Directory is set at: " + log_dir);
      String err = this.task.getTaskId() + "-stderr";
      String out = this.task.getTaskId() + "-stdout";
      File stdout = new File(log_dir, out);
      File stderr = new File(log_dir, err);
      
      pb.redirectOutput(Redirect.to(stdout));
      pb.redirectError(Redirect.to(stderr));
      
    }
    
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
      this.task.setState(CcdpTaskState.KILLED);
      this.launcher.statusUpdate( this.task );
      this.process = null;
    }
  }

}

