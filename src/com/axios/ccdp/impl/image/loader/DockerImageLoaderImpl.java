/**
 * 
 */
package com.axios.ccdp.impl.image.loader;


import org.apache.log4j.Logger;

import com.axios.ccdp.intfs.CcdpImgLoaderIntf;
import com.axios.ccdp.resources.CcdpImageInfo;
import com.fasterxml.jackson.databind.JsonNode;

/**
 * Generates a Docker image using the parameters in the given configuration
 * object
 *  
 * @author Oscar E. Ganteaume
 *
 */
public class DockerImageLoaderImpl implements CcdpImgLoaderIntf
{
  /**
   * Generates debug print statements based on the verbosity level.
   */
  private Logger logger = 
                    Logger.getLogger(DockerImageLoaderImpl.class.getName());
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
    logger.debug("Configure completed");
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
    img.setMaxReq( this.config.get("min-number-free-agents").asInt());
    
    JsonNode node = this.config.get("startup-command");
    if( node != null && node.isArray() )
    {
      String startupCommand = "";
      for( JsonNode tmp : node )
      {
        startupCommand += ( tmp.asText() + " " );
      }
      img.setStartupCommand( startupCommand );
    }
    logger.debug("Got img: " + img.toPrettyPrint());
    return img;
  }

}
