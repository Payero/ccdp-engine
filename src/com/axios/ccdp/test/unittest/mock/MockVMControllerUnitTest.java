package com.axios.ccdp.test.unittest.mock;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.apache.log4j.Logger;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;

import com.axios.ccdp.cloud.mock.MockCcdpVMControllerImpl;
import com.axios.ccdp.connections.intfs.CcdpConnectionIntf;
import com.axios.ccdp.connections.intfs.CcdpMessageConsumerIntf;
import com.axios.ccdp.factory.CcdpObjectFactory;
import com.axios.ccdp.messages.CcdpMessage;
import com.axios.ccdp.messages.ResourceUpdateMessage;
import com.axios.ccdp.messages.RunTaskMessage;
import com.axios.ccdp.messages.TaskUpdateMessage;
import com.axios.ccdp.messages.CcdpMessage.CcdpMessageType;
import com.axios.ccdp.resources.CcdpVMResource;
import com.axios.ccdp.resources.CcdpVMResource.ResourceStatus;
import com.axios.ccdp.tasking.CcdpTaskRequest;
import com.axios.ccdp.test.unittest.JUnitTestHelper;
import com.axios.ccdp.utils.CcdpImageInfo;
import com.axios.ccdp.utils.CcdpUtils;
import com.axios.ccdp.utils.CcdpUtils.CcdpNodeType;
import com.fasterxml.jackson.databind.node.ObjectNode;

public class MockVMControllerUnitTest implements CcdpMessageConsumerIntf
{

  /**
   * Generates debug print statements based on the verbosity level.
   */
  private Logger logger = 
      Logger.getLogger(MockVMControllerUnitTest.class.getName());
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
  private MockCcdpVMControllerImpl controller = null;
  
