package com.axios.ccdp.messages;

import com.axios.ccdp.messages.CcdpMessage.CcdpMessageType;
import com.axios.ccdp.utils.CcdpUtils.CcdpNodeType;
import com.fasterxml.jackson.annotation.JsonGetter;
import com.fasterxml.jackson.annotation.JsonSetter;

public class StartSessionMessage extends CcdpMessage
{

  private CcdpMessageType msgType = CcdpMessageType.START_SESSION;
  
  private String sessionId = null;
  private CcdpNodeType nodeType = CcdpNodeType.EC2;
  
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
  public String getNodeTypeAsString()
  {
    return this.nodeType.toString();
  }
  
  @JsonSetter("node-type")
  public void setNodeType( String node)
  {
    this.nodeType = CcdpNodeType.valueOf(node);
  }
  
  public CcdpNodeType getNodeType()
  {
    return this.nodeType;
  }
  
  public void setNodeType( CcdpNodeType node )
  {
    this.nodeType = node;
  }
}
