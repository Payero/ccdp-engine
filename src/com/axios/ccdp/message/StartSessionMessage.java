package com.axios.ccdp.message;

import com.axios.ccdp.message.CcdpMessage.CcdpMessageType;

public class StartSessionMessage extends CcdpMessage
{

  private CcdpMessageType msgType = CcdpMessageType.START_SESSION;
  
  private String sessionId = null;
  
  public StartSessionMessage()
  {

  }

  @Override
  public Integer getMessageType()
  {
    return this.msgType.getValue();
  }
  
  @Override
  public void setMessageType( int type )
  {
    if( type != this.msgType.getValue() )
      return;
    
    this.msgType = CcdpMessageType.get(type);
  }
  
  @PropertyNameGet("session-id")
  public String getSessionId()
  {
    return this.sessionId;
  }

  
  @PropertyNameSet("session-id")
  @PropertyNameRequired()
  public void setSessionId( String sessionId )
  {
    this.sessionId = sessionId;
  }
  
}
