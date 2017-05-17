package com.axios.ccdp.message;


public class AssignSessionMessage extends CcdpMessage
{

  private CcdpMessageType msgType = CcdpMessageType.ASSIGN_SESSION;
  
  private String sessionId = null;
  
  public AssignSessionMessage()
  {

  }

  @Override
  public Integer getMessageType()
  {
    return this.msgType.getValue();
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
