package com.axios.ccdp.tasking;

import java.util.ArrayList;
import java.util.List;

import org.apache.log4j.Logger;

import com.fasterxml.jackson.annotation.JsonGetter;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 *  Class representing a port or connection for data flow.  The JSON structure
 *  looks as follow:
 *  {
 *    "port-id" : "from-exterior",
 *    "input-ports" : [ "source-1", "source-2" ],
 *    "output-ports" : [ "dest-1", "dest-2" ]
 *  } 
 *                     
 * @author Oscar E. Ganteaume
 *
 */
public class CcdpPort
{
  /**
   * Generates debug print statements based on the verbosity level.
   */
  private Logger logger = Logger.getLogger(CcdpPort.class.getName());
  /**
   * Stores the unique identifier for this port
   */
  private String portId;
  /**
   * Stores the list of port ids where data will be coming from
   */
  private List<String> fromPort = new ArrayList<String>();
  /**
   * Stores the list of port ids where data will be sent to
   */
  private List<String> toPort = new ArrayList<String>();
  /**
   * Generates all the JSON objects for this object
   */
  private ObjectMapper mapper = new ObjectMapper();
  
  /**
   * Instantiates a new object, but it does not perform any function
   */
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
  
  /**
   * Gets a JSON representation of this object.  The resulting string is as
   * following
   * 
   *  {
   *    "port-id" : "from-exterior",
   *    "input-ports" : [ "source-1", "source-2" ],
   *    "output-ports" : [ "dest-1", "dest-2" ]
   *  } 
   * 
   * @return a JSON representation of the object
   */
  public String toString()
  {
    
    ObjectNode node = this.toJSON();
    String str = node.toString();
    try
    {
      str = 
          this.mapper.writerWithDefaultPrettyPrinter().writeValueAsString(node);
    }
    catch( JsonProcessingException e )
    {
      this.logger.error("Message: " + e.getMessage(), e);
    }
    
    return str;
  }
  
  /**
   * Gets a JSON representation of this task.  The resulting string is as
   * following
   * 
   *  {
   *    "port-id" : "from-exterior",
   *    "input-ports" : [ "source-1", "source-2" ],
   *    "output-ports" : [ "dest-1", "dest-2" ]
   *  } 
   *  
   * @return a JSON object representing this task
   */
  public ObjectNode toJSON()
  {
    ObjectNode port = this.mapper.createObjectNode();
    ArrayNode in = this.mapper.createArrayNode();
    ArrayNode out = this.mapper.createArrayNode();
    
    port.put("port-id",       this.portId);
    
    for( String input : this.fromPort )
      in.add(input);
    port.set("input-ports", in);
    
    for( String to : this.toPort )
      out.add(to);
    port.set("output-ports", out);
    
    return port;
  }
}
