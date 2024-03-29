package com.axios.ccdp.test.unittest;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.*;

import org.apache.log4j.Logger;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.internal.runners.statements.Fail;

import com.amazonaws.services.ec2.model.AmazonEC2Exception;
import com.axios.ccdp.impl.cloud.aws.AWSCcdpVMControllerImpl;
import com.axios.ccdp.resources.CcdpImageInfo;
import com.axios.ccdp.resources.CcdpVMResource;
import com.axios.ccdp.utils.CcdpUtils;
import com.fasterxml.jackson.databind.JsonNode;
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
  private JsonNode jsonCfg;
  AWSCcdpVMControllerImpl aws = null;
  
  @BeforeClass
  public static void initialize()
  {
    TestHelperUnitTest.initialize();
  }
  
  @Before
  public void AWSControlerUnitTestSetUP()
  {
    System.out.println("******************************************************************");
    CcdpUtils.configLogger();
    this.aws = new AWSCcdpVMControllerImpl();
    this.jsonCfg = this.mapper.createObjectNode();
    String cfg_file = System.getProperty("ccdp.config.file");
    if( cfg_file == null )
    {
      cfg_file = System.getenv("CCDP_HOME") + "/config/ccdp-config.json";
    }
    this.logger.debug("The config file: " + cfg_file);
    if( cfg_file != null )
    {
      try
      {
        CcdpUtils.loadProperties(cfg_file);
        
        this.jsonCfg = CcdpUtils.getResourceCfg("EC2"); 
      }
      catch( Exception e )
      {
        e.printStackTrace();
      }
    }
    
    this.logger.debug("Running, using config file " + cfg_file);
  }
  /*public AWSControlerUnitTest()
  {
    JUnitTestHelper.initialize();
    CcdpUtils.configLogger();
    this.aws = new AWSCcdpVMControllerImpl();
    this.jsonCfg = this.mapper.createObjectNode();
    String cfg_file = System.getProperty("ccdp.config.file");
    this.logger.debug("The config file: " + cfg_file);
    if( cfg_file != null )
    {
      try
      {
        CcdpUtils.loadProperties(cfg_file);
        
        this.jsonCfg = CcdpUtils.getResourceCfg("EC2"); 
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
    
  }*/
  
  //@Test
  public void testThisIsATest()
  {
    assertEquals(20, 20);
  }
  
  @Test(expected = IllegalArgumentException.class)
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

  @Test
  public void testStartInstance()
  {
    this.logger.debug("Running Test Start Instance using " + this.jsonCfg);
    this.aws.configure(this.jsonCfg);
    
    boolean inclusive = false;
    CcdpImageInfo 
    imgCfg = CcdpUtils.getImageInfo("EC2");
    imgCfg.setMinReq(1);

    List<String> launched = new ArrayList<>();
    if( inclusive )
    {
      String user_data = "#!/bin/bash "
          + "/data/ccdp_env.py -a download -i\n";
      imgCfg.setStartupCommand(user_data);
      Map<String, String> tags = new HashMap<String, String>();
      tags.put("Name", "Test-1");
      tags.put("SessionId", "Test-Session");
      imgCfg.setTags(tags);
      launched = this.aws.startInstances(imgCfg);
    }
    else
    {
      launched = this.aws.startInstances(imgCfg);
    }
    
    assert(launched.size() == 1);
  }
  
  /*
   * To get this test to pass, you may need to copy a running ID from AWS Console/EC2 Dashboard
   */
  @Test 
  public void testTerminateInstance()
  {
    this.logger.debug("Running Test Stop Instance");
    this.aws.configure(this.jsonCfg);
    List<String> ids = new ArrayList<>();
    ids.add("i-0252e383ba4f98e01-MOCK");
    try
    {
      this.aws.terminateInstances(ids);
      fail("The made up instance Id should have failed when terminating");
    }
    catch( AmazonEC2Exception e )
    {
      this.logger.debug("Got an exception, good");
    }
  }
  
  @Test
  public void testGetAllInstancesStatus()
  {
    this.logger.debug("Testing Getting all instances status");
    this.aws.configure(this.jsonCfg);
    List<CcdpVMResource> items = this.aws.getAllInstanceStatus();
    for(CcdpVMResource vm : items )
    {
      this.logger.debug("Instance[" + vm.toString() );
    }
  }
  
  @Test
  public void testGetFilteredInstances()
  {
    this.logger.debug("Testing Getting all instances status");
    this.aws.configure(this.jsonCfg);
    ObjectNode filter = this.mapper.createObjectNode();
    filter.put("instance-id", "i-0146423181872f36f");
    List<CcdpVMResource> items = this.aws.getStatusFilteredByTags(filter);
    
    for(CcdpVMResource vm : items )
    {
      this.logger.debug("Instance[" + vm.toString() );
    }
    
  }
  
  @Test
  public void testGetFilteredInstancesById()
  {
    this.logger.debug("Testing Getting Instance by Id");
    this.aws.configure(this.jsonCfg);
    CcdpVMResource node = this.aws.getStatusFilteredById("i-01fb4aae366fa2114");
    this.logger.debug("Items: " + node);
  }
  
  @Test
  public void startAndTerminateInstances() throws Exception
  {
    // Hard code Brecky and Host-Agent to be sure to not terminate
    //String Brecky = "i-0fa470f3da73d8ac0";
    //String HostAgent = "i-07bbd73987fb47b15";
    
    List<String> newInstances = new ArrayList<>();
    
    this.aws.configure(this.jsonCfg);
    CcdpImageInfo 
    imgCfg = CcdpUtils.getImageInfo("EC2");
    imgCfg.setMinReq(1);
    
    // Start a new instances
    try 
    {
        newInstances = this.aws.startInstances(imgCfg);
    }
    catch ( Exception e )
    {
      e.printStackTrace();
    }
    
    TimeUnit.SECONDS.sleep(15);
    
    this.aws.getAllInstanceStatus();
    
    TimeUnit.SECONDS.sleep(15);
    
    try
    {
      this.aws.terminateInstances(newInstances);
    }
    catch ( Exception e )
    {
      e.printStackTrace();
    }
  }
}
