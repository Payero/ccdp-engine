package com.axios.ccdp.connections.amq;

import java.util.Iterator;
import java.util.Map;

import javax.jms.DeliveryMode;
import javax.jms.Destination;
import javax.jms.JMSException;
import javax.jms.MessageProducer;
import javax.jms.TextMessage;

import org.apache.activemq.Message;
import org.apache.log4j.Logger;
import com.axios.ccdp.message.CcdpMessage;

/**
 * Class used to send ActiveMQ messages.  It was designed to be part of the
 * AMQCcdpTaskingImpl class as part of the Tasking Interface implementation
 * 
 * @author Oscar E. Ganteaume
 *
 */
public class AmqSender extends AmqConnector
{

  /**
   * Generates debug print statements based on the verbosity level.
   */
  private Logger logger = Logger.getLogger(AmqSender.class.getName());
  /**
   * Object used to send messages
   */
  private MessageProducer producer = null;
  
  private int defDelivMode = DeliveryMode.PERSISTENT;       // 1 or 2, P = 2
  private int defPriority  = Message.DEFAULT_PRIORITY;      // 0 - 9, Def = 4
  private long defTTL      = Message.DEFAULT_TIME_TO_LIVE;  // Def 0
  
  /**
   * Default constructor, it does not do anything 
   */
  public AmqSender() 
  {
  }
  
  /**
   * Connects to the given channel to send events
   * 
   * @param broker the server or broker to connect
   * @param channel the channel used to send events
   */
  public boolean connect(String broker, String channel)
  {
    try
    {
      super.connect(broker, channel);
      this.producer = session.createProducer(destination);
      return true;
    }
    catch( JMSException e)
    {
      this.logger.error("send con Message: " + e.getMessage(), e);
      return false;
    }
  }
  
  /**
   * Disconnects from the current destination and closes all the connections
   */
  public void disconnect()
  {
    this.logger.info("Disconnecting");
    try
    {
      if( this.producer != null )
        this.producer.close();
      super.disconnect();
    }
    catch ( JMSException e)
    {
      this.logger.error("send discon Message: " + e.getMessage(), e);
    }
  }
  
  /**
   * Sends a message to the subscribe channel.  If the props argument is not 
   * null then is used to set the TextMessage properties or header.
   * 
   * @param props and optional set of properties to attach to the message
   * @param body the actual message or event to send
   */
  public void sendMessage(Map<String, String> props, CcdpMessage body) 
  {
    this.sendMessage(props,  body, this.defTTL);
  }

  /**
   * Sends a message to the subscribe channel.  If the props argument is not 
   * null then is used to set the TextMessage properties or header.
   * 
   * @param props and optional set of properties to attach to the message
   * @param body the actual message or event to send
   * @param ttl the message's time to live in milliseconds
   */
  public void sendMessage(Map<String, String> props, CcdpMessage body, long ttl) 
  {
    try 
    {
      TextMessage message = session.createTextMessage();
      
      if( props != null )
      {
        Iterator<String> keys = props.keySet().iterator();
        while( keys.hasNext() )
        {
          String key = keys.next();
          String val = props.get(key);
          this.logger.debug("Setting " + key + " = " + val);
          message.setStringProperty(key, val);
        }
      }
      this.logger.info("````````````````MESSAGE BODY IS: " + body.toString());
      CcdpMessage.buildMessage(body, message);
      producer.send(message, this.defDelivMode, this.defPriority, ttl); 
      
      this.logger.info("Sent: " + message.getText());
    } 
    catch (Exception e) 
    {
      this.logger.error("send sendMessage: 1 " + e.getMessage(), e);
    }
  }
  
  /**
   * Sends a message to the subscribe channel.  If the props argument is not 
   * null then is used to set the TextMessage properties or header.
   * 
   * @param dest the name of the topic or queue to send the message to
   * @param props and optional set of properties to attach to the message
   * @param body the actual message or event to send
   */
  public void sendMessage(String dest, Map<String, String> props, 
      CcdpMessage body) 
  {
    this.sendMessage(dest, props,  body, this.defTTL);
  }

  /**
   * Sends a message to the subscribe channel.  If the props argument is not 
   * null then is used to set the TextMessage properties or header.
   * 
   * @param destination the name of the topic or queue to send the message to
   * @param props and optional set of properties to attach to the message
   * @param body the actual message or event to send
   * @param ttl the message's time to live in milliseconds
   */
  public void sendMessage(String destination, Map<String, String> props, 
      CcdpMessage body, long ttl) 
  {
    try 
    {
      TextMessage message = session.createTextMessage();
      
      if( props != null )
      {
        Iterator<String> keys = props.keySet().iterator();
        while( keys.hasNext() )
        {
          String key = keys.next();
          String val = props.get(key);
          this.logger.debug("Setting " + key + " = " + val);
          message.setStringProperty(key, val);
        }
      }
      CcdpMessage.buildMessage(body, message);
      Destination dest = this.session.createQueue(destination);
      
      // ********************  IMPORTANTE NOTE!! IMPORTANTE NOTE!!  **********
      //
      //     seems to be a problem if ttl is set to a non-zero value
      //
      //********************  IMPORTANTE NOTE!! IMPORTANTE NOTE!!  **********
      producer.send(dest, message, this.defDelivMode, this.defPriority, ttl); 
      
      this.logger.info("Sent: " + message.getText());
  
    } 
    catch (Exception e) 
    {
      this.logger.error("send sendMessage: 2 " + e.getMessage(), e);
    }
  }

}
