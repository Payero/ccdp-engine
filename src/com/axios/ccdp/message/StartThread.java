package com.axios.ccdp.message;


public class StartThread extends CcdpMessage
{

  private CcdpMessageType msgType = CcdpMessageType.START_THREAD;
  
  private String threadId = null;
  
  public StartThread()
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
  
  @PropertyNameGet("thread-id")
  public String getThreadId()
  {
    return this.threadId;
  }

  
  @PropertyNameSet("thread-id")
  @PropertyNameRequired()
  public void setThreadId( String threadId )
  {
    this.threadId = threadId;
  }
  
}
