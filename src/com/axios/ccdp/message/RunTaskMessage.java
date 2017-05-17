package com.axios.ccdp.message;


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
}
