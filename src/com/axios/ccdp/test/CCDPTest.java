package com.axios.ccdp.test;


import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import org.apache.log4j.Logger;

import com.axios.ccdp.utils.CcdpUtils;

public class CCDPTest 
{

  /**
   * Generates debug print statements based on the verbosity level.
   */
  private Logger logger = Logger.getLogger(CCDPTest.class.getName());
  
  Map<String, List<Integer>> map = new HashMap<>();
  
  
  public CCDPTest()
  {
    this.logger.debug("Running CCDP Test");
    try
    {
      this.runTest();
    }
    catch( Exception e )
    {
      this.logger.error("Message: " + e.getMessage(), e);
    }
    
  }
  
  
  private void runTest() throws Exception
  {
    this.logger.debug("Running the Test");

   String id = "i-test-bff4b6a0c8c5";
   String cp = "i-test-bff4b6a0c8c5";
   
   this.logger.info("Test 1 = " + id.equals(cp));
   this.logger.info("Test 2 = " + (id ==  cp));

    
//    for( int i = 1; i < 4; i++ )
//
//    Properties props = System.getProperties();
//    Enumeration<Object> keys = props.keys();
//    while( keys.hasMoreElements() )
//
//    {
//      String key = (String)keys.nextElement();
//      this.logger.info("Property[" + key + " = " + props.getProperty(key));
//    }
//    
//
//    this.logger.debug("The Map: " + this.map.toString());
//    this.onEvent();
//    this.logger.debug("The Map: " + this.map.toString());
//    
//
//    boolean skip = CcdpUtils.getBooleanProperty(CcdpUtils.CFG_KEY_SKIP_HEARTBEATS);
//    this.logger.info("Skipping Sending HB " + skip );

  }
  
  
  public static void main( String[] args ) throws Exception
  {
    String cfg_file = System.getProperty("ccdp.config.file");
    CcdpUtils.loadProperties(cfg_file);
    CcdpUtils.configLogger();
    
    new CCDPTest();
  }

}



