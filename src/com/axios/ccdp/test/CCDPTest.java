package com.axios.ccdp.test;

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
    List<Integer> nums = new ArrayList<>();
    for(int i = 0; i < 10; i++)
      nums.add(i);
    
    for( Integer n : nums )
    {
      if( n % 2 == 0 )
      {
        this.logger.debug("Removing " + n );
        nums.remove(n);
      }
    }
    
  }
  
  public static void main( String[] args ) throws Exception
  {
    String cfg_file = System.getProperty("ccdp.config.file");
    CcdpUtils.loadProperties(cfg_file);
    CcdpUtils.configLogger();
    
    new CCDPTest();
  }

}



