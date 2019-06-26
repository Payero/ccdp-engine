package com.axios.ccdp.impl.cloud.sim;

import java.util.ArrayList;
import java.util.List;

import org.apache.log4j.Logger;

import com.axios.ccdp.intfs.CcdpTaskLauncher;
import com.axios.ccdp.tasking.CcdpTaskRequest;
import com.axios.ccdp.tasking.CcdpTaskRequest.CcdpTaskState;
import com.axios.ccdp.utils.CcdpUtils;
import com.axios.ccdp.utils.ThreadController;


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
public class SimCcdpTaskRunner extends Thread
{
	/**
	 * The default upper limit to bound the time to pause
	 */
	public static final int UPPER_LIMIT = 30;
	
	/**
	 * Tells the thread when to stop processing gracefully
	 */
	private ThreadController controller = new ThreadController();
	
	/**
	 * All the different actions this task runner can perform
	 *
	 */
	public enum MockActions
	{
		MOCK_PAUSE("mock-pause-task"),
		MOCK_CPU("mock-cpu-task"),
		MOCK_FAIL("mock-fail-task");

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
	private Logger logger = Logger.getLogger(SimCcdpTaskRunner.class.getName());
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
	public SimCcdpTaskRunner(CcdpTaskRequest task, CcdpTaskLauncher agent)
	{
		this.task = task;
		this.logger.info("Creating a new CCDP Task: " + this.task.getTaskId());
		this.launcher = agent;
		this.cmdArgs = task.getCommand();
	}


	/**
	 * Runs the actual command.
	 */
	@Override
	public void run()
	{
		if (this.cmdArgs.size() > 0) {
			String action = this.cmdArgs.get(0);
			this.logger.info("Executing the Task: " + action);
			MockActions mock = MockActions.MOCK_PAUSE;
			try
			{
				mock = MockActions.valueOf(action);
			}
			catch(Exception e )
			{
				this.logger.warn("Could not find action " + mock.toString() +
						" using MOCK_PAUSE");
			}

			switch( mock )
			{
			case MOCK_PAUSE:
				this.mockPause();
				break;
			case MOCK_CPU:
				this.mockCPUUsage();
				break;
			case MOCK_FAIL:
				this.mockFailed();
				break;

			}
		} else {
			this.logger.warn("Task " + this.task.getName() + " has no configured command. Nothing to do.");
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
			int secs = SimCcdpTaskRunner.UPPER_LIMIT;
			double cpu = this.task.getCPU();
			
			if( cpu < 100 )
			{
			  this.logger.debug("CPU is not set tp 100, simulating waiting time");
  			try
  			{
  				if( sz >= 2 )
  					secs = Integer.valueOf( this.cmdArgs.get(1) );
  			}
  			catch( Exception e )
  			{
  				this.logger.warn("Could not parse integer using UPPER_LIMIT");
  			}
  
  			if( secs == 0 )
  				secs = 1;
  
  			this.logger.debug("Pausing for " + secs + " seconds");
  			CcdpUtils.pause(secs);
  			this.task.setState(CcdpTaskState.SUCCESSFUL);
  			this.launcher.statusUpdate(this.task);
			}
			else
			{
			  this.logger.info("CPU set to 100, waiting forever!");
			  while( !this.controller.isSet() )
			  {
			    CcdpUtils.pause(1);
			  }
			}
			
		}
		catch( Exception e )
		{
			this.logger.error("Message: " + e.getMessage(), e);
			String txt = "Could not pause the task.  Got the following " +
					"error message " + e.getMessage();
			this.task.setState(CcdpTaskState.FAILED);
			this.launcher.onTaskError(this.task, txt);
		}
	}

