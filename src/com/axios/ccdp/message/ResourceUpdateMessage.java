package com.axios.ccdp.message;


import com.axios.ccdp.message.CcdpMessage.CcdpMessageType;
import com.axios.ccdp.resources.CcdpVMResource;

public class ResourceUpdateMessage extends CcdpMessage
{

  private CcdpMessageType msgType = CcdpMessageType.RESOURCE_UPDATE;
  
  private CcdpVMResource resource = null;
  
  public ResourceUpdateMessage()
  {

  }

  @PropertyNameGet("ccdp-resource")
  public CcdpVMResource getCcdpVMResource()
  {
    return this.resource;
  }

  
  @PropertyNameSet("ccdp-resource")
  @PropertyNameRequired()
  public void setCcdpVMResource(CcdpVMResource resource)
  {
    this.resource = resource;
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
