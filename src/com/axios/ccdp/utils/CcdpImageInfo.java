package com.axios.ccdp.utils;

import java.util.HashMap;
import java.util.Map;


import com.axios.ccdp.utils.CcdpUtils.CcdpNodeType;
import com.fasterxml.jackson.annotation.JsonGetter;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Container class used to store all the information required to launch an 
 * instance.  It is based on AWS requirements and might have to be adapted if
 * other cloud providers are used
 * 
 * @author Oscar E. Ganteaume
 *
 */
public class CcdpImageInfo
{
  /**
   * The minimum number of running instances at any given time
   */
  private int minReq = 0;
  /**
   * The maximum number of running instances at any given time
   */
  private int maxReq = 1;
  /**
   * The type of Node the instances of this class will be used for
   */
  private CcdpNodeType nodeType = CcdpNodeType.DEFAULT;
  /**
   * An unique id used to identify the image to use
   */
  private String imageId = null;
  /**
   * The session id that will use an instance of this type of node
   */
  private String sessionId = null;
  /**
   * The security group used for all instances of this class
   */
  private String secGrp = null;
  /**
   * The subnet used for all instances of this class
   */
  private String subnet = null;
  /**
   * The name of the key file used to authenticate and authorize the account 
   * starting/stopping the instances
   */
  private String keyFile = null;
  /**
   * The command or script to execute at startup
   */
  private String startupCommand = "";
  /**
   * A map with all the tags to be added to the instance
   */
  private Map<String, String> tags = new HashMap<>();
  
  /**
   * Instantiates a new object, but does not perform any operation
   */
  public CcdpImageInfo()
  {
  }
  
  /**
   * Instantiates a new object and sets all the different parameters passed as
   * arguments
   * 
   * @param min the minimum number of running instances at any given time
   * @param max the maximum number of running instances at any given time
   * @param type the type of Node the instances of this class will be used for
   * @param imgId an unique id used to identify the image to use
   * @param sessionId the session id associated with this instance
   * @param secGrp the security group used for all instances of this class
   * @param subnet the subnet used for all instances of this class
   * @param keyFile the name of the key file used to authenticate and authorize 
   *        the account starting/stopping the instances
   * @param tags a map with all the tags to be added to the instance
   */
  public CcdpImageInfo(int min, int max, CcdpNodeType type, 
      String imgId, String sessionId, String secGrp, String subnet, 
      String keyFile, Map<String, String> tags)
  {
    this.setMinReq(min);
    this.setMaxReq(max);
    this.setNodeType(type);
    this.setImageId(imgId);
    this.setSessionId(sessionId);
    this.setSecGrp(secGrp);
    this.setSubnet(subnet);
    this.setKeyFile(keyFile);
    this.setTags(tags);
  }

  /**
   * Creates a copy of the given CcdpImageConfiguration object.  This method
   * should be used to avoid modifying the source object.  This method 
   * effectively generates a new object that is a clone of the source.
   * 
   * @param source the object containing the information to clone
   */
  public CcdpImageInfo( CcdpImageInfo source )
  {
    this.setMinReq(source.getMinReq());
    this.setMaxReq(source.getMaxReq());
    this.setNodeType(source.getNodeType());
    this.setImageId(source.getImageId());
    this.setSessionId(source.getSessionId());
    this.setSecGrp(source.getSecGrp());
    this.setSubnet(source.getSubnet());
    this.setKeyFile(source.getKeyFile());
    
    Map<String, String> tags = source.getTags();
    Map<String, String> tgt = new HashMap<>();
    for( String key : tags.keySet() )
    {
      tgt.put(key, tags.get(key));
    }
    this.setTags(tgt);
  }
  
  /**
   * Gets the minimum number of running instances at any given time 
   * @return the minimum number of running instances at any given time
   */
  @JsonGetter("min-req")
  public int getMinReq()
  {
    return minReq;
  }

  /**
   * Sets the minimum number of running instances at any given time 
   * 
   * @param minReq the minimum number of running instances at any given time
   */
  @JsonSetter("min-req")
  public void setMinReq(int minReq)
  {
    this.minReq = minReq;
  }

  /**
   * Gets the maximum number of running instances at any given time 
   * @return the maximum number of running instances at any given time
   */
  @JsonGetter("max-req")
  public int getMaxReq()
  {
    return maxReq;
  }

  /**
   * Sets the maximum number of running instances at any given time 
   * 
   * @param maxReq the maximum number of running instances at any given time
   */
  @JsonSetter("max-req")
  public void setMaxReq(int maxReq)
  {
    this.maxReq = maxReq;
  }
  
  /**
   * Gets the type of Node the instances of this class will be used for
   * 
   * @return the type of Node the instances of this class will be used for
   */
  public CcdpNodeType getNodeType()
  {
    return nodeType;
  }

  /**
   * Sets the type of Node the instances of this class will be used for
   * 
   * @param nodeType the type of Node the instances of this class will be used for
   */
  public void setNodeType(CcdpNodeType nodeType)
  {
    this.nodeType = nodeType;
  }

