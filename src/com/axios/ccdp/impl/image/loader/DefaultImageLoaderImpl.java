/**
 * 
 */
package com.axios.ccdp.impl.image.loader;

import java.util.Map;

import com.axios.ccdp.intfs.CcdpImgLoaderIntf;
import com.axios.ccdp.resources.CcdpImageInfo;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Generates a DEFAULT image using the parameters in the given configuration
 * object
 *  
 * @author Oscar E. Ganteaume
 *
 */
public class DefaultImageLoaderImpl implements CcdpImgLoaderIntf
{
  /**
   * Stores all the configurations for this image
   */
  private JsonNode config = null;
  /**
   * Stores the name of the Node Type this class is responsible for parsing
   */
  private String nodeType = "";

  /**
   * Configures all the specific parameters used to load a CcdpImage.  
   * 
   * @param nodeType the type of node to create
   * @param config the specific parameters used to load a CcdpImage.  
   */
  @Override
  public void configure(String nodeType, JsonNode config)
  {
    this.nodeType = nodeType;
    this.config = config;
  }

  /**
   * Generates a CcdpImageInfo object using the parameters passed through the
   * configure() method.
   * 
   * @return a CcdpImageInfo object using the parameters passed through the
   *         configure() method.
   */
  @Override
  public CcdpImageInfo getImageInfo()
  {
    if( this.config == null )
      throw new RuntimeException("The configuration is missing, please invoke configure() first ");
    
    CcdpImageInfo img = new CcdpImageInfo();
    
    img.setNodeType(nodeType);
    img.setImageId (this.config.get("image-id").asText() );
    img.setMinReq( this.config.get("min-number-free-agents").asInt());
    img.setSecGrp( this.config.get("security-group").asText() );
    img.setSubnet( this.config.get("subnet-id").asText() );
    img.setKeyFile( this.config.get("key-file-name").asText() );
    img.setAssignmentCommand( this.config.get("assignment-command").asText() );
    img.setRegion( this.config.get("region").asText() );
    img.setRoleName( this.config.get("role-name").asText() );
    if( this.config.has("proxy-url") )
      img.setProxyUrl( this.config.get("proxy-url").asText() );
    if( this.config.has("proxy-port") )
      img.setProxyPort( this.config.get("proxy-port").asInt() );
    if( this.config.has("credentials-file") )
      img.setCredentialsFile( this.config.get("credentials-file").asText() );
    if( this.config.has("credentials-profile-name") )
      img.setProfileName( this.config.get("credentials-profile-name").asText() );
    
    JsonNode node = this.config.get("startup-command");
    if( node != null && node.isArray() )
    {
      String startupCommand = "";
      for( JsonNode tmp : node )
        startupCommand += ( tmp.asText() + " " );
      
      img.setStartupCommand( startupCommand );
    }
    JsonNode nodeTags = this.config.get("tags");
    if( nodeTags != null )
    {
      
      @SuppressWarnings("unchecked")
      Map<String, String> tags = 
          new ObjectMapper().convertValue(nodeTags, Map.class);
      img.setTags(tags);
    }
    
    return img;
  }

}
