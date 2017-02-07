package com.axios.ccdp.mesos.utils;

import java.util.Timer;
import java.util.TimerTask;

import org.apache.log4j.Logger;

/**
 * Class used to run a task at a scheduled rate.  At the end of every cycle the
 * onEvent() method of the main application is invoked.
 * 
 * @author Oscar E. Ganteaume
 *
 */
public class ThreadedTimerTask
{

  /**
   * Generates debug print statements based on the verbosity level.
   */
  private Logger logger = Logger.getLogger(ThreadedTimerTask.class.getName());
  /**
   * The object that makes the call to the TimerTask
   */
  private Timer timer = null;
  /**
   * The time to wait in milliseconds before invoking the onEvent() method for
   * the first time
   */
  private long delay = 0;
  /**
   * The time to wait between cycles
   */
  private long period = 0;
  /**
   * A thread used to link the main application and the timer object
   */
  private EventTask task = null;
  
  /**
   * Instantiates a new ThreadedTimer object that will start invoking the 
   * onEvent() method immediately follow by the cyclic period of 'period'
   * milliseconds.
   *  
   * @param main the main application to notify of the end of a period
   * @param period the time to wait between cycles
   */
  public ThreadedTimerTask(TaskEventIntf main, long period)
  {
    this(main, 0, period);
  }
  
  /**
   * Instantiates a new ThreadedTimer object that will start invoking the 
   * onEvent() method after 'delay' milliseconds followed by the cyclic period 
   * of 'period' milliseconds.
   *  
   * @param main the main application to notify of the end of a period
   * @param delay the time to wait before start invoking the onEvent() method
   * @param period the time to wait between cycles
   */
  public ThreadedTimerTask(TaskEventIntf main, long delay, long period)
  {
    this.setDelay(delay);
    this.setPeriod(period);
    
    // running the timer as a daemon
    this.timer = new Timer(true);
    if( main == null )
    {
      this.logger.error("The main application cannot be null");
      this.stop();
    }
    this.task = new EventTask(main);
    this.timer.scheduleAtFixedRate(this.task, this.delay,  this.period);
  }
  
  
  /**
   * Gets the time to wait prior start invoking the onEvent() method on the main
   * application
   * 
   * @return the delay time to wait prior start invoking the onEvent() method
   */
  public long getDelay()
  {
    return delay;
  }

  /**
   * Sets the time to wait prior start invoking the onEvent() method on the main
   * application.  
   * 
   * It throws an IllegalArgumentException If the argument is less than zero 
   * 
   * @param delay the time to wait prior start invoking the onEvent() method
   * 
   * @throws IllegalArgumentException an IllegalArgumentException is thrown if
   *         the delay is less than zero
   */
  public void setDelay(long delay)
  {
    if( delay < 0 )
      throw new IllegalArgumentException("The delay cannot be negative");
    
    this.delay = delay;
  }

  /**
   * Gets the time to wait prior start invoking the onEvent() method on the main
   * application
   * 
   * @return the time to wait prior start invoking the onEvent() method
   */
  public long getPeriod()
  {
    return period;
  }

  /**
   * Sets the time to wait prior start invoking the onEvent() method on the main
   * application.
   * 
   * It throws an IllegalArgumentException If the argument is less than zero
   * 
   * @param period the time to wait prior start invoking the onEvent() method
   * 
   * @throws IllegalArgumentException an IllegalArgumentException is thrown if
   *         the period is less than zero
   */
  public void setPeriod(long period)
  {
    if( period < 0 )
      throw new IllegalArgumentException("The period cannot be negative");
    
    this.period = period;
  }

  /**
   * Terminates the timer and exits
   */
  public void stop()
  {
    this.logger.info("Stopping the Threader Timer Task");
    if( this.timer != null )
      this.timer.cancel();
  }
  
  /**
   * Simple inside class used to notify the main application that a new cycle
   * or period has ended
   * 
   * @author Oscar E. Ganteaume
   *
   */
  class EventTask extends TimerTask
  {
    private TaskEventIntf main;
    
    public EventTask( TaskEventIntf main )
    {
      this.main = main;
    }
    
    public void run()
    {
      this.main.onEvent();
    }
  }
}
