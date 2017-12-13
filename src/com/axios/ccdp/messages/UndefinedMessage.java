package com.axios.ccdp.messages;

import com.axios.ccdp.messages.CcdpMessage.CcdpMessageType;

public class UndefinedMessage extends CcdpMessage
{

  private CcdpMessageType msgType = CcdpMessageType.UNDEFINED;
  private Object payload = null;

  public UndefinedMessage()
  {
    
  }
 
  
  @PropertyNameGet("payload")
  public Object getPayload()
  {
    return this.payload;
  }

  
  @PropertyNameSet("payload")
  @PropertyNameRequired()
  public void setPayload( Object payload )
  {
    this.payload = payload;
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
  
}
