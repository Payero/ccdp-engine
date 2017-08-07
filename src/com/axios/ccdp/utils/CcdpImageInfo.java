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
   * The type of Node the instances of this class will be used for
   */
  private CcdpNodeType nodeType = CcdpNodeType.DEFAULT;
  /**
   * An unique id used to identify the image to use
   */
  private String imageId = null;
  /**
   * The minimum number of running instances at any given time
   */
  private int minReq = 0;
  /**
   * The maximum number of running instances at any given time
   */
  private int maxReq = 1;
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
  
  
  private String region = null;
  private String roleName = null;
  private String proxyUrl = null;
  private int proxyPort = -1;
  private String credentialsFile = null;
  private String profileName = null;
  private String instanceType = "t2.micro";
  
  /**
   * Instantiates a new object, but does not perform any operation
   */
  public CcdpImageInfo()
  {
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
    this.setRegion(source.getRegion());
    this.setRoleName(source.getRoleName());
    this.setProxyUrl(source.getProxyUrl());
    this.setProxyPort(source.getProxyPort());
    this.setCredentialsFile(source.getCredentialsFile());
    this.setProfileName(source.getProfileName());
    this.setInstanceType(source.getInstanceType());
    
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
  @JsonGetter("tags")
  public Map<String, String> getTags()
  {
    return tags;
  }

  /**
   * Sets a map with all the tags to be added to the instance
   * 
   * @param tags a map with all the tags to be added to the instance
   */
  @JsonSetter("tags")
  public void setTags(Map<String, String> tags)
  {
    this.tags = tags;
  }

  /**
   * Gets the region where the new instances were created
   * 
   * @return gets the region where the instances were created
   */
  @JsonGetter("region")
  public String getRegion()
  {
    return region;
  }

  /**
   * Sets the region where the new instances need to be created
   * 
   * @param region the region where the new instances will reside
   */
  @JsonSetter("region")
  public void setRegion(String region)
  {
    this.region = region;
  }

  /**
   * Gets the instance's role name
   * 
   * @return the instance's role name
   */
  @JsonGetter("role-name")
  public String getRoleName()
  {
    return roleName;
  }

  /**
   * Sets the instance's role name
   * 
   * @param roleName the instance's role name
   */
  @JsonSetter("role-name")
  public void setRoleName(String roleName)
  {
    this.roleName = roleName;
  }

  /**
   * Gets the instance's proxy-url
   * 
   * @return the instance's proxy url
   */
  @JsonGetter("proxy-url")
  public String getProxyUrl()
  {
    return proxyUrl;
  }

  /**
   * Sets the instance's proxy url
   * 
   * @param proxyUrl the instance's proxy url
   */
  @JsonSetter("proxy-url")
  public void setProxyUrl(String proxyUrl)
  {
    this.proxyUrl = proxyUrl;
  }
  
  /**
   * Gets the instance's proxy port number
   * 
   * @return the instance's proxy port number
   */
  @JsonGetter("proxy-port")
  public int getProxyPort()
  {
    return proxyPort;
  }

  /**
   * Sets the instance's proxy port number
   * 
   * @param proxyPort the instance's proxy port number
   */
  @JsonSetter("proxy-url")
  public void setProxyPort(int proxyPort)
  {
    this.proxyPort = proxyPort;
  }
  
  /**
   * Gets the profile file used to load the credentials from
   * 
   * @return the profile file used to load the credentials from
   */
  @JsonGetter("credentials-file")
  public String getCredentialsFile()
  {
    return credentialsFile;
  }

  /**
   * Sets the profile file used to load the credentials from
   * 
   * @param credentialsFile the profile file used to load the credentials from
   */
  @JsonSetter("credentials-file")
  public void setCredentialsFile(String credentialsFile)
  {
    this.credentialsFile = credentialsFile;
  }

  /**
   * Gets the profile name used to load the credentials from
   * 
   * @return the profile name used to load the credentials from
   */
  @JsonGetter("profile-name")
  public String getProfileName()
  {
    return profileName;
  }
  
  /**
   * Sets the profile name used to load the credentials from
   * 
   * @param credentialsProfile the profile name used to load the credentials 
   *        from
   */
  @JsonSetter("profile-name")
  public void setProfileName(String credentialsProfile)
  {
    this.profileName = credentialsProfile;
  }
  
  /**
   * Gets the instance type to use when creating instances
   * 
   * @return the instance type to use when creating instances
   */
  @JsonGetter("instance-type")
  public String getInstanceType()
  {
    return instanceType;
  }
  
  /**
   * Sets the instance type to use when creating instances
   * 
   * @param instType the instance type to use when creating instances
   * 
   */
  @JsonSetter("instance-type")
  public void setInstanceType(String instType)
  {
    this.instanceType = instType;
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
