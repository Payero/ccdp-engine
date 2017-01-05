package com.axios.ccdp.mesos.connections.intfs;

import java.util.Map;

/**
 * Interface used to define the protocol used to send and receive tasking 
 * information from and to the Mesos Scheduler
 * 
 * @author Oscar E. Ganteaume
 *
 */
public interface CcdpTaskingIntf
{
  /**
   * Sets the consumer that will be expecting events from external sources
   * 
   * @param consumer the object interested on receiving external events
   */
  public void setEventConsumer( CcdpEventConsumerIntf consumer );
  
  /**
   * Registers a unique identifier with a specific channels.  
   * 
   * IMPORTANT NOTE: The registration will allow to start receiving external 
   * events and therefore the setEventConsumer() needs to be called first, 
   * failing to do so could have unexpected behavior.
   * 
   * @param uuid the consumer unique identifier to use
   * @param channel the channel to subscribe or register
   */
  public void register(String uuid, String channel);
  
  /**
   * Sends an event to the destination specified in the given channel.  The
   * props Map is an optional set of properties to be set in the header and the 
   * body is the actual payload or event to send.
   * 
   * @param channel the destination channel to send the event
   * @param props a series of optional header information
   * @param body the actual body or event to send
   */
  public void sendEvent(String channel, Map<String, String> props, Object body);
  
  /**
   * Unregisters the UUID from receiving incoming events from the given channel
   * 
   * @param uuid the unique identifier to remove from receiving events
   * @param channel the channel to unsubscribe
   */
  public void unregister(String uuid, String channel);
}
