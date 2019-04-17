package com.axios.ccdp.test;

import java.io.FileInputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.StringTokenizer;
import java.util.UUID;

import org.apache.log4j.Logger;
import org.bson.Document;

import com.axios.ccdp.resources.CcdpVMResource;
import com.axios.ccdp.resources.CcdpVMResource.ResourceStatus;
import com.axios.ccdp.tasking.CcdpTaskRequest;
import com.axios.ccdp.utils.CcdpUtils;
import com.axios.ccdp.utils.LinuxResourceMonitorImpl;
import com.axios.ccdp.utils.CcdpUtils.CcdpNodeType;
import com.axios.ccdp.utils.SystemResourceMonitorAbs.UNITS;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.deser.std.ObjectArrayDeserializer;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mongodb.BasicDBObject;
import com.spotify.docker.client.DefaultDockerClient;
import com.spotify.docker.client.DockerClient;
import com.spotify.docker.client.DockerClient.ListContainersParam;
import com.spotify.docker.client.messages.Container;
import com.spotify.docker.client.messages.ContainerConfig;
import com.spotify.docker.client.messages.ContainerCreation;
import com.spotify.docker.client.messages.ContainerInfo;
import com.spotify.docker.client.messages.HostConfig;



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
    
    CcdpVMResource vm = CCDPTest.getVM();
    //this.logger.debug("Parsing:\n" + vm.toPrettyPrint());
    ObjectMapper mapper = new ObjectMapper();
    Map<String, Object> map = mapper.convertValue(vm.toJSON(), Map.class);
    this.logger.debug("The Map " + map.toString() );
    Document doc = new Document(map);
  }
  
  public void whatNow( JsonNode node, Map map )
  {
    Iterator<String> keys = node.fieldNames();
    while( keys.hasNext() )
    {
      String key = keys.next();
      JsonNode value = node.get(key);
      this.logger.debug("Working with " + key );
      if( value.isObject() )
      {
        this.logger.debug("**************  Found a Container (" + key + "), calling me again   ****");
        this.whatNow( value, map);
      }
      else if( value.isArray() )
      {
        this.logger.debug("**************  Found an array (" + key + "), calling me again   ****");
        Iterator<JsonNode> elements = value.elements();
        while( elements.hasNext() )
        {
          this.whatNow(elements.next(), map);
        }
      }
      else
      {
      }
      
    }
  }
  
  public static CcdpVMResource getVM()
  {
    String uiid = "i-test-cec36adf496e";
    CcdpTaskRequest task = new CcdpTaskRequest();
    task.setTaskId(UUID.randomUUID().toString());
    task.setCPU(0.5);
    task.setHostId("localhost");
    
    CcdpVMResource vm = new CcdpVMResource(uiid);
    vm.setStatus(ResourceStatus.RUNNING);
    vm.setHostname("localhost");
    vm.setInstanceId(uiid);
    vm.setNodeType(CcdpNodeType.DEFAULT);
    vm.setAgentId("myAgent");
    vm.setAssignedSession("DEFAULT");
    vm.setAssignedCPU(0.0);
    vm.setAssignedMEM(0.0);
    vm.setAssignedDisk(0.0);
    vm.setMemLoad(8151.0);
    vm.setSingleTask("localhost");
    vm.isSingleTasked(false);
    vm.setCPULoad(85.35564853556485);
    vm.setMemLoad(31897.0);
    vm.setDisk(932431.0);
    vm.setFreeMemory(23745.0);
    vm.setCPULoad(0.14644351464435146);
    vm.setFreeDiskSpace(909747.0);
    vm.setLastAssignmentTime(1555323695787L);
    vm.setLastUpdatedTime(1555323695787L);
    vm.addTask(task);
    return vm;
  }
  
  public static void main( String[] args ) throws Exception
  {
    String cfg_file = System.getProperty("ccdp.config.file");
    CcdpUtils.loadProperties(cfg_file);
    CcdpUtils.configLogger();
    
    new CCDPTest();
  }

}