  /**
   * Gets a string representation of the type of Node the instances of this 
   * class will be used for
   * 
   * @return the type of Node the instances of this class will be used for as 
   *         a string
   */
  @JsonGetter("node-type")
  public String getNodeTypeAsString()
  {
    return nodeType.toString();
  }

  /**
   * Sets a the type of Node the instances of this class will be used for from
   * a node type name
   * 
   * @param nodeType the type of Node the instances of this class will be used 
   *        for from a string
   */
  @JsonSetter("node-type")
  public void setNodeType(String nodeType)
  {
    this.nodeType = CcdpNodeType.valueOf(nodeType);
  }
  
  /**
   * Gets an unique id used to identify the image to use
   * 
   * @return the imgId an unique id used to identify the image to use
   */
  @JsonGetter("image-id")
  public String getImageId()
  {
    return imageId;
  }

  /**
   * Sets the unique id used to identify the image to use
   * 
   * @param imageId the unique id used to identify the image to use
   */
  @JsonSetter("image-id")
  public void setImageId(String imageId)
  {
    this.imageId = imageId;
  }
  
  /**
   * Gets the session id associated with this instance
   * 
   * @return the session id associated with this instance
   */
  @JsonGetter("session-id")
  public String getSessionId()
  {
    return sessionId;
  }

  /**
   * Sets the session id associated with this instance
   * 
   * @param sessionId the session id associated with this instance
   */
  @JsonSetter("session-id")
  public void setSessionId(String sessionId)
  {
    this.sessionId = sessionId;
  }
  
  /**
   * Gets the security group used for all instances of this class
   * 
   * @return the security group used for all instances of this class
   */
  @JsonGetter("security-group")
  public String getSecGrp()
  {
    return secGrp;
  }

  /**
   * Sets the security group used for all instances of this class
   * 
   * @param secGrp the security group used for all instances of this class
   */
  @JsonSetter("security-group")
  public void setSecGrp(String secGrp)
  {
    this.secGrp = secGrp;
  }

  /**
   * Gets the subnet used for all instances of this class
   * 
   * @return the subnet used for all instances of this class
   */
  @JsonGetter("subnet-id")
  public String getSubnet()
  {
    return subnet;
  }

  /**
   * Sets the subnet used for all instances of this class
   * 
   * @param subnet the subnet used for all instances of this class
   */
  @JsonSetter("subnet-id")
  public void setSubnet(String subnet)
  {
    this.subnet = subnet;
  }

  /**
   * Gets the name of the key file used to authenticate and authorize the 
   * account starting/stopping the instances
   * 
   * @return the name of the key file used to authenticate and authorize 
   *         the account starting/stopping the instances
   */
  @JsonGetter("key-file")
  public String getKeyFile()
  {
    return keyFile;
  }

  /**
   * Sets the name of the key file used to authenticate and authorize the 
   * account starting/stopping the instances
   * 
   * @param keyFile the name of the key file used to authenticate and authorize 
   *        the account starting/stopping the instances
   */
  @JsonSetter("key-file")
  public void setKeyFile(String keyFile)
  {
    this.keyFile = keyFile;
  }
  
  /**
   * Gets the command or script to execute at startup
   * 
   * @return the command or script to execute at startup
   */
  @JsonGetter("startup-command")
  public String getStartupCommand()
  {
    return startupCommand;
  }

  /**
   * Sets the command or script to execute at startup
   * 
   * @param startupCmd the command or script to execute at startup
   */
  @JsonSetter("startup-command")
  public void setStartupCommand(String startupCmd)
  {
    this.startupCommand = startupCmd;
  }
  
  
  /**
   * Gets a map with all the tags to be added to the instance
   * 
   * @return a map with all the tags to be added to the instance
   */
  @JsonGetter("configuration")
  public Map<String, String> getTags()
  {
    return tags;
  }

  /**
   * Sets a map with all the tags to be added to the instance
   * 
   * @param tags a map with all the tags to be added to the instance
   */
  @JsonSetter("configuration")
  public void setTags(Map<String, String> tags)
  {
    this.tags = tags;
  }

  /**
   * Gets a JSON representation of this object
   * 
   * @return a JSON representation of this object
   */
  public ObjectNode toJSON()
  {
    return new ObjectMapper().convertValue( this, ObjectNode.class );
  }
  
  /**
   * Returns a string containing all the information of this object using the
   * JSON construct
   */
  public String toString()
  {
    String str = null;
    
    try
    {
      
      str = new ObjectMapper().writeValueAsString(this);
    }
    catch( Exception e )
    {
      throw new RuntimeException("Could not write Json " + e.getMessage() );
    }
    
    return str;
  }

  /**
   * Prints the contents of the object using a more human readable form.
   * 
   * @return a String representation of the object using a more human friendly
   *         formatting
   */
  public String toPrettyPrint()
  {
    String str = null;
    
    try
    {
      ObjectMapper mapper = new ObjectMapper();
      mapper.enable(SerializationFeature.INDENT_OUTPUT);
      str = mapper.writeValueAsString(this);
    }
    catch( Exception e )
    {
      throw new RuntimeException("Could not write Json " + e.getMessage() );
    }
    
    return str;
  }
}
