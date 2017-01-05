package com.axios.ccdp.mesos.connections.amq;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import org.apache.log4j.Logger;

import com.axios.ccdp.mesos.connections.intfs.CcdpEventConsumerIntf;
import com.axios.ccdp.mesos.connections.intfs.CcdpTaskingIntf;

/**
 * An actual implementation of the CcdpTaskingIntf that uses ActiveMQ as the 
 * mechanism to send and receive events or messages.
 * 
 * @author Oscar E. Ganteaume
 *
 */
public class AMQCcdpTaskingImpl implements CcdpTaskingIntf
{

  /**
   * Generates debug print statements based on the verbosity level.
   */
  private Logger logger = Logger.getLogger(AMQCcdpTaskingImpl.class.getName());
  /**
   * Sends messages to particular channels or destinations
   */
  private AmqSender sender = null;
  /**
   * Receives messages sent to the CcdpEventConsumer and notifies it every time
   * an event is received
   */
  private AmqReceiver receiver = null;
  /**
   * Stores all the registrations using the channel as the key and a list of
   * UUID as the value
   */
  private HashMap<String, ArrayList<String>> 
                      registrations = new HashMap<String, ArrayList<String>>();
  
  /**
   * Instantiates a new object.  The receiver is not instantiated until the
   * setEventConsumer is invoked.
   */
  public AMQCcdpTaskingImpl()
  {
    this.logger.debug("Creating new connections");
    this.sender = new AmqSender();
  }

  /**
   * Sets the object interested on receiving events.
   * 
   * @param consumer the object interested on receiving events.
   */
  @Override
  public void setEventConsumer( CcdpEventConsumerIntf consumer )
  {
    this.receiver = new AmqReceiver(consumer);
  }
  
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
  @Override
  public void register(String uuid, String channel)
  {
    this.logger.info("Registering " + uuid + " with channel " + channel);
    if( this.receiver == null )
    {
      this.logger.error("Have not registered a CcdpEventConsumer!!");
      return;
    }
    
    this.receiver.connect(channel);
    
    // do we have it already?
    if( this.registrations.containsKey(channel) )
    {
      this.logger.debug("Adding a new UUID to an existing channel");
      ArrayList<String> registered = this.registrations.get(channel);
      if( registered.contains(uuid) )
        this.logger.warn("UUID " + uuid + " is already registerd, skipping it");
      else
        registered.add(uuid);
      this.registrations.put(channel,  registered);
    }
    else
    {
      this.logger.debug("Creating a new channel");
      ArrayList<String> registered = new ArrayList<String>();
      registered.add(uuid);
      this.registrations.put(channel, registered);
    }
  }

  /**
   * Sends an event to the destination specified in the given channel.  The
   * props Map is an optional set of properties to be set in the header and the 
   * body is the actual payload or event to send.
   * 
   * @param channel the destination channel to send the event
   * @param props a series of optional header information
   * @param body the actual body or event to send
   */
  @Override
  public void sendEvent(String channel, Map<String, String> props, Object body)
  {
    this.logger.info("Sending Event");
    this.sender.connect(channel);
    this.sender.sendMessage(props, body.toString());
  }

  /**
   * Unregisters the UUID from receiving incoming events from the given channel
   * 
   * @param uuid the unique identifier to remove from receiving events
   * @param channel the channel to unsubscribe
   */
  @Override
  public void unregister(String uuid, String channel)
  {
    this.logger.info("Disconnecting " + uuid + " from channel " + channel);
    ArrayList<String> registered = this.registrations.get(channel);
    if( registered == null )
    {
      this.logger.error("Could not find registration for channel " + channel);
    }
    else
    {
      this.logger.debug("Registered Channels: " + registered.toString());
      
      if( registered.contains( uuid ) )
      {
        this.logger.debug("Found UUID");
        registered.remove(uuid);
        if( registered.isEmpty())
        {
          this.logger.info("No more UUIDs for channel " + channel);
          this.registrations.remove(channel);
          this.receiver.disconnect();
        }
      }
      else
      {
        this.logger.warn("Could not find UUID: " + uuid);
      }
    }
    if( this.sender != null )
      this.sender.disconnect();
  }
}
