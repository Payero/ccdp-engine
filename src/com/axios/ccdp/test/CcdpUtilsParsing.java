package com.axios.ccdp.test;

import java.util.Collection;
import java.util.HashMap;

import org.apache.log4j.Logger;

import com.axios.ccdp.factory.CcdpObjectFactory;
import com.axios.ccdp.intfs.CcdpVMControllerIntf;
import com.axios.ccdp.utils.CcdpUtils;

import com.fasterxml.jackson.databind.JsonNode;
import com.spotify.docker.client.shaded.com.google.common.collect.ArrayListMultimap;
import com.spotify.docker.client.shaded.com.google.common.collect.Multimap;


public class CcdpUtilsParsing 
{
  /**
   * Generates debug print statements based on the verbosity level.
   */
  private Logger logger = Logger.getLogger(CcdpUtilsParsing.class.getName());
  HashMap<String,CcdpVMControllerIntf> controllerMap = new HashMap<>(); 

  
  public CcdpUtilsParsing(JsonNode cfg)
  {
    
    JsonNode node = CcdpUtils.getResourceCfg("DOCKER").deepCopy();
    
    
    /*
    CcdpObjectFactory factory = CcdpObjectFactory.newInstance();
    CcdpVMControllerIntf newCont = null;
    
    Multimap<String,String> controllerTypes = ArrayListMultimap.create();
    for ( String nodeType : CcdpUtils.getNodeTypes())
    {
      controllerTypes.put(cfg.get(nodeType).get(CcdpUtils.CFG_KEY_VM_CONTROLLER).toString().replace("\"", ""), nodeType);
    }
    System.out.println(controllerTypes);
    Collection<String> test = controllerTypes.get("com.axios.ccdp.impl.cloud.aws.AWSCcdpVMControllerImpl");
    System.out.println(test);
    
    for (String key : controllerTypes.keySet())
    {
      System.out.println(key);
      newCont = factory.getCcdpVMResourceController(cfg, key);
      for (String nodeType : controllerTypes.get(key))
      {
        this.logger.debug("Adding <" + nodeType.toString() + ", " + newCont.toString() + "> to map");
        controllerMap.put(nodeType, newCont);
      }
    }
    
    System.out.println(controllerMap.toString());*/
  }
    

  

  public static void main( String[] args ) throws Exception
  {
    //String cfg_file = System.getProperty("ccdp.config.file");
    
    //System.out.println(cfg_file); ///projects/users/srbenne/workspace/engine/config/ccdp-config.json AS EXPECTED
    
    // Uses the cfg file to configure all CcdpUtils for use in the next service
    CcdpUtils.loadProperties("/projects/users/srbenne/workspace/engine/config/ccdp-config.json");
    CcdpUtils.configLogger();
    
    JsonNode cfg = CcdpUtils.getResourcesCfg();
    
    new CcdpUtilsParsing(cfg);
  }

}



