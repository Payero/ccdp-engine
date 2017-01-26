package com.axios.ccdp.mesos.tasking;

import java.util.ArrayList;
import java.util.List;

/**
 *  {"port-id": "cycles_selector-1",
                     "to": [ "pi_estimator_input-1" ]
                    }
                    
 * @author Oscar E. Ganteaume
 *
 */
public class CcdpPort
{

  private String PortId;
  private List<String> FromPort = new ArrayList<String>();
  private List<String> ToPort = new ArrayList<String>();
  
  public CcdpPort()
  {

  }
  
  /**
   * @return the portId
   */
  public String getPortId()
  {
    return PortId;
  }

  /**
   * @param portId the portId to set
   */
  public void setPortId(String portId)
  {
    this.PortId = portId;
  }

  /**
   * @return the fromPort
   */
  public List<String> getFromPort()
  {
    return FromPort;
  }

  /**
   * @param fromPort the fromPort to set
   */
  public void setFromPort(List<String> fromPort)
  {
    this.FromPort = fromPort;
  }

  /**
   * @return the toPort
   */
  public List<String> getToPort()
  {
    return ToPort;
  }

  /**
   * @param toPort the toPort to set
   */
  public void setToPort(List<String> toPort)
  {
    this.ToPort = toPort;
  }
}