  public MockVMControllerUnitTest()
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
    JUnitTestHelper.initialize();
  }
  
  /**
   * This method gets invoke before every test and can be used to do some
   * cleaning before running the tests.  It makes sure the CCDP_HOME environment
   * variable is set properly
   */
  @Before
  public void setUpTest()
  {
    this.controller = new MockCcdpVMControllerImpl();
    this.messages = new ArrayList<>();
    this.heartbeats = new ArrayList<>();
    
    // creating the factory that generates the objects used by the agent
    CcdpObjectFactory factory = CcdpObjectFactory.newInstance();
    ObjectNode task_msg_node = 
        CcdpUtils.getJsonKeysByFilter(CcdpUtils.CFG_KEY_CONN_INTF);
    
    this.connection = factory.getCcdpConnectionInterface(task_msg_node);
    this.connection.configure(task_msg_node);
    this.connection.setConsumer(this);
    this.logger.debug("Done with the connections: " + task_msg_node.toString());
    
    assertNotNull("Could not setup a connection with broker", this.connection);
    String uuid = UUID.randomUUID().toString();
    String channel = CcdpUtils.getProperty(CcdpUtils.CFG_KEY_MAIN_CHANNEL);
    assertNotNull("The Main Channel cannot be null", channel);
    this.connection.registerConsumer(uuid, channel);
    
  }
  
  /**
   * Tests the ability to start an instance and make sure is only one
   */
  @Ignore
  public void startSingleInstanceTest()
  {
    CcdpImageInfo imgInf = CcdpUtils.getImageInfo(CcdpNodeType.EC2);
    assertNotNull("Could not find Image information", imgInf);
    CcdpImageInfo image = CcdpImageInfo.copyImageInfo(imgInf);
    assertNotNull("Could not find Image information", image);
    image.setMinReq(1);
    image.setMaxReq(1);
    assertTrue("The minimum should be ", image.getMinReq() == 1);
    assertTrue("The maximum should be ", image.getMaxReq() == 1);
    
    List<String> vms = this.controller.startInstances(image);
    assertTrue("Wrong number of instances", vms.size() == 1);
    this.logger.debug(this.controller.getStatusSummary());
  }
  
  
  /**
   * Tests the ability to start multiple instances using a single request
   */
  @Ignore
  public void startMultipleInstancesTest()
  {
    CcdpImageInfo imgInf = CcdpUtils.getImageInfo(CcdpNodeType.EC2);
    assertNotNull("Could not find Image information", imgInf);
    CcdpImageInfo image = CcdpImageInfo.copyImageInfo(imgInf);
    assertNotNull("Could not find Image information", image);
    image.setMinReq(1);
    image.setMaxReq(3);
    image.setSessionId("test-session");
    assertTrue("The minimum should be ", image.getMinReq() == 1);
    assertTrue("The maximum should be ", image.getMaxReq() == 3);
    
    List<String> vms = this.controller.startInstances(image);
    assertTrue("Wrong number of instances", vms.size() == 3);
    this.logger.debug(this.controller.getStatusSummary());
  }
  
  /**
   * Tests the ability to start an instance with a specific Session ID
   */
  @Ignore
  public void startInstanceWithSessionIdTest()
  {
    CcdpImageInfo imgInf = CcdpUtils.getImageInfo(CcdpNodeType.EC2);
    assertNotNull("Could not find Image information", imgInf);
    CcdpImageInfo image = CcdpImageInfo.copyImageInfo(imgInf);
    assertNotNull("Could not find Image information", image);
    image.setMinReq(1);
    image.setMaxReq(1);
    image.setSessionId("test-session");
    assertTrue("The minimum should be ", image.getMinReq() == 1);
    assertTrue("The maximum should be ", image.getMaxReq() == 1);
    
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
  @Ignore
  public void startInstanceWithoutSessionIdTest()
  {
    CcdpImageInfo imgInf = CcdpUtils.getImageInfo(CcdpNodeType.EC2);
    assertNotNull("Could not find Image information", imgInf);
    CcdpImageInfo image = CcdpImageInfo.copyImageInfo(imgInf);
    assertNotNull("Could not find Image information", image);
    image.setMinReq(1);
    image.setMaxReq(1);
    assertTrue("The minimum should be ", image.getMinReq() == 1);
    assertTrue("The maximum should be ", image.getMaxReq() == 1);
    
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
  @Ignore
  public void startAndStopInstanceTest()
  {
    CcdpImageInfo imgInf = CcdpUtils.getImageInfo(CcdpNodeType.EC2);
    assertNotNull("Could not find Image information", imgInf);
    CcdpImageInfo image = CcdpImageInfo.copyImageInfo(imgInf);
    assertNotNull("Could not find Image information", image);
    image.setMinReq(1);
    image.setMaxReq(1);
    assertTrue("The minimum should be ", image.getMinReq() == 1);
    assertTrue("The maximum should be ", image.getMaxReq() == 1);
    
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
  @Ignore
  public void startManyAndStopSingleInstanceTest()
  {
    CcdpImageInfo imgInf = CcdpUtils.getImageInfo(CcdpNodeType.NIFI);
    assertNotNull("Could not find Image information", imgInf);
    CcdpImageInfo image = CcdpImageInfo.copyImageInfo(imgInf);
    assertNotNull("Could not find Image information", image);
    image.setMinReq(3);
    image.setMaxReq(3);
    assertTrue("The minimum should be ", image.getMinReq() == 3);
    assertTrue("The maximum should be ", image.getMaxReq() == 3);
    
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
    CcdpImageInfo imgInf = CcdpUtils.getImageInfo(CcdpNodeType.NIFI);
    assertNotNull("Could not find Image information", imgInf);
    CcdpImageInfo image = CcdpImageInfo.copyImageInfo(imgInf);
    assertNotNull("Could not find Image information", image);
    image.setMinReq(3);
    image.setMaxReq(3);
    assertTrue("The minimum should be ", image.getMinReq() == 3);
    assertTrue("The maximum should be ", image.getMaxReq() == 3);
    
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
    this.logger.debug("Got a new Message: " + msgType.toString());
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
