package com.axios.ccdp.test.unittest.sim;

import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.apache.log4j.Logger;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import com.axios.ccdp.factory.CcdpObjectFactory;
import com.axios.ccdp.impl.cloud.sim.SimCcdpVMControllerImpl;
import com.axios.ccdp.intfs.CcdpConnectionIntf;
import com.axios.ccdp.intfs.CcdpMessageConsumerIntf;
import com.axios.ccdp.messages.CcdpMessage;
import com.axios.ccdp.messages.ResourceUpdateMessage;
import com.axios.ccdp.messages.RunTaskMessage;
import com.axios.ccdp.messages.TaskUpdateMessage;
import com.axios.ccdp.messages.CcdpMessage.CcdpMessageType;
import com.axios.ccdp.resources.CcdpImageInfo;
import com.axios.ccdp.resources.CcdpVMResource;
import com.axios.ccdp.resources.CcdpVMResource.ResourceStatus;
import com.axios.ccdp.tasking.CcdpTaskRequest;
import com.axios.ccdp.test.unittest.TestHelperUnitTest;
import com.axios.ccdp.utils.CcdpUtils;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

public class SimVMControllerUnitTest implements CcdpMessageConsumerIntf
{

  /**
   * Generates debug print statements based on the verbosity level.
   */
  private Logger logger = 
      Logger.getLogger(SimVMControllerUnitTest.class.getName());
  /**
   * Object used to send and receive messages 
   */
  private CcdpConnectionIntf connection;
  /**
   * Stores all incoming messages other than heartbeats
   */
  private List<CcdpMessage> messages = null;
  /**
   * Stores all incoming heartbeat messages
   */
  private List<CcdpMessage> heartbeats = null;
  /**
   * The actual object to test
   */
  private SimCcdpVMControllerImpl controller = null;
  
  public SimVMControllerUnitTest()
  {
    this.logger.debug("Initializing Controller Mock Unit Test");
  }
  
  /**
   * Runs before any of the methods so is used to initialize all the objects
   * such as connections and configuration properties
   */
  @BeforeClass
  public static void initialize()
  {
    TestHelperUnitTest.initialize();
  }
  
  /**
   * This method gets invoke before every test and can be used to do some
   * cleaning before running the tests.  It makes sure the CCDP_HOME environment
   * variable is set properly
   */
  @Before
  public void setUpTest()
  {
    System.out.println("****************************************************************************** \n");
    this.controller = new SimCcdpVMControllerImpl();
    this.messages = new ArrayList<>();
    this.heartbeats = new ArrayList<>();
    
    // creating the factory that generates the objects used by the agent
    CcdpObjectFactory factory = CcdpObjectFactory.newInstance();
    JsonNode task_msg_node = CcdpUtils.getConnnectionIntfCfg();
    
    this.connection = factory.getCcdpConnectionInterface(task_msg_node);
    this.connection.configure(task_msg_node);
    this.connection.setConsumer(this);
    this.logger.debug("Done with the connections: " + task_msg_node.toString());
    
    assertNotNull("Could not setup a connection with broker", this.connection);
    String uuid = UUID.randomUUID().toString();
    String channel = 
        task_msg_node.get( CcdpUtils.CFG_KEY_MAIN_CHANNEL ).asText();
    assertNotNull("The Main Channel cannot be null", channel);
    this.connection.registerConsumer(uuid, channel);
    
  }
  
