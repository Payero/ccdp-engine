package com.axios.ccdp.utils;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import org.apache.log4j.Logger;

/**
 * Simple utility class used to determine when to stop a continuous loop almost
 * immediately.  This is a preferred method rather than having a loop with a
 * timeout.  The main reason is because the loop will not exit until the 
 * timeout expires
 * 
 * @author Oscar E. Ganteaume
 *
 */
public class ThreadController
{
  /**
   * Generates debug print statements based on the verbosity level.
   */
  private Logger logger = Logger.getLogger(ThreadController.class.getName());
  /**
   * Limits the access to part of the codes in order to make thread safe
   */
  private Lock lock = new ReentrantLock();
  /**
   * Signals all threads waiting to for the condition to change
   */
  private Condition cond = lock.newCondition();
  /**
   * Determines whether or not the controller flag is set
   */
  private boolean flag;
  
  public ThreadController()
  {

  }
  
  /**
   * Waits until the flag is set or the thread is interrupted
   */
  public void doWait()
  {
    lock.lock();
    try
    {
      this.cond.await();
    }
    catch( InterruptedException e )
    {
      this.logger.warn("Got an InterruptedException: " + e.getMessage());
    }
    finally
    {
      this.lock.unlock();
    }
  }
  
  /**
   * Waits for the number of seconds specified in the argument.  If the number
   * of seconds is less than 0 then it throws an IllegalArgumentException
   * 
   * @param seconds the number of seconds to wait 
   * 
   * @throws IllegalArgumentException an IllegalArgumentException is thrown
   *         if the number of seconds is less than zero
   */
  public void doWait(double seconds)
  {
    if( seconds < 0 )
    {
      String msg = "The number of seconds cannot be less than zero";
      throw new IllegalArgumentException(msg);
    }
    
    lock.lock();
    try
    {
      this.cond.await( (long)(( seconds )* 1000), TimeUnit.MILLISECONDS);
    }
    catch( InterruptedException e )
    {
      this.logger.warn("Got an InterruptedException: " + e.getMessage());
    }
    finally
    {
      this.lock.unlock();
    }
  }
  
  /**
   * Determines whether or not the flag has been set.
   * 
   * @return true iff the flag has been set
   */
  public boolean isSet()
  {
    this.lock.lock();
    try
    {
      return this.flag;
    }
    finally
    {
      this.lock.unlock();
    }
  }
  
  /**
   * Sets the flag which eventually will notify all waiting threads
   */
  public void set()
  {
    this.setFlag(true);
  }
  
  /**
   * Sets the flag to false which will cause notifying all the threads
   */
  public void clear()
  {
    this.setFlag(false);
  }
  
  /**
   * Sets the flag and generates a signal notification to all waiting threads
   * 
   * @param flag the flag modifying the current status of the event
   */
  private void setFlag( boolean flag )
  {
    this.lock.lock();
    try
    {
      this.flag = flag;
      this.cond.signalAll();
    }
    finally
    {
      this.lock.unlock();
    }
  }
}
