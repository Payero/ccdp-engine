package com.axios.ccdp.connections.intfs;

import java.util.Map;

import com.axios.ccdp.message.CcdpMessage;
import com.fasterxml.jackson.databind.node.ObjectNode;

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
  public void setTaskConsumer( CcdpTaskConsumerIntf consumer );
  
  /**
   * Registers a unique identifier with a specific channels.  The configuration
   * information is specified in the properties file  
   * 
   * IMPORTANT NOTE: The registration will allow to start receiving external 
   * events and therefore the setEventConsumer() needs to be called first, 
   * failing to do so could have unexpected behavior.
   * 
   */
  public void register();
  
  /**
   * Registers a unique identifier with a specific channels.  
   * 
   * IMPORTANT NOTE: The registration will allow to start receiving external 
   * events and therefore the setEventConsumer() needs to be called first, 
   * failing to do so could have unexpected behavior.
   * 
   * @param uuid the consumer unique identifier to use
   */
  public void register(String uuid);
  
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
   * Configures the running environment and/or connections required to perform
   * the operations.  The JSON Object contains all the different fields 
   * necessary to operate.  These fields might change on each actual 
   * implementation
   * 
   * @param config a JSON Object containing all the necessary fields required 
   *        to operate
   */
  public void configure( ObjectNode config );
  
  /**
   * Sends an event to the destination specified in the given channel.  The
   * props Map is an optional set of properties to be set in the header and the 
   * body is the actual payload or event to send.
   * 
   * @param props a series of optional header information
   * @param msg the actual message to send
   */
  public void sendCcdpMessage(Map<String, String> props, CcdpMessage msg);
  
  /**
   * Sends an event to the destination specified in the given channel.  The
   * props Map is an optional set of properties to be set in the header and the 
   * body is the actual payload or event to send.
   * 
   * @param channel the destination channel to send the event
   * @param props a series of optional header information
   * @param msg the actual message to send
   */
  public void sendCcdpMessage(String channel, Map<String, String> props, 
                              CcdpMessage msg);

  /**
   * Unregisters the UUID from receiving incoming events from the given channel
   * 
   */
  public void unregister();

  
  /**
   * Unregisters the UUID from receiving incoming events from the given channel
   * 
   * @param uuid the unique identifier to remove from receiving events
   */
  public void unregister(String uuid);
  
  /**
   * Unregisters the UUID from receiving incoming events from the given channel
   * 
   * @param uuid the unique identifier to remove from receiving events
   * @param channel the channel to unsubscribe
   */
  public void unregister(String uuid, String channel);
}
