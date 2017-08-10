package com.axios.ccdp.test;


import java.nio.file.Files;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Enumeration;
import java.util.List;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import org.apache.log4j.Logger;

import com.axios.ccdp.message.ThreadRequestMessage;
import com.axios.ccdp.tasking.CcdpThreadRequest;
import com.axios.ccdp.utils.CcdpImageInfo;
import com.axios.ccdp.utils.CcdpUtils;
import com.axios.ccdp.utils.CcdpUtils.CcdpNodeType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

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
    CcdpImageInfo imgDef = CcdpUtils.getImageInfo(CcdpNodeType.DEFAULT);
    
    this.logger.debug("The Original " + imgDef.toString());
    
    ObjectNode node = imgDef.toJSON();
    ObjectMapper mapper = new ObjectMapper();
    
    CcdpImageInfo imgCpy = mapper.treeToValue(node, CcdpImageInfo.class);
    imgCpy.setMinReq(2);
    imgCpy.setMaxReq(3);
    imgCpy.setSessionId("my-session");
    Map<String, String> tags = imgCpy.getTags();
    Iterator<String> keys = tags.keySet().iterator();
    while( keys.hasNext() )
    {
      String key = keys.next();
      tags.put(key, tags.get(key) + "-modified");
    }
    this.logger.debug("The Copy     " + imgCpy.toString());
    this.logger.debug("The Original " + imgDef.toString());
    
    CcdpImageInfo imgCpy2 = mapper.treeToValue(imgCpy.toJSON(), CcdpImageInfo.class);
    this.logger.debug("The second   " + imgCpy2.toString());
  }
  
  
  public static void main( String[] args ) throws Exception
  {
    String cfg_file = System.getProperty("ccdp.config.file");
    CcdpUtils.loadProperties(cfg_file);
    CcdpUtils.configLogger();
    
    new CCDPTest();
  }

}



