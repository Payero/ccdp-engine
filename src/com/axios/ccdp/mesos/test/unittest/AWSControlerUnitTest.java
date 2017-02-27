package com.axios.ccdp.mesos.test.unittest;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.log4j.Logger;
import org.junit.Test;

import com.axios.ccdp.mesos.controllers.aws.AWSCcdpVMControllerImpl;
import com.axios.ccdp.mesos.utils.CcdpUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

public class AWSControlerUnitTest
{

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
    
    this.jsonCfg.put(AWSCcdpVMControllerImpl.FLD_SECURITY_GRP, "sg-b28aafcf");
    this.jsonCfg.put(AWSCcdpVMControllerImpl.FLD_SUBNET_ID, "subnet-7c6dfc51");
    this.jsonCfg.put(AWSCcdpVMControllerImpl.FLD_KEY_FILE, "aws_serv_server_key");
    
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
    this.logger.debug("Running Test Start Instance");
    this.aws.configure(this.jsonCfg);
    
    ObjectNode json = this.mapper.createObjectNode();
    json.put("server-id", "1");
    json.put("mesos-type", "SLAVE");
    json.put("session-id", "oeg-1");
    
    ObjectNode master = this.mapper.createObjectNode();
    master.put("server-id", "1");
    master.put("ip-address", "10.0.2.135");
    master.put("port", "23888:3888");
    
    ArrayNode masters = this.mapper.createArrayNode();
    masters.add(master);
    json.set("masters", masters);
    
    
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
  
  @Test
  public void testGetFilteredInstancesById()
  {
    this.logger.debug("Testing Getting Instance by Id");
    this.aws.configure(this.jsonCfg);
    ObjectNode node = this.aws.getStatusFilteredById("i-0146423181872f36f");
    this.logger.debug("Items: " + node);
  }
  
}