  /**
   * Tests the ability to start an instance and make sure is only one
   */
  @Test
  public void startSingleInstanceTest()
  {
    CcdpImageInfo imgInf = CcdpUtils.getImageInfo("EC2");
    assertNotNull("Could not find Image information", imgInf);
    CcdpImageInfo image = CcdpImageInfo.copyImageInfo(imgInf);
    assertNotNull("Could not find Image information", image);
    image.setMinReq(1);
    assertTrue("The minimum should be ", image.getMinReq() == 1);
    
    List<String> vms = this.controller.startInstances(image);
    assertTrue("Wrong number of instances", vms.size() == 1);
    this.logger.debug(this.controller.getStatusSummary());
  }
  
  
  /**
   * Tests the ability to start multiple instances using a single request
   */
  @Test
  public void startMultipleInstancesTest()
  {
    CcdpImageInfo imgInf = CcdpUtils.getImageInfo("EC2");
    assertNotNull("Could not find Image information", imgInf);
    CcdpImageInfo image = CcdpImageInfo.copyImageInfo(imgInf);
    assertNotNull("Could not find Image information", image);
    image.setMinReq(3);
    image.setSessionId("test-session");
    assertTrue("The minimum should be ", image.getMinReq() == 3);
    
    List<String> vms = this.controller.startInstances(image);
    assertTrue("Wrong number of instances", vms.size() == 3);
    this.logger.debug(this.controller.getStatusSummary());
  }
  
  /**
   * Tests the ability to start an instance with a specific Session ID
   */
  @Test
  public void startInstanceWithSessionIdTest()
  {
    CcdpImageInfo imgInf = CcdpUtils.getImageInfo("EC2");
    assertNotNull("Could not find Image information", imgInf);
    CcdpImageInfo image = CcdpImageInfo.copyImageInfo(imgInf);
    assertNotNull("Could not find Image information", image);
    image.setMinReq(1);
    image.setSessionId("test-session");
    assertTrue("The minimum should be ", image.getMinReq() == 1);
    
    List<String> ids = this.controller.startInstances(image);
    assertTrue("Wrong number of instances", ids.size() == 1);
    List<CcdpVMResource> vms = this.controller.getAllInstanceStatus();
    CcdpVMResource vm = vms.get(0);
    assertTrue("The Session ID is different", "test-session".equals(vm.getAssignedSession()));
    this.logger.debug(this.controller.getStatusSummary());
  }

  /**
   * Tests the ability to start an instance without a specific Session ID
   */
  @Test
  public void startInstanceWithoutSessionIdTest()
  {
    CcdpImageInfo imgInf = CcdpUtils.getImageInfo("EC2");
    assertNotNull("Could not find Image information", imgInf);
    CcdpImageInfo image = CcdpImageInfo.copyImageInfo(imgInf);
    assertNotNull("Could not find Image information", image);
    image.setMinReq(1);
    assertTrue("The minimum should be ", image.getMinReq() == 1);
    
    List<String> ids = this.controller.startInstances(image);
    assertTrue("Wrong number of instances", ids.size() == 1);
    List<CcdpVMResource> vms = this.controller.getAllInstanceStatus();
    CcdpVMResource vm = vms.get(0);
    assertTrue("The Session ID is different", "EC2".equals(vm.getAssignedSession()));
    this.logger.debug(this.controller.getStatusSummary());
  }
  
