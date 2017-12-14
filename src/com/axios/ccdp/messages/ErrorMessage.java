package com.axios.ccdp.messages;


public class ErrorMessage extends CcdpMessage
{

  private CcdpMessageType msgType = CcdpMessageType.ERROR_MSG;
  
  String message = null;
  
  public ErrorMessage()
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
  
  @PropertyNameGet("error-message")
  public String getErrorMessage()
  {
    return this.message;
  }

  
  @PropertyNameSet("error-message")
  @PropertyNameRequired()
  public void setErrorMessage( String message )
  {
    this.message = message;
  }
  
}
