package com.axios.ccdp.messages;


import com.axios.ccdp.tasking.CcdpTaskRequest;

public class KillTaskMessage extends CcdpMessage
{

  private CcdpMessageType msgType = CcdpMessageType.KILL_TASK;
  
  private CcdpTaskRequest task = null;
  private int how_many = 0;
  
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

  @PropertyNameGet("how-many")
  public int getHowMany()
  {
    return how_many;
  }

  
  @PropertyNameSet("how-many")
  public void setHowMany(int how_many)
  {
    if( how_many >= 0 )
      this.how_many = how_many;
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
