package com.axios.ccdp.mesos.connections.amq;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import javax.jms.JMSException;
import javax.jms.MessageProducer;
import javax.jms.TextMessage;

import org.apache.log4j.Logger;

import com.axios.ccdp.mesos.utils.CcdpUtils;

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
  
  /**
   * Default constructor, it does not do anything 
   */
  public AmqSender() 
  {
  }
  
  /**
   * Connects to the given channel to send events
   * 
   * @param channel the channel used to send events
   */
  public boolean connect(String channel)
  {
    try
    {
      super.connect(channel);
      this.producer = session.createProducer(destination);
      return true;
    }
    catch( JMSException e)
    {
      this.logger.error("Message: " + e.getMessage(), e);
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
      this.producer.close();
      super.disconnect();
    }
    catch ( JMSException e)
    {
      this.logger.error("Message: " + e.getMessage(), e);
    }
  }
  
  /**
   * Sends a message to the subscribe channel.  If the props argument is not 
   * null then is used to set the TextMessage properties or header.
   * 
   * @param props and optional set of properties to attach to the message
   * @param body the actual message or event to send
   */
  public void sendMessage(Map<String, String> props, String body) 
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
      
      message.setText(body);
      producer.send(message);
      
      this.logger.info("Sent: " + message.getText());
  
    } 
    catch (JMSException e) 
    {
      this.logger.error("Message: " + e.getMessage(), e);
    }
  }

  public static void main(String[] args) 
  {
    CcdpUtils.configLogger();
    
    AmqSender sender = new AmqSender();
    Map<String, String> map = new HashMap<String, String>();
    map.put("name", "Test");
    map.put("id", "1");
    
    sender.sendMessage(map, 
              "Hello ...This is a sample message..sending from FirstClient");
    sender.disconnect();
  }

}
