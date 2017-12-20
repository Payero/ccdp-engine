package com.axios.ccdp.fmwk;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.lang.ProcessBuilder.Redirect;
import java.util.ArrayList;
import java.util.List;

import org.apache.log4j.Logger;

import com.axios.ccdp.connections.intfs.CcdpTaskLauncher;
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
public class CcdpTaskRunnerTest extends Thread 
{
  /**
   * Generates debug print statements based on the verbosity level.
   */
  private Logger logger = Logger.getLogger(CcdpTaskRunnerTest.class.getName());
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
  public CcdpTaskRunnerTest(CcdpTaskRequest task, CcdpTaskLauncher agent)
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
  public void runMe()
  {
    try
    {
      StringBuffer buf = new StringBuffer();
      for( String arg : this.task.getCommand() )
      {
        buf.append(arg);
        buf.append(" ");
      }
      String path = System.getenv("CCDP_HOME");
      
      String cmd = path + "/bin/test.sh /data/ccdp/ccdp-engine/python/CcdpModuleLauncher.py -f /data/ccdp/webapp/frontend/server/src/modules/csv_reader.py -c CsvReader -a eydicm9rZXJfaG9zdCc6ICdheC1jY2RwLmNvbScsICdicm9rZXJfcG9ydCc6IDYxNjE2LCAndGFza19pZCc6ICdjc3YtcmVhZGVyJ30=";
      
      boolean run_rand = false;
      if( run_rand )
        cmd = path + "/bin/test.sh /data/ccdp/ccdp-engine/python/ccdp_mod_test.py -a testRandomTime -p min=5,max=10";
      
      this.logger.debug("Running " + cmd);    
      Process runtime = Runtime.getRuntime().exec(cmd);
      int exitCode;
      try
      {
        exitCode = runtime.waitFor();
        BufferedReader reader =
            new BufferedReader(new InputStreamReader(runtime.getInputStream()));

        StringBuffer output = new StringBuffer();
        String line = "";
        while ((line = reader.readLine())!= null) 
        {
          output.append(line + "\n");
        }
        
        this.logger.debug("The Output " + output);
      }
      catch (Exception e)
      {
        this.logger.error("Message: " + e.getMessage(), e);
        exitCode = -99;
      }
      
      synchronized( this )
      {
        if ( runtime == null )
        {
          this.logger.info("The process is null");
          return;
        }
        
        runtime = null;
        if( exitCode == 0 )
        {
          this.task.setState(CcdpTaskState.SUCCESSFUL);
          this.logger.info("Task Finished properly, State: " + this.task.getState());
          this.launcher.statusUpdate(this.task);
        }
        else
        {
          this.task.setState(CcdpTaskState.FAILED);
          String msg = "Task finished with a non-zero value (" + exitCode + "), State: " + this.task.getState();
          this.logger.info(msg);
          this.launcher.onTaskError(this.task, msg);
        }
      }
      
      
    }
    catch( Exception e )
    {
      e.printStackTrace();
    }
  }
  
  
  
  public void run()
  {
    this.logger.info("Executing the Task");
    try
    {
      this.process = this.startProcess();
      BufferedReader reader =
          new BufferedReader(new InputStreamReader(this.process.getInputStream()));

      StringBuffer output = new StringBuffer();
      String line = "";
      while ((line = reader.readLine())!= null) 
      {
        output.append(line + "\n");
      }
      this.logger.debug("Output " + output );
      
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
          this.task.setState(CcdpTaskState.SUCCESSFUL);
          this.logger.info("Task Finished properly, State: " + this.task.getState());
          this.launcher.statusUpdate(this.task);
        }
        else
        {
          this.task.setState(CcdpTaskState.FAILED);
          String msg = "Task finished with a non-zero value (" + exitCode + "), State: " + this.task.getState();
          this.logger.info(msg);
          this.launcher.statusUpdate(this.task);
        }
      }
    }
    catch( Exception e )
    {
      System.out.println("STATE WAS SET TO FAILEEEEEED :oooooooo");
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
//    this.logger.info("Launching a new Process: " + this.cmdArgs.toString() );
    String path = System.getenv("CCDP_HOME");
    List<String> cmd = new ArrayList<>();
    //cmd.add(path + "/bin/test.sh");
    cmd.add("/bin/bash");
    cmd.add("-c");
    cmd.add("/data/ccdp/ccdp-engine/python/CcdpModuleLauncher.py -f /data/ccdp/webapp/frontend/server/src/modules/csv_reader.py -c CsvReader -a eydicm9rZXJfaG9zdCc6ICdheC1jY2RwLmNvbScsICdicm9rZXJfcG9ydCc6IDYxNjE2LCAndGFza19pZCc6ICdjc3YtcmVhZGVyJ30=");
    
    
    boolean run_rand = false;
    if( run_rand )
    {
      cmd = new ArrayList<>();
      cmd.add(path + "/bin/test.sh");
      cmd.add("/data/ccdp/ccdp-engine/python/ccdp_mod_test.py -a testRandomTime -p min=5,max=10");
    }    
    
    this.logger.info("Launching a new Process: " + cmd );
//    ProcessBuilder pb = new ProcessBuilder(this.cmdArgs);
    ProcessBuilder pb = new ProcessBuilder(cmd);
    pb.redirectOutput(ProcessBuilder.Redirect.INHERIT);
    pb.redirectErrorStream(true);
    
    if( this.task.getConfiguration().containsKey(CcdpUtils.CFG_KEY_LOG_DIR) )
    {
      String log_dir = 
          this.task.getConfiguration().get(CcdpUtils.CFG_KEY_LOG_DIR);
      
      this.logger.info("The Mesos Directory is set at: " + log_dir);
      String err = this.task.getTaskId() + "-stderr";
      String out = this.task.getTaskId() + "-stdout";
      File stdout = new File("/tmp", out);
      File stderr = new File("/tmp", err);
      
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

