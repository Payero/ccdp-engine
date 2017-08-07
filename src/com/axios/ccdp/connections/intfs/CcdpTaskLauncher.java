package com.axios.ccdp.connections.intfs;

import com.axios.ccdp.tasking.CcdpTaskRequest;

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
