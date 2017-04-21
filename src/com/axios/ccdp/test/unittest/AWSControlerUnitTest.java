package com.axios.ccdp.test.unittest;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.log4j.Logger;
import org.junit.Test;

import com.axios.ccdp.controllers.aws.AWSCcdpVMControllerImpl;
import com.axios.ccdp.utils.CcdpUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

public class AWSControlerUnitTest
{
//  private static final String IMAGE_ID = "ami-8077fc96";

  /**
   * Generates debug print statements based on the verbosity level.
   */
  private Logger logger = Logger
      .getLogger(AWSControlerUnitTest.class.getName());
  
  private ObjectMapper mapper = new ObjectMapper();
  private ObjectNode jsonCfg;
  AWSCcdpVMControllerImpl aws = null;
  
  
  public AWSControlerUnitTest()
  {
    CcdpUtils.configLogger();
    this.aws = new AWSCcdpVMControllerImpl();
    this.jsonCfg = this.mapper.createObjectNode();
    String cfg_file = System.getProperty("ccdp.config.file");
    this.logger.debug("The config file " + cfg_file);
    if( cfg_file != null )
    {
      try
      {
        CcdpUtils.loadProperties(cfg_file);
        this.jsonCfg = 
            CcdpUtils.getJsonKeysByFilter(CcdpUtils.CFG_KEY_RESOURCE);
      }
      catch( Exception e )
      {
        e.printStackTrace();
      }
    }
    
    this.logger.debug("Running");
    
    
    
    
//    this.jsonCfg.put(AWSCcdpVMControllerImpl.FLD_SECURITY_GRP, "sg-54410d2f");
//    this.jsonCfg.put(AWSCcdpVMControllerImpl.FLD_SUBNET_ID, "subnet-d7008b8f");
//    this.jsonCfg.put(AWSCcdpVMControllerImpl.FLD_KEY_FILE, "aws_serv_server_key");
    
  }
  @Test
  public void testThisIsATest()
  {
    org.junit.Assert.assertEquals(20, 20);
  }
  
  //@Test(expected = IllegalArgumentException.class)
  public void testConfigNull()
  {
    this.logger.debug("Testing a null JSON Object");
    this.aws.configure(null);
  }
  
  //@Test(expected = IllegalArgumentException.class)
  public void testConfigFail()
  {
    this.logger.debug("Testing a field missing JSON Object");
    
    this.aws.configure( this.mapper.createObjectNode() );
  }
  
  //@Test
  public void testConfigPass()
  {
    this.logger.debug("Testing a valid credentials creation");
    this.aws.configure(this.jsonCfg);
  }
  
  //@Test
  public void testStartInstance()
  {
    this.logger.debug("Running Test Start Instance using " + this.jsonCfg);
    this.aws.configure(this.jsonCfg);
    
    boolean inclusive = false;
    
    List<String> launched = new ArrayList<>();
    if( inclusive )
    {
      String img_id = 
          this.jsonCfg.get(AWSCcdpVMControllerImpl.FLD_IMAGE_ID).asText();
      
      String user_data = "#!/bin/bash\n\n "
          + "/data/ccdp_env.py -a download -i\n";
      
      Map<String, String> tags = new HashMap<String, String>();
      tags.put("Name", "Test-1");
      tags.put("SessionId", "Test-Session");
          
      launched = this.aws.startInstances(img_id, 1, 1, tags);
    }
    else
    {
      launched = this.aws.startInstances(1, 1);
    }
    
    assert(launched.size() == 1);
  }
  
  //@Test 
  public void testTerminateInstance()
  {
    this.logger.debug("Running Test Stop Instance");
    this.aws.configure(this.jsonCfg);
    List<String> ids = new ArrayList<>();
    ids.add("i-0299eb42ecdb10143");
    
    this.aws.terminateInstances(ids);

  }
  
  //@Test
  public void testGetAllInstancesStatus()
  {
    this.logger.debug("Testing Getting all instances status");
    this.aws.configure(this.jsonCfg);
    ObjectNode items = this.aws.getAllInstanceStatus();
    this.logger.debug("Items: " + items);
    
  }
  
  //@Test
  public void testGetFilteredInstances()
  {
    this.logger.debug("Testing Getting all instances status");
    this.aws.configure(this.jsonCfg);
    ObjectNode filter = this.mapper.createObjectNode();
    filter.put(CcdpUtils.KEY_INSTANCE_ID, "i-0146423181872f36f");
    ObjectNode items = this.aws.getStatusFilteredByTags(filter);
    this.logger.debug("Items: " + items);
  }
  
  //@Test
  public void testGetFilteredInstancesById()
  {
    this.logger.debug("Testing Getting Instance by Id");
    this.aws.configure(this.jsonCfg);
    ObjectNode node = this.aws.getStatusFilteredById("i-01fb4aae366fa2114");
    this.logger.debug("Items: " + node);
  }
  
}
