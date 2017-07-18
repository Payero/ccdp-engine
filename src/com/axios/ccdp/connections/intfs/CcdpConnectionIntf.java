package com.axios.ccdp.connections.intfs;

import java.util.Map;

import com.axios.ccdp.message.CcdpMessage;
import com.axios.ccdp.resources.CcdpVMResource;
import com.axios.ccdp.tasking.CcdpTaskRequest;
import com.axios.ccdp.utils.CcdpUtils;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Interface used to define the protocol used to send and receive tasking 
 * information from and to the Mesos Scheduler
 * 
 * @author Oscar E. Ganteaume
 *
 */
public interface CcdpConnectionIntf
{
  /**
   * Sets the consumer that will be expecting events from external sources
   * 
   * @param consumer the object interested on receiving external events
   */
  public void setConsumer( CcdpMessageConsumerIntf consumer );
  
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
  public void registerConsumer(String uuid, String channel);
  
  /**
   * Registers a unique identifier with a specific channels.  
   * 
   * @param channel the channel to send messages
   */
  public void registerProducer(String channel);
  
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
   * Sends a message to the channel given as an argument.  The JSON object will 
   * conformed the body of the message as a String object.
   * 
   * @param channel the destination where to send the message
   * @param msg the actual message to send
   */
  public void sendCcdpMessage( String channel, CcdpMessage msg );
  
  /**
   * Sends a message to the channel given as an argument.  The JSON object will 
   * conformed the body of the message as a String object. It sets all the 
   * properties passed as argument in the message's header
   * 
   * @param channel the destination where to send the message
   * @param props a map containing all the properties to be attached to the
   *        message that is being sent
   * @param msg the actual message to send
   */
  public void sendCcdpMessage( String channel, Map<String, String> props, 
      CcdpMessage msg );

  /**
   * Sends a message to the channel given as an argument.  The JSON object will 
   * conformed the body of the message as a String object. It specifies how
   * long this particular message should remain in the server.
   * 
   * @param channel the destination where to send the message
   * @param msg the actual message to send
   * @param ttl the time to live of the message being setn
   */
  public void sendCcdpMessage( String channel, CcdpMessage msg, long ttl );
  
  /**
   * Sends a message to the channel given as an argument.  The JSON object will 
   * conformed the body of the message as a String object. It sets all the 
   * properties passed as argument in the message's header.  It specifies how
   * long this particular message should remain in the server.
   * 
   * @param channel the destination where to send the message
   * @param props a map containing all the properties to be attached to the
   *        message that is being sent
   * @param msg the actual message to send
   * @param ttl the time to live of the message being setn
   */
  public void sendCcdpMessage( String channel, Map<String, String> props, 
      CcdpMessage msg, long ttl );

  /**
   * Sends a hearbeat message to the channel specified provided as argument.
   * The heartbeat contains information about the current resource utilization 
   * as well as other information concerning the health of the node
   * 
   * @param channel the destination to send the hearbeats
   * @param resource an object with the node's health
   */
  public void sendHeartbeat( String channel, CcdpVMResource resource );
  
  /**
   * Sends a task update message to the channel provided as an argument.
   * The task update contains information about the state of a task assigned to 
   * this node
   * 
   * @param channel the destination to send the task updates
   * @param task an object with information about a specific task
   */
  public void sendTaskUpdate( String channel, CcdpTaskRequest task );
  
  /**
   * Unregisters the UUID from receiving incoming events from the given channel
   * 
   * @param uuid the unique identifier to remove from receiving events
   * @param channel the channel to unsubscribe
   */
  public void unregisterConsumer(String uuid, String channel);
  
  /**
   * Disconnects the object responsible for sending data to a particular 
   * channel.
   * 
   * @param channel the channel to unsubscribe
   */
  public void unregisterProducer(String channel);
  
  /**
   * Disconnects all the objects; senders and receivers.  This is normally 
   * called when an object is terminating execution
   * 
   */
  public void disconnect();
  
}
