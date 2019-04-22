/**
 * 
 */
package com.axios.ccdp.impl.image.loader;

import java.util.Map;

import com.axios.ccdp.intfs.CcdpImgLoaderIntf;
import com.axios.ccdp.resources.CcdpImageInfo;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * @author oegante
 *
 */
public class DefaultImageLoaderImpl implements CcdpImgLoaderIntf
{

  private ObjectNode config = null;
  private String nodeType = "";
  
  @Override
  public void configure(String nodeType, ObjectNode config)
  {
    this.nodeType = nodeType;
    this.config = config;
  }

  @Override
  public CcdpImageInfo getImageInfo()
  {
    if( this.config == null )
      throw new RuntimeException("The configuration is missing, please invoke configure() first ");
    
    CcdpImageInfo img = new CcdpImageInfo();
    
    img.setNodeType(nodeType);
    img.setImageId (this.config.get("image-id").asText() );
    img.setMinReq( this.config.get("min-number-free-agents").asInt());
    img.setMaxReq( this.config.get("min-number-free-agents").asInt());
    img.setSecGrp( this.config.get("security-group").asText() );
    img.setSubnet( this.config.get("subnet-id").asText() );
    img.setKeyFile( this.config.get("key-file-name").asText() );
    img.setAssignmentCommand( this.config.get("assignment-command").asText() );
    img.setRegion( this.config.get("region").asText() );
    img.setRoleName( this.config.get("role-name").asText() );
    img.setProxyUrl( this.config.get("proxy-url").asText() );
    img.setProxyPort( this.config.get("proxy-port").asInt() );
    img.setCredentialsFile( this.config.get("credentials-file").asText() );
    img.setProfileName( this.config.get("credentials-profile-name").asText() );
    
    JsonNode node = this.config.get("startup-command");
    if( node != null && node.isArray() )
    {
      String startupCommand = "";
      for( JsonNode tmp : node )
        startupCommand += tmp.asText();
      
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
