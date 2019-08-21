package com.axios.ccdp.test;

import java.util.HashMap;

import org.apache.log4j.Logger;

import com.axios.ccdp.factory.CcdpObjectFactory;
import com.axios.ccdp.impl.controllers.CcdpServerlessControllerAbs;
import com.axios.ccdp.utils.CcdpConfigParser;
import com.axios.ccdp.utils.CcdpUtils;
import com.fasterxml.jackson.databind.JsonNode;
import com.spotify.docker.client.shaded.com.google.common.collect.ArrayListMultimap;
import com.spotify.docker.client.shaded.com.google.common.collect.Multimap;



public class CCDPTest 
{
  /**
   * Generates debug print statements based on the verbosity level.
   */
  private Logger logger = Logger.getLogger(CCDPTest.class.getName());
  
  private CcdpServerlessControllerAbs serverless_cont = null;
  private HashMap<String, CcdpServerlessControllerAbs> controllerMap = new HashMap<>();


  public CCDPTest() throws Exception
  {
    JsonNode svr_cfg = CcdpUtils.getServerlessCfg();
    System.out.println(svr_cfg);
    CcdpObjectFactory factory = CcdpObjectFactory.newInstance();
    
    Multimap<String,String> controllerTypes = ArrayListMultimap.create();
    for ( String svrlessType : CcdpUtils.getServerlessTypes())
    {
      //For some reason, the quotes are preserved, so get rid of them with replace
      controllerTypes.put(svr_cfg.get(svrlessType).get(CcdpConfigParser.KEY_SERVERLESS_CONTROLLER).toString().replace("\"", ""), svrlessType);
    }
    System.out.println(controllerTypes);
    
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
  
  public static void main( String[] args ) throws Exception
  {
    String cfg_file = System.getProperty("ccdp.config.file");
        
    // Uses the cfg file to configure all CcdpUtils for use in the next service
    CcdpUtils.loadProperties(cfg_file);
    CcdpUtils.configLogger();
    
    new CCDPTest();
  }

}



