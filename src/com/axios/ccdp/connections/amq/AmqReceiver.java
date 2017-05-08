package com.axios.ccdp.connections.amq;


import java.util.Enumeration;

import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageListener;
import javax.jms.TextMessage;

import org.apache.log4j.Logger;

import com.axios.ccdp.connections.intfs.CcdpEventConsumerIntf;
import com.axios.ccdp.utils.CcdpUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Class used to receive ActiveMQ messages.  It was designed to be part of the
 * AMQCcdpTaskingImpl class as part of the Tasking Interface implementation
 * 
 * @author Oscar E. Ganteaume
 *
 */
public class AmqReceiver extends AmqConnector implements MessageListener
{
  /**
   * Receives messages from the AMQ server
   */
  private MessageConsumer receiver = null;
  /**
   * Stores the object requesting the asynchronous events
   */
  private CcdpEventConsumerIntf consumer = null;

  /**
   * Generates all the JSON objects
   */
  private ObjectMapper mapper = new ObjectMapper();
  
  /**
   * Instantiates a new object and sets the consumer
   * @param consumer the object requesting the asynchronous events
   */
  public AmqReceiver(CcdpEventConsumerIntf consumer)
  {
    this.consumer = consumer;
  }

  /**
   * Method invoked every time a new AMQ message is received
   */
  public void onMessage( Message message )
  {
    try
    {
      if (message instanceof TextMessage) 
      {
        ObjectNode node = this.mapper.createObjectNode();
        ObjectNode cfg = this.mapper.createObjectNode();
        @SuppressWarnings("unchecked")
        Enumeration<String> keys = message.getPropertyNames();
        while( keys.hasMoreElements() )
        {
          String key = keys.nextElement(); 
          cfg.put(key, message.getStringProperty(key));
        }
        
        TextMessage text = (TextMessage) message;
        String msg = text.getText();
        node.set("config", cfg);
        node.put("body", msg);
        this.logger.trace("Message is : " + msg);
        this.consumer.onEvent(node);
      }
      else
      {
        this.logger.warn("Expecting TextMessages only");
      }
    }
    catch( JMSException e )
    {
      this.logger.error("Message: " + e.getMessage(), e);
    }
  }
  
  /**
   * Connects to the given channel to start receiving events
   * 
   * @param broker the server or broker to connect
   * @param channel the channel to subscribe and start receiving events
   */
  public boolean connect(String broker, String channel)
  {
    try
    {
      super.connect(broker, channel);
      receiver = session.createConsumer(destination);
      this.receiver.setMessageListener(this);
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
      this.receiver.close();
      super.disconnect();
    }
    catch ( JMSException e)
    {
      this.logger.error("Message: " + e.getMessage(), e);
    }
  }
  
  public static void main(String[] args) 
  {
    CcdpUtils.configLogger();
    Driver driver = new Driver();
    
    CcdpUtils.pause(3);
    driver.stop();
  }
}

class Driver implements CcdpEventConsumerIntf
{
  Logger logger = Logger.getLogger(Driver.class.getName());
  private AmqReceiver rcvr = null;
  
  public Driver()
  {
    this.rcvr = new AmqReceiver(this);

  }
  
  public void onEvent(Object request)
  {
    this.logger.debug("Got a new Request: " + request.toString());
  }
  
  public void stop()
  {
    this.rcvr.disconnect();
  }
}