  /**
   * Tests the ability to start and stop an instance
   */
  @Test
  public void startAndStopInstanceTest()
  {
    CcdpImageInfo imgInf = CcdpUtils.getImageInfo("EC2");
    assertNotNull("Could not find Image information", imgInf);
    CcdpImageInfo image = CcdpImageInfo.copyImageInfo(imgInf);
    assertNotNull("Could not find Image information", image);
    image.setMinReq(1);
    assertTrue("The minimum should be ", image.getMinReq() == 1);
    
    List<String> ids = this.controller.startInstances(image);
    assertTrue("Wrong number of instances", ids.size() == 1);
    List<CcdpVMResource> vms = this.controller.getAllInstanceStatus();
    CcdpVMResource vm = vms.get(0);
    assertTrue("The Session ID is different", "EC2".equals(vm.getAssignedSession()));
    
    this.controller.stopInstances(ids);
    for( CcdpVMResource res : vms )
    {
      String iid = res.getInstanceId();
      ResourceStatus status = this.controller.getInstanceState(iid);
      assertNotNull("Could not find Resource " + iid, status);
      this.logger.debug("VM Status " + status);
      assertTrue("The VM is not stopped", status.equals(ResourceStatus.STOPPED));
    }
    this.logger.debug(this.controller.getStatusSummary());
  }
  
  
  /**
   * Tests the ability to start multiple instances and stopping just one
   */
  @Test
  public void startManyAndStopSingleInstanceTest()
  {
    CcdpImageInfo imgInf = CcdpUtils.getImageInfo("NIFI");
    assertNotNull("Could not find Image information", imgInf);
    CcdpImageInfo image = CcdpImageInfo.copyImageInfo(imgInf);
    assertNotNull("Could not find Image information", image);
    image.setMinReq(3);
    assertTrue("The minimum should be ", image.getMinReq() == 3);
    
    List<String> ids = this.controller.startInstances(image);
    assertTrue("Wrong number of instances", ids.size() == 3);
    List<CcdpVMResource> vms = this.controller.getAllInstanceStatus();
    
    CcdpVMResource vm = vms.get(1);
    String testId = vm.getInstanceId();
    assertTrue("The Session ID is different", "NIFI".equals(vm.getAssignedSession()));
    
    List<String> stopIds = new ArrayList<>();
    stopIds.add(vm.getInstanceId());
    
    this.controller.stopInstances(stopIds);
    for( CcdpVMResource res : vms )
    {
      String iid = res.getInstanceId();
      ResourceStatus status = this.controller.getInstanceState(iid);
      assertNotNull("Could not find Resource " + iid, status);
      this.logger.debug("VM Status " + status);
      if( testId.equals(iid) )
        assertTrue("The VM is not stopped", status.equals(ResourceStatus.STOPPED));
      else
        assertTrue("The VM is not running", status.equals(ResourceStatus.RUNNING));
      
    }
    this.logger.debug(this.controller.getStatusSummary());
  }
  
  
  /**
   * Tests the ability to start multiple instances and stopping just one
   */
  @Test
  public void checksTasksRunningOnVMTest()
  {
    CcdpImageInfo imgInf = CcdpUtils.getImageInfo("NIFI");
    assertNotNull("Could not find Image information", imgInf);
    CcdpImageInfo image = CcdpImageInfo.copyImageInfo(imgInf);
    assertNotNull("Could not find Image information", image);
    image.setMinReq(3);
    assertTrue("The minimum should be ", image.getMinReq() == 3);
    
    List<String> ids = this.controller.startInstances(image);
    assertTrue("Wrong number of instances", ids.size() == 3);
    List<CcdpVMResource> vms = this.controller.getAllInstanceStatus();
    
    CcdpVMResource vm = vms.get(1);
    String testId = vm.getInstanceId();
    assertTrue("The Session ID is different", "NIFI".equals(vm.getAssignedSession()));
    
    CcdpTaskRequest task1 = this.sendTaskRequest(testId, "MOCK_PAUSE", 5);
    CcdpTaskRequest task2 = this.sendTaskRequest(testId, "MOCK_PAUSE", 5);
    CcdpUtils.pause(0.2);
    vms = this.controller.getAllInstanceStatus();
    
    for( CcdpVMResource res : vms )
    {
      String iid = res.getInstanceId();
      ResourceStatus status = this.controller.getInstanceState(iid);
      assertNotNull("Could not find Resource " + iid, status);
      this.logger.debug("VM Status " + status);
      assertTrue("The VM is not stopped", status.equals(ResourceStatus.RUNNING));
      if( testId.equals(iid) )
      {
        List<CcdpTaskRequest> tasks = res.getTasks();
        assertTrue("Did not find task", tasks.size() == 2);
        for( CcdpTaskRequest task : tasks )
        {
          
          String tid = task.getTaskId();
          if( !tid.equals(task1.getTaskId() ) && !tid.equals(task2.getTaskId()) )
            fail("The running Task does not match");
        }
      }
    }
    this.logger.debug(this.controller.getStatusSummary());
  }
  
  
  /**
   * Tests the ability of getting the instance id of a non-existing instance, 
   * a request with null, and a valid instance id
   */
  @Test
  public void getInstanceStateTest()
  {
    ResourceStatus state = this.controller.getInstanceState(null);
    assertNull(state);
    
    state = this.controller.getInstanceState("my-bogus-id");
    assertNull(state);
    
    CcdpImageInfo imgInf = CcdpUtils.getImageInfo("EC2");
    assertNotNull("Could not find Image information", imgInf);
    CcdpImageInfo image = CcdpImageInfo.copyImageInfo(imgInf);
    assertNotNull("Could not find Image information", image);
    image.setMinReq(1);
    assertTrue("The minimum should be ", image.getMinReq() == 1);
    
    List<String> vms = this.controller.startInstances(image);
    assertTrue("Wrong number of instances", vms.size() == 1);
    
    state = this.controller.getInstanceState(vms.get(0));
    assertNotNull("The state came back null", state);
    
  }
  
  
  /**
   * Tests the ability to retrieve Status of the Remote resources based on the
   * tags associated with that server
   */
  @Test
  public void getStatusFilteredByTagsTest()
  {
    List<String> iids = new ArrayList<>();
    Map<String, String> tags = new HashMap<>();
    tags.put("Name", "Server-One");
    tags.put("Group", "Test");
    
    this.logger.debug("Creating the first instance");
    CcdpImageInfo imgInf = CcdpUtils.getImageInfo("EC2");
    assertNotNull("Could not find Image information", imgInf);
    CcdpImageInfo image = CcdpImageInfo.copyImageInfo(imgInf);
    assertNotNull("Could not find Image information", image);
    image.setMinReq(1);
    assertTrue("The minimum should be ", image.getMinReq() == 1);

    image.setTags(tags);
    
    iids.add( this.controller.startInstances(image).get(0) );
    
    assertTrue("Should have only one instance", this.controller.getAllInstanceStatus().size() == 1);
    this.logger.debug("Creating the Second Instance");
    Map<String, String> tags2 = new HashMap<>();
    tags2.put("Name", "Server-Two");
    tags2.put("Group", "Test");
    
    assertNotNull("Could not find Image information", imgInf);
    image = CcdpImageInfo.copyImageInfo(imgInf);
    assertNotNull("Could not find Image information", image);
    image.setMinReq(1);
    assertTrue("The minimum should be ", image.getMinReq() == 1);

    image.setTags(tags2);
    
    iids.add( this.controller.startInstances(image).get(0) );
    assertTrue("Should have only two instance", this.controller.getAllInstanceStatus().size() == 2);
    
    this.logger.debug("Creating the Third Instance");
    Map<String, String> tags3 = new HashMap<>();
    tags3.put("Name", "Server-Three");
    tags3.put("Group", "Other-Test");
    
    assertNotNull("Could not find Image information", imgInf);
    image = CcdpImageInfo.copyImageInfo(imgInf);
    assertNotNull("Could not find Image information", image);
    image.setMinReq(1);
    assertTrue("The minimum should be ", image.getMinReq() == 1);

    image.setTags(tags3);
    
    iids.add( this.controller.startInstances(image).get(0) );
    assertTrue("Should have only three instance", this.controller.getAllInstanceStatus().size() == 3);
    
    List<CcdpVMResource> res = this.controller.getAllInstanceStatus();
    for(CcdpVMResource vm : res )
    {
      this.logger.debug(vm.toPrettyPrint() );
      this.logger.debug("----------------------------------------------------");
      
    }
    
    this.logger.debug("Now the actual tests");
    List<CcdpVMResource> vms = null;
    assertTrue("Have more than three instances", iids.size() == 3);
    ObjectMapper mapper = new ObjectMapper();
    ObjectNode node = mapper.createObjectNode();
    node.put("Name", "Server-Two");
    vms = this.controller.getStatusFilteredByTags(node);
    assertNotNull("The results should not be null", vms);
    assertTrue("The Second Server should have been found", vms.size() == 1);
    
    node = mapper.createObjectNode();
    node.put("Group", "Test");
    
    vms = this.controller.getStatusFilteredByTags(node);
    assertNotNull("The results should not be null", vms);
    assertTrue("Should have found two servers", vms.size() == 2);
    
    vms = this.controller.getStatusFilteredByTags(null);
    assertNotNull("The results should not be null", vms);
    assertTrue("Should be empty", vms.size() == 0);
    
    node = mapper.createObjectNode();
    vms = this.controller.getStatusFilteredByTags(null);
    assertNotNull("The results should not be null", vms);
    assertTrue("Should be empty", vms.size() == 0);
    
  }
  
