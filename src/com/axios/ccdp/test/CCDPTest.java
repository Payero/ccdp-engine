package com.axios.ccdp.test;

import java.io.*;
import java.util.ArrayList;
import java.util.List;

import org.apache.log4j.Logger;

import com.axios.ccdp.utils.CcdpUtils;


public class CCDPTest 
{
  /**
   * Generates debug print statements based on the verbosity level.
   */
  private Logger logger = Logger.getLogger(CCDPTest.class.getName());
  
  
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

    Runtime runtime = Runtime.getRuntime();     //getting Runtime object
    
    try
    {
        runtime.exec("/nishome/srbenne/eclipse/eclipse");        //Just to test shell script execution
    }
    catch (IOException e)
    {
        e.printStackTrace();
    }
  }
  
  public static void main( String[] args ) throws Exception
  {
    String cfg_file = System.getProperty("ccdp.config.file");
    
    //System.out.println(cfg_file); ///projects/users/srbenne/workspace/engine/config/ccdp-config.json AS EXPECTED
    
    // Uses the cfg file to configure all CcdpUtils for use in the next service
    CcdpUtils.loadProperties(cfg_file);
    CcdpUtils.configLogger();
    
    new CCDPTest();
  }

}



