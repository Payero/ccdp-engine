package com.axios.ccdp.connections.intfs;

import com.axios.ccdp.message.CcdpMessage;

/**
 * Generic interface to allow Consumers to receive incoming Events
 * 
 * @author Oscar E. Ganteaume
 *
 */
public interface CcdpMessageConsumerIntf
{
  /**
   * Receives an asynchronous message from an external source
   * 
   * @param message the CcdpMessage containing the desired information
   */
  public void onCcdpMessage(CcdpMessage message);
}
