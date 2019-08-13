package com.axios.ccdp.intfs;

import com.axios.ccdp.resources.CcdpImageInfo;
import com.fasterxml.jackson.databind.JsonNode;

public interface CcdpImgLoaderIntf
{
  /**
   * Configures all the specific parameters used to load a CcdpImage.  
   * 
   * @param nodeType the type of node to create
   * @param config the specific parameters used to load a CcdpImage.  
   */
  public void configure( String nodeType, JsonNode config );
  
  /**
   * Generates a CcdpImageInfo object using the parameters passed through the
   * configure() method.
   * 
   * @return a CcdpImageInfo object using the parameters passed through the
   *         configure() method.
   */
  public CcdpImageInfo getImageInfo();
  
}