  /**
   * Tests the ability to get a single instance based on the id.
   */
  @Test
  public void getStatusByIdTest()
  {
    CcdpImageInfo imgInf = CcdpUtils.getImageInfo("EC2");
    assertNotNull("Could not find Image information", imgInf);
    CcdpImageInfo image = CcdpImageInfo.copyImageInfo(imgInf);
    assertNotNull("Could not find Image information", image);
    image.setMinReq(5);
    assertTrue("The minimum should be ", image.getMinReq() == 5);

    List<String> iids = this.controller.startInstances(image);
    assertEquals("Shoud have five instances", iids.size(), 5);
    
    String id = iids.get(1);
    CcdpVMResource vm = this.controller.getStatusFilteredById(id);
    assertNotNull("Should not be null", vm);
    assertEquals("Should be the same VM", id, vm.getInstanceId());
    
    vm = this.controller.getStatusFilteredById(null);
    assertNull("ID passed was null so it should be null", vm);
    
    vm = this.controller.getStatusFilteredById("bogus-id");
    assertNull("Invalid id so it should be null", vm);
  }
  
  
  /***************************************************************************/
  /**                            Helper Classes                             **/
  /***************************************************************************/
  
  /**
   * Sends a task message to the intended VM.  
   * 
   * @param action what to do either paused, cpu, or
   * @param time for how long
   * 
   * @return the task that was sent
   */
  private CcdpTaskRequest sendTaskRequest(String iid, String action, long time)
  {
    assertNotNull("The Instance ID cannot be null", iid);
    
    CcdpTaskRequest task = new CcdpTaskRequest();
    
    task.setSessionId("test-session");
    List<String> cmd = new ArrayList<>();
    cmd.add(action);
    cmd.add(Long.toString(time));
    task.setCommand(cmd);
    RunTaskMessage msg = new RunTaskMessage();
    msg.setTask(task);
    
    this.connection.sendCcdpMessage(iid, msg);
    return task;
  }
  
