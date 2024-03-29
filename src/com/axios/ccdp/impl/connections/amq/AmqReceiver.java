package com.axios.ccdp.impl.connections.amq;


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

import com.axios.ccdp.intfs.CcdpMessageConsumerIntf;
import com.axios.ccdp.messages.AssignSessionMessage;
import com.axios.ccdp.messages.CcdpMessage;
import com.axios.ccdp.messages.ResourceUpdateMessage;
import com.axios.ccdp.messages.RunTaskMessage;
import com.axios.ccdp.messages.TaskUpdateMessage;
import com.axios.ccdp.messages.ThreadRequestMessage;
import com.axios.ccdp.messages.UndefinedMessage;
import com.axios.ccdp.messages.CcdpMessage.CcdpMessageType;
import com.axios.ccdp.tasking.CcdpTaskRequest;
import com.axios.ccdp.tasking.CcdpThreadRequest;
import com.axios.ccdp.utils.CcdpUtils;

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
   * Instantiates a new object and sets the consumer
   * @param consumer the object requesting the asynchronous events
   */
  public AmqReceiver(CcdpMessageConsumerIntf consumer)
  {
    this.consumer = consumer;
  }
  
  public void printMessage( TextMessage msg )
  {
    try
    {
      
      @SuppressWarnings("rawtypes")
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
   * Method invoked every time a new AMQ message is received.  It tries very
   * hard to determine what the message is.  It first test if is a TextMessage 
   * and if it is it attempts the get the message type from the header.   If
   * is not set, then it takes the payload and gets the message type from it.
   * If the message type is found it creates a CcdpMessage of the appropriate
   * type and passes it to the consumer.
   * 
   * If the message type was not found then it attempts to generate a 
   * CcdpThreadRequest from the payload.  If it was parsed successfully then is
   * created into a ThreadRequestMessage and passed to the consumer.
   * 
   * If none of the above is possible then it throws an error and ignores the
   * incoming message
   * 
   */
  public void onMessage( Message message )
  {
    try
    {
      // Did I get a TextMessage?
      if (message instanceof TextMessage) 
      {
        TextMessage txtMsg = (TextMessage)message;
        this.logger.debug("Payload: " + txtMsg.getText());
        
        // the actual message type is returned based on the msgType field
        CcdpMessage ccdpMsg = CcdpMessage.buildObject(txtMsg);
        
        // if it has an integer field called msg-type, then it might be a
        // CcdpMessage
        if( ccdpMsg != null )
        {
          String replyTo = null;
          if( txtMsg.getJMSReplyTo() != null )
            replyTo = txtMsg.getJMSReplyTo().toString();
          
          ccdpMsg.setReplyTo(replyTo);
          
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
        this.logger.warn("Expecting TextMessages only " + message.getClass().getName() );
      }
    }
    catch( Exception e )
    {
      this.logger.error("Message: " + e.getMessage());
      e.printStackTrace();
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
      if( this.receiver != null )
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
    try
    {
      CcdpUtils.configureProperties();
      CcdpUtils.configLogger();
      Driver driver = new Driver();
      CcdpUtils.pause(30);
      driver.stop();
    }
    catch( Exception e)
    {
      System.err.println("Message: " + e.getMessage());
      
    }
  }
}

class Driver implements CcdpMessageConsumerIntf
{
  Logger logger = Logger.getLogger(Driver.class.getName());
  private AmqReceiver rcvr = null;
  
  public Driver()
  {
    this.rcvr = new AmqReceiver(this);
    String broker = "failover://tcp://localhost:61616";
    String channel = "TaskingQueue";
    this.logger.debug("Broker " + broker);
    this.logger.debug("Channel " + channel);
    this.rcvr.connect(broker, channel);

  }
  
  public void onCcdpMessage(CcdpMessage message)
  {
    
    CcdpMessageType msgType = CcdpMessageType.get(message.getMessageType());
    this.logger.debug("Got a " + msgType + " Message");
    switch( msgType )
    {
      case UNDEFINED:
        UndefinedMessage undMsg = (UndefinedMessage)message;
        this.logger.info("Undefined Msg: " + undMsg.getPayload().toString());
        break;
      case RUN_TASK:
        RunTaskMessage taskMsg = (RunTaskMessage)message;
        this.logger.info("RunTask Msg: " + taskMsg.getTask().toPrettyPrint());
        break;
      case ASSIGN_SESSION:
        AssignSessionMessage sessMsg = (AssignSessionMessage)message;
        this.logger.info("Session Msg: " + sessMsg.getSessionId());
        break;
      case RESOURCE_UPDATE:
        ResourceUpdateMessage resMsg = (ResourceUpdateMessage)message;
        this.logger.info("Res Upd. Msg: " + resMsg.getCcdpVMResource().toPrettyPrint());
        break;
      case TASK_UPDATE:
        TaskUpdateMessage updMsg = (TaskUpdateMessage)message;
        this.logger.info("Task Upd Msg: " + updMsg.getTask().toPrettyPrint());
        break;
      case THREAD_REQUEST:
        ThreadRequestMessage reqMsg = (ThreadRequestMessage)message;
        this.logger.info("Request Msg: " + reqMsg.getRequest().toPrettyPrint());
        break;
      default:
        this.logger.error("Message Type not found");
    }
  }
  
  public void stop()
  {
    this.rcvr.disconnect();
  }
}
