package com.axios.ccdp.message;


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
}
