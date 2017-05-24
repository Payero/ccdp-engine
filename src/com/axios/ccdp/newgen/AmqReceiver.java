package com.axios.ccdp.newgen;


import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageListener;
import javax.jms.TextMessage;

import org.apache.log4j.Logger;

import com.axios.ccdp.connections.intfs.CcdpMessageConsumerIntf;
import com.axios.ccdp.message.AssignSessionMessage;
import com.axios.ccdp.message.CcdpMessage;
import com.axios.ccdp.message.ResourceUpdateMessage;
import com.axios.ccdp.message.RunTaskMessage;
import com.axios.ccdp.message.TaskUpdateMessage;
import com.axios.ccdp.message.ThreadRequestMessage;
import com.axios.ccdp.message.UndefinedMessage;
import com.axios.ccdp.message.CcdpMessage.CcdpMessageType;
import com.axios.ccdp.tasking.CcdpTaskRequest;
import com.axios.ccdp.tasking.CcdpThreadRequest;
import com.axios.ccdp.utils.CcdpUtils;
import com.fasterxml.jackson.databind.JsonNode;
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
  private CcdpMessageConsumerIntf consumer = null;

  /**
   * Generates all the JSON objects
   */
  private ObjectMapper mapper = new ObjectMapper();
  
  /**
   * Instantiates a new object and sets the consumer
   * @param consumer the object requesting the asynchronous events
   */
  public AmqReceiver(CcdpMessageConsumerIntf consumer)
  {
    this.consumer = consumer;
  }

