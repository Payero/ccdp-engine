package com.axios.ccdp.messages;

import com.axios.ccdp.utils.CcdpUtils;
import com.fasterxml.jackson.annotation.JsonGetter;
import com.fasterxml.jackson.annotation.JsonSetter;

public class StartSessionMessage extends CcdpMessage
{

  private CcdpMessageType msgType = CcdpMessageType.START_SESSION;
  
  private String sessionId = null;
  private String nodeType = CcdpUtils.DEFAULT_RES_NAME;
  
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
  
  @JsonGetter("node-type")
  public String getNodeType()
  {
    return this.nodeType;
  }
  
  @JsonSetter("node-type")
  public void setNodeType( String node)
  {
    this.nodeType = node;
  }
}