	/**
	 * It loads the CPU to a specific percentage for a predetermined period of
	 * time.  If not provided then it loads the CPU to 100% for the number
	 * of seconds set as argument.  If the second argument is less than zero then
	 * it does not end.
	 */
	private void mockCPUUsage()
	{
		try
		{
			int secs = SimCcdpTaskRunner.UPPER_LIMIT;
			double load = 1;

			int sz = this.cmdArgs.size();
			if( sz >= 2 )
			{
				this.logger.debug("The assigned time " + this.cmdArgs.get(1));
				int time = Integer.valueOf( this.cmdArgs.get(1) );
				if( time > 0 )
					secs = time;
			}

			if( sz >= 3 )
				load = Double.valueOf( this.cmdArgs.get(2) );

			this.logger.debug("Maximizing CPU for " + secs + " seconds");
			secs *= 1000;

			int cores = Runtime.getRuntime().availableProcessors();
			int numThreadsPerCore = 2;
			int amount = cores * numThreadsPerCore;

			Thread[] threads = new Thread[amount];
			for (int thread = 0; thread < amount; thread++)
			{
				Thread t = new BusyThread("Thread" + thread, load, secs);
				t.start();
				threads[thread] = t;

			}
			// once the threads are launched then let's wait for them
			for( int i = 0; i < amount; i++ )
				threads[i].join();

			this.task.setState(CcdpTaskState.SUCCESSFUL);
			this.launcher.statusUpdate(this.task);
		}
		catch ( Exception e)
		{
			this.logger.error("Message: " + e.getMessage(), e);
			String txt = "Could not load CPU to desired value.  Got the following " +
					"error message " + e.getMessage();
			this.task.setState(CcdpTaskState.FAILED);
			this.launcher.onTaskError(this.task, txt);
		}
	}

	/**
	 * Runs a tasks for a determined number of seconds and then  sends a FAILED
	 * response back
	 */
	private void mockFailed()
	{
		try
		{
			this.logger.info("Running a Failed Task");
			int sz = this.cmdArgs.size();
			int secs = SimCcdpTaskRunner.UPPER_LIMIT;

			if( sz >= 2 )
				secs = Integer.valueOf( this.cmdArgs.get(1) );
			if( secs == 0 )
				secs = 1;

			CcdpTaskState state = this.task.getState();
			while( !CcdpTaskState.FAILED.equals( state ) )
			{
				state = this.task.getState();
				this.logger.debug("Pausing for " + secs + " seconds, State " + state);
				CcdpUtils.pause(secs);
				this.task.fail();
				String msg = "Task " + this.task.getTaskId() + " failed to execute, retrying";
				this.launcher.onTaskError(this.task, msg);
			}

			this.launcher.statusUpdate(this.task);
		}
		catch( Exception e )
		{
			this.logger.error("Message: " + e.getMessage(), e);
			String txt = "Could not pause the task.  Got the following " +
					"error message " + e.getMessage();
			this.task.setState(CcdpTaskState.FAILED);
			this.launcher.onTaskError(this.task, txt);
		}
	}


	/**
	 * Thread that actually generates the given load
	 *
	 * @author Oscar E. Ganteaume
	 */
	public static class BusyThread extends Thread
	{
		/**
		 * Stores the load to generate
		 */
		private double load;
		/**
		 * How long to generate the load
		 */
		private long duration;

		/**
		 * Constructor which creates the thread
		 *
		 * @param name Name of this thread
		 * @param load Load % that this thread should generate
		 * @param duration Duration that this thread should generate the load for
		 */
		public BusyThread(String name, double load, long duration)
		{
			super(name);

			if( load <= 0 || load > 1 )
				throw new IllegalArgumentException("The load needs to be between 0 and 1");

			if( duration < 0 )
				throw new IllegalArgumentException("The duration cannot be negative");

			this.load = load;
			this.duration = duration;
		}

		/**
		 * Generates the load when run
		 */
		@Override
		public void run()
		{
			long startTime = System.currentTimeMillis();
			try
			{
				// Loop for the given duration
				while (System.currentTimeMillis() - startTime < duration)
				{
					// Every 100ms, sleep for the percentage of unladen time
					if (System.currentTimeMillis() % 100 == 0)
					{
						Thread.sleep((long) Math.floor((1 - load) * 100));
					}
				}
			}
			catch (InterruptedException e)
			{
				e.printStackTrace();
			}
		}
	}

}