//  /**
//   * Method invoked every time a new AMQ message is received
//   */
//  public void onMessage( Message message )
//  {
//    try
//    {
//      if (message instanceof TextMessage) 
//      {
//        ObjectNode node = this.mapper.createObjectNode();
//        ObjectNode cfg = this.mapper.createObjectNode();
//        @SuppressWarnings("unchecked")
//        Enumeration<String> keys = message.getPropertyNames();
//        while( keys.hasMoreElements() )
//        {
//          String key = keys.nextElement(); 
//          cfg.put(key, message.getStringProperty(key));
//        }
//        
//        TextMessage text = (TextMessage) message;
//        String msg = text.getText();
//        node.set("config", cfg);
//        JsonNode body = this.mapper.readTree(msg);
//        
//        node.set("body", body);
//        this.logger.debug("Message is : " + node.toString());
//        this.consumer.onEvent(node);
//      }
//      else
//      {
//        this.logger.warn("Expecting TextMessages only");
//      }
//    }
//    catch( Exception e )
//    {
//      this.logger.error("Message: " + e.getMessage(), e);
//    }
//  }
  
  private void printMessage( TextMessage msg )
  {
    try
    {
      Enumeration keys = msg.getPropertyNames();
      while( keys.hasMoreElements() )
      {
        String key = (String)keys.nextElement();
        Object obj = msg.getObjectProperty(key);
        System.err.println("Message[" + key + "] = " + obj.toString() );
        if( obj instanceof String )
          System.err.println("Is a String");
        else if( obj instanceof Integer )
          System.err.println("Is an Integer");
        else if( obj instanceof Long )
          System.err.println("Is a Long");
        else if( obj instanceof Boolean )
          System.err.println("Is a Boolean");
        else if( obj instanceof Double )
          System.err.println("Is a Double");
        else if( obj instanceof Byte )
          System.err.println("Is a Byte");
        else if( obj instanceof Float )
          System.err.println("Is a Float");
        else if( obj instanceof Short )
          System.err.println("Is a Short");
      }
      
      System.err.println("The Body = " + msg.getText());
    }
    catch (JMSException e)
    {
      e.printStackTrace();
    }
    
  }
  
  /**
   * Method invoked every time a new AMQ message is received
   */
  public void onMessage( Message message )
  {
    try
    {
      // Did I get a TextMessage?
      if (message instanceof TextMessage) 
      {
        TextMessage txtMsg = (TextMessage)message;
        this.printMessage(txtMsg);
        this.logger.debug("Incoming message " + txtMsg.getText());
        
        if( true )
          return;
        
        // if it has an integer field called msg-type, then it might be a
        // CcdpMessage
        if( txtMsg.propertyExists(CcdpMessage.MSG_TYPE_FLD) )
        {
          int msgTypeNum = txtMsg.getIntProperty(CcdpMessage.MSG_TYPE_FLD);
          this.logger.debug("Got a message type " + msgTypeNum);
          CcdpMessageType msgType = CcdpMessageType.get(msgTypeNum);
          this.logger.debug("The Message Type is " + msgType);
          CcdpMessage ccdpMsg = null;
          switch( msgType )
          {
            case UNDEFINED:
              ccdpMsg = 
                  CcdpMessage.buildObject(txtMsg, UndefinedMessage.class);
              break;
            case RUN_TASK:
              ccdpMsg = 
                  CcdpMessage.buildObject(txtMsg, RunTaskMessage.class);
              break;
            case ASSIGN_SESSION:
              ccdpMsg = 
                  CcdpMessage.buildObject(txtMsg, AssignSessionMessage.class);
              break;
            case RESOURCE_UPDATE:
              ccdpMsg = 
                  CcdpMessage.buildObject(txtMsg, ResourceUpdateMessage.class);
              break;
            case TASK_UPDATE:
              ccdpMsg = 
                  CcdpMessage.buildObject(txtMsg, TaskUpdateMessage.class);
              break;
            case THREAD_REQUEST:
              ccdpMsg = 
                  CcdpMessage.buildObject(txtMsg, ThreadRequestMessage.class);
              break;
            default:
              this.logger.error("Message Type not found");
          }
          // passing the message to the consumer
          this.consumer.onCcdpMessage(ccdpMsg);
        }
        else  // is not a CCDP Message, maybe a request?
        {
          this.logger.trace("The message was not a CcdpMessage, request?");
          Map<String, String> cfg = new HashMap<>();
          
          // let's get all the configuration
          @SuppressWarnings("unchecked")
          Enumeration<String> keys = message.getPropertyNames();
          while( keys.hasMoreElements() )
          {
            String key = keys.nextElement(); 
            cfg.put(key, message.getStringProperty(key));
          }
          
          String msg = txtMsg.getText();
          this.logger.trace("Message is : " + msg);
          // let's try to make a request out of this
          List<CcdpThreadRequest> reqs = CcdpUtils.toCcdpThreadRequest(msg);
          if( reqs != null )
          {
            this.logger.debug("Got " + reqs.size() + " Thread requests");
            for( CcdpThreadRequest req : reqs )
            {
              if( cfg != null )
              {
                for( CcdpTaskRequest task : req.getTasks() )
                {
                  if( task.getConfiguration().isEmpty() )
                  {
                    this.logger.debug("Setting the Configuration to " + cfg.toString());
                    task.setConfiguration(cfg);
                  }
                }
              }
              // now we can create a CcdpMessage and pass it along
              ThreadRequestMessage reqMsg = new ThreadRequestMessage();
              reqMsg.setRequest(req);
              this.consumer.onCcdpMessage(reqMsg);
            }
          }
          else
          {
            this.logger.debug("Can't find what this is using undefined");
            UndefinedMessage undMsg = new UndefinedMessage();
            undMsg.setPayload(txtMsg.getText());
          }
        }
      }
      else
      {
        this.logger.warn("Expecting TextMessages only");
      }
    }
    catch( Exception e )
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

class Driver implements CcdpMessageConsumerIntf
{
  Logger logger = Logger.getLogger(Driver.class.getName());
  private AmqReceiver rcvr = null;
  
  public Driver()
  {
    this.rcvr = new AmqReceiver(this);

  }
  
  public void onCcdpMessage(CcdpMessage message)
  {
    this.logger.debug("Got a new Message: " + message.toString());
  }
  
  public void stop()
  {
    this.rcvr.disconnect();
  }
}
