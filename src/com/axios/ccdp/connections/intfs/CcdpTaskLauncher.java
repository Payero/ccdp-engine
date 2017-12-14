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
   * as an argument.  If there was an error executing the task then a message
   * is provided back to the caller.
   * 
   * @param task the task to send updates to the main application
   * @param message a message describing the error if a tasks fails to execute
   */
  public void statusUpdate(CcdpTaskRequest task, String message);
  
}
