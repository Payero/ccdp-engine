/*
 * 
 * Scott Bennett, scott.bennett@caci.com
 * This class allows the CCDP Engine to handle nodes of all kinds,
 * provided their VM Controller Type in the "resource" section.
 * 
 */
package com.axios.ccdp.impl.controllers;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import org.apache.log4j.Logger;

import com.axios.ccdp.factory.CcdpObjectFactory;
import com.axios.ccdp.intfs.CcdpDatabaseIntf;
import com.axios.ccdp.intfs.CcdpVMControllerIntf;
import com.axios.ccdp.resources.CcdpImageInfo;
import com.axios.ccdp.resources.CcdpVMResource.ResourceStatus;
import com.axios.ccdp.utils.CcdpConfigParser;
import com.axios.ccdp.utils.CcdpUtils;
import com.fasterxml.jackson.databind.JsonNode;
import com.spotify.docker.client.shaded.com.google.common.collect.ArrayListMultimap;
import com.spotify.docker.client.shaded.com.google.common.collect.Multimap;


public class CcdpMasterVMController
{
  /**
   * Generates debug print statements based on the verbosity level.
   */
  private Logger logger = Logger
      .getLogger(CcdpMasterVMController.class.getName());
  /*
   * A map to map node types to controllers
   */
  private HashMap<String,CcdpVMControllerIntf> controllerMap = new HashMap<>();
  /*
   * A database intf to use for determining node type from iid
   */  
  private CcdpDatabaseIntf dbClient = null;
  /*
   * A controller device to use when referencing created controllers
   * Also set this before use or it will possibly reference the wrong thing
   */
  private CcdpVMControllerIntf vm_controller = null;
  
  public CcdpMasterVMController( JsonNode ctl_config, JsonNode db_config )
  {
    this.logger.debug("New MasterVMController created, configuring");
    if( ctl_config == null )
      throw new IllegalArgumentException("The config cannot be null");
    
    
    // A new factory for the individual VM controllers
    CcdpObjectFactory factory = CcdpObjectFactory.newInstance();
    
    // Create the database link to use later for iid queries
    dbClient = factory.getCcdpDatabaseIntf(db_config);
    dbClient.connect();
    
    // Get all the nodes and their controller types
    Multimap<String,String> controllerTypes = ArrayListMultimap.create();
    for ( String nodeType : CcdpUtils.getNodeTypes())
    {
      // For some reason, the quotes are preserved, so get rid of them with replace
      controllerTypes.put(ctl_config.get(nodeType).get(CcdpConfigParser.KEY_VM_CONTROLLER).toString().replace("\"", ""), nodeType);
    }
    
    /* 
     * For each key, make a new controller, then for each value of each key, add the
     * reverse to a map 
     */
    for (String key : controllerTypes.keySet())
    {
      vm_controller = factory.getCcdpVMResourceController( ctl_config, key);
      for (String nodeType : controllerTypes.get(key))
      {
        this.logger.debug("Adding <" + nodeType + ", " + vm_controller.toString() + "> to map");
        controllerMap.put(nodeType, vm_controller);
      }
    }
    this.logger.debug("ControllerMap: \n" + controllerMap.toString());
  }
  
  public List<String> startInstances( CcdpImageInfo imgCfg )
  {
    List<String> launched = new ArrayList<>();
    String nodeType = imgCfg.getNodeType();
    
    //Determine the controller
    if ( controllerMap.containsKey(nodeType) )
    {
      vm_controller = controllerMap.get(nodeType);
      launched = vm_controller.startInstances(imgCfg);
    }
    else 
    {
      this.logger.debug("The node type provided in imgCfg doesn't exist.");
      launched = null;
    }
    return launched;
  }
  
  public void terminateInstances( List<String> terminateIds )
  {
    boolean deleted = false;
    String nodeType = null;
    List<String> singleID = new ArrayList<>();
    
    // For each ID, get the controller and terminate the instance
    for ( String id : terminateIds ) 
    {
      singleID.add(id);
      nodeType = dbClient.getVMInformation(id).getNodeType();
      vm_controller = controllerMap.get(nodeType);
      deleted = vm_controller.terminateInstances(singleID);
      singleID.clear();
    }
    
    if ( !deleted )
    {
      this.logger.debug("Not all VMs in list were terminated");
    }
    else
      this.logger.debug("All VMs in list were termianted");
  }
  
  public ResourceStatus getInstanceState( String iid )
  {
    String nodeType = dbClient.getVMInformation(iid).getNodeType();
    vm_controller = controllerMap.get(nodeType);
    return vm_controller.getInstanceState(iid);
  }
  
}
