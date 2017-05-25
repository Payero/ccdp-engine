package com.axios.ccdp.message;


import com.axios.ccdp.tasking.CcdpTaskRequest;

public class KillTaskMessage extends CcdpMessage
{

  private CcdpMessageType msgType = CcdpMessageType.KILL_TASK;
  
  private CcdpTaskRequest task = null;
  
  public KillTaskMessage()
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
}