package com.axios.ccdp.test;


import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Enumeration;
import java.util.List;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import org.apache.log4j.Logger;

import com.axios.ccdp.newgen.CcdpTaskRunner;
import com.axios.ccdp.tasking.CcdpTaskRequest;
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

    SimpleDateFormat formatter = 
        new SimpleDateFormat("yyyy-MM-dd hh:mm:ss");
    
    this.logger.debug("The Date " + formatter.format( new Date() ));
    
  }
  
  
  public static void main( String[] args ) throws Exception
  {
    String cfg_file = System.getProperty("ccdp.config.file");
    CcdpUtils.loadProperties(cfg_file);
    CcdpUtils.configLogger();
    
    new CCDPTest();
  }

}



