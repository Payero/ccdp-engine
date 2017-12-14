package com.axios.ccdp.test;


import java.net.InetAddress;
import java.net.UnknownHostException;
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
import java.util.Random;
import java.util.UUID;

import org.apache.log4j.Logger;

import com.axios.ccdp.connections.amq.AmqSender;
import com.axios.ccdp.messages.KillTaskMessage;
import com.axios.ccdp.messages.ThreadRequestMessage;
import com.axios.ccdp.tasking.CcdpTaskRequest;
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
    
    long startTime = System.currentTimeMillis();
    double load = 50;
    long secs = 1000;
    
    this.logger.debug("Diff " + (System.currentTimeMillis() - startTime ) );
    long diff = System.currentTimeMillis() - startTime;
    
    // Loop for the given duration
    while (diff < secs) 
    {
      // Every 100ms, sleep for the percentage of unladen time
      if (System.currentTimeMillis() % 100 == 0) 
      {
        Thread.sleep((long) Math.floor((1 - load) * 100));
      }
      diff = System.currentTimeMillis() - startTime;
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



