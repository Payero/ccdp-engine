package com.axios.ccdp.intfs;

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
   * Sends an update to the Main Application with the status change provided
   * as an argument.  
   * 
   * @param task the task to send updates to the main application
   */
  public void statusUpdate(CcdpTaskRequest task);

  /**
   * Notifies the launcher that an error has occurred while executing the given
   * task.  The error message is provided as a separate argument
   * 
   * @param task the task to send updates to the main application
   * @param message a message describing the error if a tasks fails to execute
   */
  public void onTaskError(CcdpTaskRequest task, String message);

  
}
