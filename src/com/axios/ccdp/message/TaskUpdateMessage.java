package com.axios.ccdp.message;


import com.axios.ccdp.tasking.CcdpTaskRequest;

public class TaskUpdateMessage extends CcdpMessage
{

  private CcdpMessageType msgType = CcdpMessageType.TASK_UPDATE;
  
  private CcdpTaskRequest task = null;
  
  public TaskUpdateMessage()
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
