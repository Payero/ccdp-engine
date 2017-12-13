package com.axios.ccdp.messages;


public class ShutdownMessage extends CcdpMessage
{

  private CcdpMessageType msgType = CcdpMessageType.SHUTDOWN;
  
  String message = null;
  
  public ShutdownMessage()
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
  
  @PropertyNameGet("message")
  public String getMessage()
  {
    return this.message;
  }

  
  @PropertyNameSet("message")
  public void setMessage( String message )
  {
    this.message = message;
  }
  
}
