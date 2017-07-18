package com.axios.ccdp.connections.amq;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;


import org.apache.log4j.Logger;

import com.axios.ccdp.connections.intfs.CcdpConnectionIntf;
import com.axios.ccdp.connections.intfs.CcdpMessageConsumerIntf;
import com.axios.ccdp.message.CcdpMessage;
import com.axios.ccdp.message.ResourceUpdateMessage;
import com.axios.ccdp.message.TaskUpdateMessage;
import com.axios.ccdp.resources.CcdpVMResource;
import com.axios.ccdp.tasking.CcdpTaskRequest;
import com.axios.ccdp.utils.CcdpUtils;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * An actual implementation of the CcdpTaskingIntf that uses ActiveMQ as the 
 * mechanism to send and receive events or messages.
 * 
 * @author Oscar E. Ganteaume
 *
 */
public class AmqCcdpConnectionImpl 
                         implements CcdpConnectionIntf, CcdpMessageConsumerIntf
{
  /**
   * Generates debug print statements based on the verbosity level.
   */
  private Logger logger = 
      Logger.getLogger(AmqCcdpConnectionImpl.class.getName());
  /**
   * Sends messages to particular channels or destinations
   */
  private Map<String, AmqSender> senders = new HashMap<>();
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
   * The object interested on receiving the actual task
   */
  private CcdpMessageConsumerIntf consumer = null;
  /**
   * Stores the configuration for the tasking interface
   */
  private ObjectNode config = null;
  /**
   * Stores the time to live for the heartbeats
   */
  private long hbTTLMills;
  
  /**
   * Instantiates a new object.  The receiver is not instantiated until the
   * setEventConsumer is invoked.
   */
  public AmqCcdpConnectionImpl()
  {
    this.logger.debug("Creating new connections");
    this.receiver = new AmqReceiver(this);
    
    this.hbTTLMills = 3000;
    try
    {
      this.hbTTLMills = 
          CcdpUtils.getIntegerProperty(CcdpUtils.CFG_KEY_HB_FREQ) * 1000;
    }
    catch( Exception e )
    {
      this.logger.warn("The heartbeat frequency was not set using 3 seconds");
    }
  }

  /**
   * Sets the object interested on receiving events.
   * 
   * @param consumer the object interested on receiving events.
   */
  @Override
  public void setConsumer( CcdpMessageConsumerIntf consumer )
  {
    this.consumer = consumer;
  }
  
  /**
   * Configures the running environment and/or connections required to perform
   * the operations.  The JSON Object contains all the different fields 
   * necessary to operate.  These fields might change on each actual 
   * implementation
   * 
   * @param config a JSON Object containing all the necessary fields required 
   *        to operate
   */
  @Override
  public void configure( ObjectNode config )
  {
    this.config = config;
    this.logger.debug("Using Configuration " + this.config.toString());
    if( this.config.has(CcdpUtils.CFG_KEY_MAIN_CHANNEL) )
    {
      String channel = this.config.get(CcdpUtils.CFG_KEY_MAIN_CHANNEL).asText();
      this.registerProducer(channel);
    }
  }
  
  /**
   * Registers a message producer to send messages to a particular channel  
   * 
   * @param channel the channel to send messages
   */
  @Override
  public void registerProducer(String channel)
  {
    synchronized( this.senders )
    {
      if( !this.senders.containsKey(channel) )
      {
        this.logger.info("Registering Producer: " + channel);
        String brkr = 
            this.config.get(CcdpUtils.CFG_KEY_BROKER_CONNECTION).asText();
        AmqSender sender = new AmqSender();
        sender.connect(brkr, channel);
        
        this.senders.put(channel, sender);
        
      }
      else
      {
        this.logger.debug( channel + " already has a Sender" );
      }
    }
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
  public void registerConsumer(String uuid, String channel)
  {
    
    this.logger.info("Registering " + uuid + " with channel " + channel);
    if( this.receiver == null )
    {
      this.logger.error("Have not registered a CcdpEventConsumer!!");
      return;
    }
    String brkr = this.config.get(CcdpUtils.CFG_KEY_BROKER_CONNECTION).asText();
    this.receiver.connect(brkr, channel);
    
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
   * Sends a message to the channel given as an argument.  The JSON object will 
   * conformed the body of the message as a String object.
   * 
   * @param channel the destination where to send the message
   * @param msg the actual message to send
   */
  @Override
  public void sendCcdpMessage( String channel, CcdpMessage msg)
  {
    this.sendCcdpMessage(channel, new HashMap<String, String>(), msg );
  }
  
  /**
   * Sends a message to the channel given as an argument.  The JSON object will 
   * conformed the body of the message as a String object. It specifies how
   * long this particular message should remain in the server.
   * 
   * @param channel the destination where to send the message
   * @param msg the actual message to send
   * @param ttl the time to live of the message being set
   */
  public void sendCcdpMessage( String channel, CcdpMessage msg, long ttl )
  {
    this.sendCcdpMessage(channel, new HashMap<String, String>(), msg, ttl );
  }
  
  /**
   * Sends an event to the destination specified in the given channel.  The
   * props Map is an optional set of properties to be set in the header and the 
   * body is the actual payload or event to send.
   * 
   * @param channel the destination where to send the message
   * @param props a series of optional header information
   * @param msg the actual message to send
   */
  @Override
  public void sendCcdpMessage(String channel, Map<String, String> props, 
      CcdpMessage msg)
  {
    this.sendCcdpMessage(channel, props, msg, 0);
  }

  /**
   * Sends an event to the destination specified in the given channel.  The
   * props Map is an optional set of properties to be set in the header and the 
   * body is the actual payload or event to send.
   * 
   * @param props a series of optional header information
   * @param msg the actual message to send
   */
  @Override
  public void sendCcdpMessage(String channel, Map<String, String> props, 
      CcdpMessage msg, long ttl)
  {
    synchronized( this.senders )
    {
      if( this.senders.containsKey(channel) )
      {
        AmqSender sender = this.senders.get(channel);
        sender.sendMessage(channel, props,  msg, ttl);
      }
      else
      {
        this.logger.error("Could not find a registered sender for " + channel );
      }
    }
  }
  
  /**
   * Unregisters the UUID from receiving incoming events from the given channel
   * 
   * @param uuid the unique identifier to remove from receiving events
   * @param channel the channel to unsubscribe
   */
  @Override
  public void unregisterConsumer(String uuid, String channel)
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
          if( this.receiver != null )
            this.receiver.disconnect();
        }
      }
      else
      {
        this.logger.warn("Could not find UUID: " + uuid);
      }
    }
  }
  
  /**
   * Gets the events from the AMQ Receiver as a String.  The string contains the
   * JSON representation of the task or thread to execute.  This method 
   * translates the incoming event and transform it into a CcdpThreadRequest.
   * The request is then passed to the CcdpTaskingConsumerIntf object
   * 
   * @param message the message that needs to be passed to the consumer
   */
  public void onCcdpMessage( CcdpMessage message )
  {
    this.consumer.onCcdpMessage(message);
  }

  
  /**
   * Sends a hearbeat message to the channel specified provided as argument.
   * The heartbeat contains information about the current resource utilization 
   * as well as other information concerning the health of the node
   * 
   * @param channel the destination to send the hearbeats
   * @param resource an object with the node's health
   */
  @Override
  public void sendHeartbeat( String channel, CcdpVMResource resource )
  {
    ResourceUpdateMessage msg = new ResourceUpdateMessage();
    msg.setCcdpVMResource(resource);
    this.sendCcdpMessage(channel, msg, this.hbTTLMills );
  }
  
  /**
   * Sends a task update message to the channel provided as an argument.
   * The task update contains information about the state of a task assigned to 
   * this node
   * 
   * @param channel the destination to send the task updates
   * @param task an object with information about a specific task
   */
  @Override
  public void sendTaskUpdate( String channel, CcdpTaskRequest task )
  {
    TaskUpdateMessage msg = new TaskUpdateMessage();
    msg.setTask(task);
    this.logger.debug("Sending a task update to channel: " + channel + " with message: " + msg);
    this.sendCcdpMessage(channel, msg);
    
  }
}
