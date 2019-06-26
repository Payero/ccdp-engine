package com.axios.ccdp.messages;


import com.axios.ccdp.tasking.CcdpTaskRequest;

public class RunTaskMessage extends CcdpMessage
{

  private CcdpMessageType msgType = CcdpMessageType.RUN_TASK;
  
  private CcdpTaskRequest task = null;
  
  public RunTaskMessage()
  {

  }

  @PropertyNameGet("ccdp-task")
  public CcdpTaskRequest getTask()
  {
    return task;
  }

  
  @PropertyNameSet("ccdp-task")
  @PropertyNameRequired()
  public void setTask(CcdpTaskRequest task)
  {
    this.task = task;
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
