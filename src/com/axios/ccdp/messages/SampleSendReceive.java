package com.axios.ccdp.messages;

import javax.jms.Message;
import javax.jms.TextMessage;

import org.apache.log4j.Logger;

import com.axios.ccdp.impl.connections.amq.AmqConnector;
import com.axios.ccdp.messages.CcdpMessage.CcdpMessageType;
import com.axios.ccdp.tasking.CcdpTaskRequest;

public class SampleSendReceive extends AmqConnector
{

  /**
   * Generates debug print statements based on the verbosity level.
   */
  private Logger logger = Logger.getLogger(SampleSendReceive.class.getName());

  public SampleSendReceive()
  {

  }
  
  /**
   * Example of how to send a message using the CcdpMessage class
   */
  public void sendMessage( )
  {
    try
    {
      RunTaskMessage msg = new RunTaskMessage();
      CcdpTaskRequest task = new CcdpTaskRequest();
      msg.setTask(task);
      TextMessage txtMsg = this.session.createTextMessage();
      CcdpMessage.buildMessage(msg, txtMsg);
      
      // this.sender.sendMessage( msg );
      
    }
    catch( CcdpMessageException e)
    {
      this.logger.error("Message: " + e.getMessage(), e);
    }
    catch( Exception e)
    {
      this.logger.error("Message: " + e.getMessage(), e);
    }
  }
  
  /**
   * Example of how to receive a message using the CcdpMessage class
   * 
   * @param msg the message to process
   */
  public void onMessage( Message msg )
  {
    if( msg instanceof TextMessage )
    {
      TextMessage txtMsg = (TextMessage)msg;
      try
      {
        String text = txtMsg.getText();
        this.logger.debug("The Text " + text);
        int type = txtMsg.getIntProperty(CcdpMessage.MSG_TYPE_FLD);
        CcdpMessageType msgType = CcdpMessageType.get(type);
        switch( msgType )
        {
        case RUN_TASK:
          RunTaskMessage tm = CcdpMessage.buildObject(txtMsg, RunTaskMessage.class);
          CcdpTaskRequest task = tm.getTask();
          this.logger.debug("The Task: " + task.toString() );
          break;
          default:
            this.logger.error("Do not handle this type " + msgType.toString());
        }
      }
      catch( CcdpMessageException e)
      {
        this.logger.error("Message: " + e.getMessage(), e);
      }
      catch( Exception e)
      {
        this.logger.error("Message: " + e.getMessage(), e);
      }
    }
  }
}
