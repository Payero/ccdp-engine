/*
 * @author Scott Bennett, scott.bennett@caci.com
 * 
 * This class add functionality to database incorporation into CCDP. Developer code can
 * be run using this class for Database implementations.
 */
package com.axios.ccdp.intfs;

import com.fasterxml.jackson.databind.JsonNode;

public interface CcdpDatabaseEventTriggerIntf extends Runnable
{

  /**
   * Configures the running environment and/or connections required to perform
   * the operations.  The JSON Object contains all the different fields 
   * necessary to operate.  These fields might change on each actual 
   * implementation
   * 
   * @param config a JSON Object containing all the necessary fields required 
   *        to operate
   */
  public void configure( JsonNode config );
}
