package com.axios.ccdp.messages;


import com.axios.ccdp.tasking.CcdpThreadRequest;


public class ThreadRequestMessage extends CcdpMessage
{

  private CcdpMessageType msgType = CcdpMessageType.THREAD_REQUEST;
  
  private CcdpThreadRequest request = null;
  
  public ThreadRequestMessage()
  {

  }

  @PropertyNameGet("ccdp-request")
  public CcdpThreadRequest getRequest()
  {
    return request;
  }

  
  @PropertyNameSet("ccdp-request")
  @PropertyNameRequired()
  public void setRequest(CcdpThreadRequest request)
  {
    this.request = request;
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
