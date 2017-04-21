package com.axios.ccdp.connections.intfs;

import com.axios.ccdp.tasking.CcdpThreadRequest;

/**
 * Generic interface to allow Consumers to receive incoming Tasks
 * 
 * @author Oscar E. Ganteaume
 *
 */
public interface CcdpTaskConsumerIntf
{
  /**
   * Receives an asynchronous task from an external source
   * 
   * @param request the thread or processing task to be handled to the consumer
   */
  public void onTask(CcdpThreadRequest request);
}
