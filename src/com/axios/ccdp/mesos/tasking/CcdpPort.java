package com.axios.ccdp.mesos.tasking;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonGetter;
import com.fasterxml.jackson.annotation.JsonSetter;

/**
 *  {"port-id": "cycles_selector-1",
                     "to": [ "pi_estimator_input-1" ]
                    }
                    
 * @author Oscar E. Ganteaume
 *
 */
public class CcdpPort
{

  private String portId;
  private List<String> fromPort = new ArrayList<String>();
  private List<String> toPort = new ArrayList<String>();
  
  public CcdpPort()
  {

  }
  
  /**
   * @return the portId
   */
  @JsonGetter("port-id")
  public String getPortId()
  {
    return portId;
  }

  /**
   * @param portId the portId to set
   */
  @JsonSetter("port-id")
  public void setPortId(String portId)
  {
    this.portId = portId;
  }

  /**
   * @return the fromPort
   */
  @JsonGetter("from-port")
  public List<String> getFromPort()
  {
    return fromPort;
  }

  /**
   * @param fromPort the fromPort to set
   */
  @JsonSetter("from-port")
  public void setFromPort(List<String> fromPort)
  {
    this.fromPort = fromPort;
  }

  /**
   * @return the toPort
   */
  @JsonGetter("to-port")
  public List<String> getToPort()
  {
    return toPort;
  }

  /**
   * @param toPort the toPort to set
   */
  @JsonSetter("to-port")
  public void setToPort(List<String> toPort)
  {
    this.toPort = toPort;
  }
}
