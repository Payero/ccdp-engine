package com.axios.ccdp.messages;


public class EndSessionMessage extends CcdpMessage
{

  private CcdpMessageType msgType = CcdpMessageType.END_SESSION;
  
  private String sessionId = null;
  
  public EndSessionMessage()
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
