package com.axios.ccdp.connections.intfs;

import com.axios.ccdp.tasking.CcdpTaskRequest;

/**
 * Simple interface used to enable the TaskRunner to communicate back to the
 * launcher any updates about the task being executed.
 * 
 * @author Oscar E. Ganteaume
 *
 */
public interface CcdpTaskLauncher
{

  /**
   * Sends an update to the ExecutorDriver with the status change provided
   * as an argument.  If the message is not null then is set using the 
   * setMessage() method in the TaskStatus.Builder object
   * 
   * @param task the task to send updates to the main application
   * @param message a message (optional) to be sent back to the ExecutorDriver
   */
  public void statusUpdate(CcdpTaskRequest task, String message);
  
}
