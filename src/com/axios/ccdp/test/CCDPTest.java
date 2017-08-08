package com.axios.ccdp.test;


import java.nio.file.Files;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Enumeration;
import java.util.List;
import java.util.HashMap;
import java.util.Map;

import org.apache.log4j.Logger;

import com.axios.ccdp.message.ThreadRequestMessage;
import com.axios.ccdp.tasking.CcdpThreadRequest;
import com.axios.ccdp.utils.CcdpUtils;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

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
    String fname = "/nishome/oegante/req.json";
    byte[] data = Files.readAllBytes( Paths.get( fname ) );
    String job = new String(data, "utf-8");
    this.logger.debug("Running a Task sender, sending " + job);
    ObjectMapper mapper = new ObjectMapper();
    
    JsonNode node = mapper.readTree( job );
    
    ThreadRequestMessage req = mapper.treeToValue(node, ThreadRequestMessage.class);
    this.logger.debug("Sending " + req.toString() );

  }
  
  
  public static void main( String[] args ) throws Exception
  {
    String cfg_file = System.getProperty("ccdp.config.file");
    CcdpUtils.loadProperties(cfg_file);
    CcdpUtils.configLogger();
    
    new CCDPTest();
  }

}



