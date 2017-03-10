package com.axios.ccdp.mesos.connections.amq;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.jms.TextMessage;

import org.apache.log4j.Logger;

import com.axios.ccdp.mesos.connections.intfs.CcdpEventConsumerIntf;
import com.axios.ccdp.mesos.connections.intfs.CcdpTaskConsumerIntf;
import com.axios.ccdp.mesos.connections.intfs.CcdpTaskingIntf;
import com.axios.ccdp.mesos.tasking.CcdpThreadRequest;
import com.axios.ccdp.mesos.utils.CcdpUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * An actual implementation of the CcdpTaskingIntf that uses ActiveMQ as the 
 * mechanism to send and receive events or messages.
 * 
 * @author Oscar E. Ganteaume
 *
 */
public class AMQCcdpTaskingImpl 
                          implements CcdpTaskingIntf, CcdpEventConsumerIntf
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
   * The object interested on receiving the actual task
   */
  private CcdpTaskConsumerIntf consumer = null;
  
  /**
   * Stores the configuration for the tasking interface
   */
  private ObjectNode config = null;
  
  /**
   * Generates all the JSON objects 
   */
  private ObjectMapper mapper = new ObjectMapper();
  
  /**
   * Instantiates a new object.  The receiver is not instantiated until the
   * setEventConsumer is invoked.
   */
  public AMQCcdpTaskingImpl()
  {
    this.logger.debug("Creating new connections");
    this.sender = new AmqSender();
    this.receiver = new AmqReceiver(this);
  }

  /**
   * Sets the object interested on receiving events.
   * 
   * @param consumer the object interested on receiving events.
   */
  @Override
  public void setTaskConsumer( CcdpTaskConsumerIntf consumer )
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
  }
  
  /**
   * Registers a unique identifier with a specific channels.  
   * 
   * IMPORTANT NOTE: The registration will allow to start receiving external 
   * events and therefore the setEventConsumer() needs to be called first, 
   * failing to do so could have unexpected behavior.
   * 
   */
  @Override
  public void register()
  {
    this.register(this.config.get(CcdpUtils.CFG_KEY_TASKING_UUID).asText());
  }
  
  /**
   * Registers a unique identifier with a specific channels.  
   * 
   * IMPORTANT NOTE: The registration will allow to start receiving external 
   * events and therefore the setEventConsumer() needs to be called first, 
   * failing to do so could have unexpected behavior.
   * 
   * @param uuid the consumer unique identifier to use
   */
  @Override
  public void register(String uuid)
  {
    String channel = 
        this.config.get(CcdpUtils.CFG_KEY_TASKING_CHANNEL).asText();
    this.register(uuid, channel);
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
   * Sends an event to the destination specified in the given channel.  The
   * props Map is an optional set of properties to be set in the header and the 
   * body is the actual payload or event to send.
   * 
   * @param props a series of optional header information
   * @param body the actual body or event to send
   */
  @Override
  public void sendEvent(Map<String, String> props, Object body)
  {
    String channel = 
        this.config.get(CcdpUtils.CFG_KEY_RESPONSE_CHANNEL).asText();
    this.sendEvent(channel, props, body);
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
    this.logger.info("Sending Event to " + channel);
    String brkr = this.config.get(CcdpUtils.CFG_KEY_BROKER_CONNECTION).asText();
    this.sender.connect(brkr, channel);
    this.sender.sendMessage(props, body.toString());
  }

  /**
   * Unregisters the UUID from receiving incoming events from the given channel
   * 
   * @param uuid the unique identifier to remove from receiving events
   * @param channel the channel to unsubscribe
   */
  @Override
  public void unregister()
  {
    this.unregister(this.config.get(CcdpUtils.CFG_KEY_TASKING_UUID).asText());
  }
  
  /**
   * Unregisters the UUID from receiving incoming events from the given channel
   * 
   * @param uuid the unique identifier to remove from receiving events
   * @param channel the channel to unsubscribe
   */
  @Override
  public void unregister(String uuid)
  {
    String channel = 
        this.config.get(CcdpUtils.CFG_KEY_TASKING_CHANNEL).asText();
    this.unregister(uuid, channel);
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
  
  /**
   * Gets the events from the AMQ Receiver as a String.  The string contains the
   * JSON representation of the task or thread to execute.  This method 
   * translates the incoming event and transform it into a CcdpThreadRequest.
   * The request is then passed to the CcdpTaskingConsumerIntf object
   * 
   * @param event the JSON configuration of the tasking request
   */
  public void onEvent( Object event )
  {
    String msg = (String)event;
    this.logger.debug("Got a Task Request " + msg);
    
    try
    {
      List<CcdpThreadRequest> reqs = CcdpUtils.toCcdpThreadRequest(msg);
      if( reqs != null )
      {
        this.logger.debug("Got " + reqs.size() + " Thread requests");
        for( CcdpThreadRequest req : reqs )
        {
          this.logger.debug("Tasking Consumer: " + req.toString());
          this.consumer.onTask(req);
        }
      }
    }
    catch( Exception e )
    {
      this.logger.error("Message: " + e.getMessage(), e);
    }
  }
}
