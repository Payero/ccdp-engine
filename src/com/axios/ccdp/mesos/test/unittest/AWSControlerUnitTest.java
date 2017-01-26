package com.axios.ccdp.mesos.test.unittest;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.log4j.Logger;
import org.junit.Test;

import com.axios.ccdp.mesos.controllers.aws.AWSCcdpVMControllerImpl;
import com.axios.ccdp.mesos.utils.CcdpUtils;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

public class AWSControlerUnitTest
{

  /**
   * Generates debug print statements based on the verbosity level.
   */
  private Logger logger = Logger
      .getLogger(AWSControlerUnitTest.class.getName());

  AWSCcdpVMControllerImpl aws = null;
  JsonObject jsonCfg = new JsonObject();
  
  
  public AWSControlerUnitTest()
  {
    CcdpUtils.configLogger();
    this.aws = new AWSCcdpVMControllerImpl();
    
    this.jsonCfg.addProperty(AWSCcdpVMControllerImpl.FLD_SECURITY_GRP, "sg-b28aafcf");
    this.jsonCfg.addProperty(AWSCcdpVMControllerImpl.FLD_SUBNET_ID, "subnet-7c6dfc51");
    this.jsonCfg.addProperty(AWSCcdpVMControllerImpl.FLD_KEY_FILE, "aws_serv_server_key");
    
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
    JsonObject json = new JsonObject();
    this.aws.configure(json);
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
    this.logger.debug("Running Test Start Instance");
    this.aws.configure(this.jsonCfg);
    
    
    JsonObject json = new JsonObject();
    json.addProperty("server-id", "1");
    json.addProperty("mesos-type", "SLAVE");
    json.addProperty("session-id", "oeg-1");
    JsonObject master = new JsonObject();
    master.addProperty("server-id", "1");
    master.addProperty("ip-address", "10.0.2.135");
    master.addProperty("port", "23888:3888");
    JsonArray masters = new JsonArray();
    masters.add(master);
    json.add("masters", masters);
    
    
    String user_data = "#!/bin/bash\n\n " +
                       "cd /home/ubuntu\n " +
                       "/home/ubuntu/mesos_config.py '" + json.toString() +"'\n ";
    
    Map<String, String> tags = new HashMap<String, String>();
    tags.put("Name", "Test-1");
    tags.put("SessionId", "Test-Session");
    List<String> launched = this.aws.startInstances("ami-417e6156", 1, 1, 
        tags, user_data);
    
    assert(launched.size() == 1);
  }
  
//  @Test 
  public void testTerminateInstance()
  {
    this.logger.debug("Running Test Stop Instance");
    this.aws.configure(this.jsonCfg);
    
//    List<String> launched = this.aws.startInstances("ami-091f031e", 1, 1, null);
//    assert(launched.size() == 1);
//    this.logger.debug("Waiting 60 seconds before shutting it down");
//
//    CcdpUtils.pause(20);
//    Iterator<String> ids = launched.iterator();
//    while( ids.hasNext() )
//    {
//      String id = ids.next();
//      this.logger.debug("Shutting Down InstanceId: " + id);
//    }
//
//    boolean res = this.aws.terminateInstances(launched);
//    Assert.assertTrue(res);
  }
  
  @Test
  public void testGetAllInstancesStatus()
  {
    this.logger.debug("Testing Getting all instances status");
    this.aws.configure(this.jsonCfg);
    JsonObject items = this.aws.getAllInstanceStatus();
    this.logger.debug("Items: " + items);
    
  }
  
//  @Test
  public void testGetFilteredInstances()
  {
    this.logger.debug("Testing Getting all instances status");
    this.aws.configure(this.jsonCfg);
    JsonObject filter = new JsonObject();
    filter.addProperty("Name", "CCDP-Server");
    JsonObject items = this.aws.getStatusFilteredByTags(filter);
    this.logger.debug("Items: " + items);
  }
  
}
