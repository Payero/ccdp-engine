package com.axios.ccdp.mesos.connections.intfs;

/**
 * Generic interface to allow Consumers to receive incoming Events
 * 
 * @author Oscar E. Ganteaume
 *
 */
public interface CcdpEventConsumerIntf
{
  /**
   * Receives an asynchronous event from an external source
   * 
   * @param event the event to be handled to the consumer
   */
  public void onEvent(Object event);
}
