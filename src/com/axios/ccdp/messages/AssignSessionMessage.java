package com.axios.ccdp.messages;


public class AssignSessionMessage extends CcdpMessage
{

  private CcdpMessageType msgType = CcdpMessageType.ASSIGN_SESSION;
  
  private String sessionId = null;
  private String assignCmd = null;
  
  public AssignSessionMessage()
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
  
  @PropertyNameGet("assign-command")
  public String getAssignCommand()
  {
    return this.assignCmd;
  }

  
  @PropertyNameSet("assign-command")
  public void setAssignCommand( String assignCmd )
  {
    this.assignCmd = assignCmd;
  }
}
