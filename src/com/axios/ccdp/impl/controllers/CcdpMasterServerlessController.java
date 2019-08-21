/*
 * Scott Bennett, scott.bennett@caci.com
 * 
 * This controller works similarly to how the CcdpMasterVMController works
 * Instead of dealing with the node controllers, this controller allocates one
 * instance of every type of serverless provider controller outlined in the 
 * "serverless" section of "resource-provisioning" in the ccdp-config.json file.
 */

package com.axios.ccdp.impl.controllers;

import java.util.HashMap;
import org.apache.log4j.Logger;

import com.axios.ccdp.factory.CcdpObjectFactory;
import com.axios.ccdp.intfs.CcdpDatabaseIntf;
import com.axios.ccdp.tasking.CcdpTaskRequest;
import com.axios.ccdp.utils.CcdpConfigParser;
import com.axios.ccdp.utils.CcdpUtils;
import com.fasterxml.jackson.databind.JsonNode;
import com.spotify.docker.client.shaded.com.google.common.collect.ArrayListMultimap;
import com.spotify.docker.client.shaded.com.google.common.collect.Multimap;

public class CcdpMasterServerlessController
{
  /*
   * Generates debug print statements based on the verbosity level
   */
  private Logger logger = Logger
      .getLogger(CcdpMasterServerlessController.class.getName());
  /*
   * A map to map node types to controllers
   */
  private HashMap<String, CcdpServerlessControllerAbs> controllerMap = new HashMap<>();
  /*
   * A client to talk to the database
   */
  private CcdpDatabaseIntf dbClient = null;
  /*
   * A controller device to use when referencing controllers created by the master controller
   */
  private CcdpServerlessControllerAbs serverless_cont = null;

  public CcdpMasterServerlessController(JsonNode svr_cfg, JsonNode db_config)
  {
    this.logger.debug("New Serverless Controller created, configuring");
    if (svr_cfg == null || db_config == null)
      throw new IllegalArgumentException("The config cannot be null");  
    
    // A factory for creating the objects
    CcdpObjectFactory factory = CcdpObjectFactory.newInstance();
    
    // Create the database link to use for publishing to the database
    dbClient = factory.getCcdpDatabaseIntf(db_config);
    dbClient.connect();
    
    // Get all the nodes and their controller types
    Multimap<String,String> controllerTypes = ArrayListMultimap.create();
    for ( String svrlessType : CcdpUtils.getServerlessTypes())
    {
      // For some reason, the quotes are preserved, so get rid of them with replace
      controllerTypes.put(svr_cfg.get(svrlessType).get(CcdpConfigParser.KEY_SERVERLESS_CONTROLLER).toString().replace("\"", ""), svrlessType);
    }
    
    /* 
     * For each key, make a new controller, then for each value of each key, add the
     * reverse to a map 
     */
    for (String key : controllerTypes.keySet())
    {
      serverless_cont = factory.getCcdpServerlessResourceController( svr_cfg, key);
      for (String serverlessType : controllerTypes.get(key))
      {
        this.logger.debug("Adding <" + serverlessType + ", " + serverless_cont.toString() + "> to map");
        controllerMap.put(serverlessType, serverless_cont);
      }
    }
    this.logger.debug("ControllerMap: \n" + controllerMap.toString());
  }
  
  public void runTask (CcdpTaskRequest task)
  {
    // Check to make sure task is serverless and the config is not null
    if (task.getServerless() == false || task.getServerlessCfg() == null)
    {
      this.logger.error("The task isn't serverless or the Cfg isn't present, aborting serverless tasking");
      return;
    }
    
    // Get the configuration and get the provider. Use that to call run task
    String provider = task.getServerlessCfg().get(CcdpUtils.S_CFG_PROVIDER);
    serverless_cont = controllerMap.get(provider);
    
    // Make sure controller was found and run the task
    if (serverless_cont == null)
    {
      this.logger.error("The serverless provider from the task doesn't match a loaded configuration, aborting");
      throw new IllegalArgumentException("Serverless provider doesn't exist in Provider Map");
    }
    serverless_cont.runTask(task);
  }
}
