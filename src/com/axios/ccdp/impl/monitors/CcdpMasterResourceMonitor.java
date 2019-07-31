/*
 * 
 * Scott Bennett, scott.bennett@caci.com
 * 
 */

package com.axios.ccdp.impl.monitors;

import org.apache.log4j.Logger;

import com.axios.ccdp.factory.CcdpObjectFactory;
import com.axios.ccdp.utils.CcdpUtils;
import com.fasterxml.jackson.databind.JsonNode;

public class CcdpMasterResourceMonitor
{

  /**
   * Generates debug print statements based on the verbosity level.
   */
  private Logger logger = Logger
      .getLogger(CcdpMasterResourceMonitor.class.getName());
  
  public CcdpMasterResourceMonitor()
  {
    this.logger.debug("New MasterResourceMonitor created");
  }
  
  public static SystemResourceMonitorAbs getCcdpResourceMonitor( String type )
  {
    // A new factory for the individual VM controllers
    CcdpObjectFactory factory = CcdpObjectFactory.newInstance();
    JsonNode cfg = CcdpUtils.getResourceCfg(type);
    return factory.getResourceMonitorInterface(cfg);
  }
}
