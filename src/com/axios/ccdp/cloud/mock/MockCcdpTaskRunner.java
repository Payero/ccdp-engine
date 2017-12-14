package com.axios.ccdp.cloud.mock;

import java.io.File;
import java.lang.ProcessBuilder.Redirect;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import org.apache.log4j.Logger;

import com.axios.ccdp.connections.intfs.CcdpTaskLauncher;
import com.axios.ccdp.tasking.CcdpTaskRequest;
import com.axios.ccdp.tasking.CcdpTaskRequest.CcdpTaskState;
import com.axios.ccdp.utils.CcdpUtils;


/**
 * Simple class that is used by a CCDP Agent to mock the execution of tasks.  
 * It looks for keywords in the command and does different operations based
 * on that.  For instance;
 * 
 *         keyword                            Result
 *   ------------------------------------------------------------------------
 *    mock-pause-task:   Waits for some random time between a period of time
 *    mock-cpu-task:     Maximizes CPU usage for a define period of time
 *    mock-failed-task:  Simulates a failed command execution
 * 
 * @author Oscar E. Ganteaume
 *
 */
public class MockCcdpTaskRunner extends Thread 
{
  /**
   * The default upper limit to bound the time to pause
   */
  public static final int UPPER_LIMIT = 30;
  
  /**
   * All the different actions this task runner can perform
   * 
   */
  public enum MockActions 
  {
    MOCK_PAUSE("mock-pause-task"),
    MOCK_CPU("mock-cpu-task"),
    MOCK_FAILED("mock-failed-task");
    
    private final String text;
    
    private MockActions( final String text )
    {
      this.text = text;
    }
    
    @Override
    public String toString()
    {
      return text;
    }
  };
  
    
  /**
   * Stores the object responsible for printing items to the screen
   */
  private Logger logger = Logger.getLogger(MockCcdpTaskRunner.class.getName());
  /**
   * Stores the object requesting the task execution
   */
  private CcdpTaskLauncher launcher = null;
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
  public MockCcdpTaskRunner(CcdpTaskRequest task, CcdpTaskLauncher agent)
  {
    this.task = task;
    this.logger.info("Creating a new CCDP Task: " + this.task.getTaskId());
    this.launcher = agent;
    
    // adding the basic commands to run it on a shell
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
    String action = this.cmdArgs.get(0);
    this.logger.info("Executing the Task: " + action);
    MockActions mock = MockActions.valueOf(action);
    
    switch( mock )
    {
      case MOCK_PAUSE:
        this.mockPause();
        break;
      case MOCK_CPU:
        this.mockCPUUsage();
        break;
      case MOCK_FAILED:
        this.mockFailed();
        break;
      default:
        this.logger.error("Could not find action " + mock.toString());
    }
  }

  /**
   * Simulates a task running for a determined period of time.  If a bound limit
   * is passed then is used as the upper limit otherwise it uses the default of
   * 60 seconds
   */
  private void mockPause()
  {
    try
    {
      this.logger.info("Running a Pause Task");
      int sz = this.cmdArgs.size();
      Random rand = new Random(MockCcdpTaskRunner.UPPER_LIMIT);
      int secs = rand.nextInt();
      if( sz >= 2 )
         secs = rand.nextInt( Integer.valueOf( this.cmdArgs.get(1) ) );
      this.logger.debug("Pausing for " + secs + " seconds");
      CcdpUtils.pause(secs);
      this.task.setState(CcdpTaskState.SUCCESSFUL);
      this.launcher.statusUpdate(this.task, null);
    }
    catch( Exception e )
    {
      this.logger.error("Message: " + e.getMessage(), e);
      String txt = "Could not pause the task.  Got the following " +
          "error message " + e.getMessage();
      this.task.setState(CcdpTaskState.FAILED);
      this.launcher.statusUpdate(this.task, txt);
    }
  }
  
  /**
   * It loads the CPU to a specific percentage for a predetermined period of 
   * time.  If not provided then it loads the CPU to 100% for a random number
   * of seconds between 0 and the default upper limit.  If the third argument
   * is less than zero then it does not end.
   */
  private void mockCPUUsage()
  {
    try 
    {
      Random rand = new Random();
      int secs = rand.nextInt();
      double load = 100;
      int sz = this.cmdArgs.size();
      if( sz >= 2 )
      {
        int time = Integer.valueOf( this.cmdArgs.get(1) );
        if( time <= 0 )
        {
          this.logger.info("Time is less or equal than zero");
          secs = Integer.MAX_VALUE;
        }
      }
      
      if( sz >= 3 )
        load = Double.valueOf( this.cmdArgs.get(2) );
      
      this.logger.debug("Maximizing CPU for " + secs + " seconds");
      secs *= 1000;
      
      long startTime = System.currentTimeMillis();

      // Loop for the given duration
      while (System.currentTimeMillis() - startTime < secs) 
      {
        // Every 100ms, sleep for the percentage of unladen time
        if (System.currentTimeMillis() % 100 == 0) 
        {
          Thread.sleep((long) Math.floor((1 - load) * 100));
        }
      }
      this.task.setState(CcdpTaskState.SUCCESSFUL);
      this.launcher.statusUpdate(this.task, null);
    } 
    catch (InterruptedException e) 
    {
      this.logger.error("Message: " + e.getMessage(), e);
      String txt = "Could not load CPU to desired value.  Got the following " +
          "error message " + e.getMessage();
      this.task.setState(CcdpTaskState.FAILED);
      this.launcher.statusUpdate(this.task, txt);
    }
  }
  
  private void mockFailed()
  {
    try
    {
      this.logger.info("Running a Failed Task");
      int sz = this.cmdArgs.size();
      Random rand = new Random(MockCcdpTaskRunner.UPPER_LIMIT);
      int secs = rand.nextInt();
      if( sz >= 2 )
         secs = rand.nextInt( Integer.valueOf( this.cmdArgs.get(1) ) );
      this.logger.debug("Pausing for " + secs + " seconds");
      CcdpUtils.pause(secs);
      this.task.setState(CcdpTaskState.FAILED);
      this.launcher.statusUpdate(this.task, null);
    }
    catch( Exception e )
    {
      this.logger.error("Message: " + e.getMessage(), e);
      String txt = "Could not pause the task.  Got the following " +
          "error message " + e.getMessage();
      this.task.setState(CcdpTaskState.FAILED);
      this.launcher.statusUpdate(this.task, txt);
    }
  }
}