  /**
   * Receives all the messages from the VM.  If the message is a heartbeat 
   * (ResourceUpdateMessage) then is stored in the heartbeats list otherwise
   * is stored in the messages list
   * 
   * @param message the message sent to the unit test object
   */
  public void onCcdpMessage( CcdpMessage message )
  {
    CcdpMessageType msgType = CcdpMessageType.get( message.getMessageType() );
    this.logger.trace("Got a new Message: " + msgType.toString());
    switch( msgType )
    {
      case RESOURCE_UPDATE:
        ResourceUpdateMessage msg = (ResourceUpdateMessage)message;
        this.heartbeats.add(msg);
        break;
      case TASK_UPDATE:
        CcdpTaskRequest task = ((TaskUpdateMessage)message).getTask();
        String tid = task.getTaskId();
        CcdpTaskRequest.CcdpTaskState state = task.getState();
        this.logger.debug(tid + " Updated task to " + state.toString());
      default:
        this.messages.add(message);
    }
  }
  
  /**
   * This method is invoked after every test and can be used to do some 
   * cleaning after running the tests
   */
  @After
  public void tearDownTest()
  {
    this.logger.debug("Cleaning up after the test");
    this.connection.disconnect();
    this.messages.clear();
    this.messages = null;
  }
  
  /**
   * Runs once at the end of the unit testing and can be used to close all the 
   * connections open during initialization
   */
  @AfterClass
  public static void terminate()
  {
    
  }
  
}
