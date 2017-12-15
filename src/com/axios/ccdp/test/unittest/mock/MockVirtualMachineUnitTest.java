package com.axios.ccdp.test.unittest.mock;

import org.apache.log4j.Logger;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;

import com.axios.ccdp.cloud.mock.MockVirtualMachine;
import com.axios.ccdp.connections.intfs.CcdpConnectionIntf;
import com.axios.ccdp.connections.intfs.CcdpMessageConsumerIntf;
import com.axios.ccdp.factory.CcdpObjectFactory;
import com.axios.ccdp.messages.AssignSessionMessage;
import com.axios.ccdp.messages.CcdpMessage;
import com.axios.ccdp.messages.CcdpMessage.CcdpMessageType;
import com.axios.ccdp.messages.KillTaskMessage;
import com.axios.ccdp.messages.ResourceUpdateMessage;
import com.axios.ccdp.messages.RunTaskMessage;
import com.axios.ccdp.messages.TaskUpdateMessage;
import com.axios.ccdp.resources.CcdpVMResource;
import com.axios.ccdp.tasking.CcdpTaskRequest;
import com.axios.ccdp.tasking.CcdpTaskRequest.CcdpTaskState;
import com.axios.ccdp.test.unittest.JUnitTestHelper;
import com.axios.ccdp.utils.CcdpUtils;
import com.axios.ccdp.utils.CcdpUtils.CcdpNodeType;
import com.fasterxml.jackson.databind.node.ObjectNode;

import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;


public class MockVirtualMachineUnitTest implements CcdpMessageConsumerIntf
{

  /**
   * Generates debug print statements based on the verbosity level.
   */
  private Logger logger = Logger.getLogger(MockVirtualMachineUnitTest.class.getName());
  /**
   * Object used to send and receive messages 
   */
  private CcdpConnectionIntf connection;
  /**
   * Stores the actual object to test
   */
  private MockVirtualMachine node = null;
  /**
   * Stores all incoming messages other than heartbeats
   */
  private List<CcdpMessage> messages = null;
  /**
   * Stores all incoming heartbeat messages
   */
  private List<CcdpMessage> heartbeats = null;
  
  
  public MockVirtualMachineUnitTest()
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
    
    this.node = new MockVirtualMachine(CcdpNodeType.EC2);
    this.node.setRemoveTask(false);
    
    assertNotNull("Could not instantiate a new MockVirtualMachine", this.node);
    assertTrue("The messages should be empty", this.messages.isEmpty());
    assertTrue("The heartbeats should be empty", this.heartbeats.isEmpty());
    
    this.logger.info("Create a new Thread and start it to begin test");
    
  }
  
  /**
   * Makes sure the connections are in place by waiting for Heartbeats.  If 
   * this test passes then we are sending and receiving messages
   */
  @Test
  public void vmIsSendingHeartbeatsTest()
  {
    this.logger.debug("Checking to see if is sending Heartbeats");
    new Thread(this.node).start();
    
    long hb = 3;
    try
    {
      hb = CcdpUtils.getIntegerProperty(CcdpUtils.CFG_KEY_HB_FREQ);
    }
    catch( Exception e )
    {
    }
    long wait = hb*3;
    CcdpUtils.pause( wait );
    int sz = this.heartbeats.size();
    assertTrue("Has not received Heartbeats", sz > 0);
    this.logger.debug("After " + wait + " secs received " + sz + " messages");
    
  }
  
  /**
   * Test the ability to change the session id
   */
  @Test
  public void setSessionIdTest()
  {
    CcdpVMResource info = this.node.getVirtualMachineInfo();
    String iid = info.getInstanceId();
    assertNotNull("The Instance Id should not be null", iid);
    
    AssignSessionMessage msg = new AssignSessionMessage();
    msg.setSessionId("unittest-session");
    this.connection.sendCcdpMessage(iid, msg);
    CcdpUtils.pause(1);
    this.logger.debug("Getting the session again");
    info = this.node.getVirtualMachineInfo();
    String session = info.getAssignedSession();
    this.logger.debug("The new Session " + session);
    assertNotNull("The session should not be null", session);
    assertTrue("The session ID does not match", "unittest-session".equals(session));
    
  }
  
  /**
   * Tests the ability to run a task when assigning the session id
   */
  @Test
  public void runAssingmentTaskTest()
  {
    CcdpVMResource info = this.node.getVirtualMachineInfo();
    String iid = info.getInstanceId();
    assertNotNull("The Instance Id should not be null", iid);
    
    AssignSessionMessage msg = new AssignSessionMessage();
    msg.setSessionId("test-session");
    msg.setAssignCommand("MOCK_PAUSE 10");
    this.connection.sendCcdpMessage(iid, msg);
    CcdpUtils.pause(0.5);
    this.logger.debug("Getting the session again");
    info = this.node.getVirtualMachineInfo();
    String session = info.getAssignedSession();
    this.logger.debug("The new Session " + session);
    assertNotNull("The session should not be null", session);
    assertTrue("The session ID does not match", "test-session".equals(session));
    List<CcdpTaskRequest> tasks = info.getTasks();
    int sz = tasks.size();
    this.logger.debug("VM has " + sz + " tasks");
    assertTrue("Need to be running one task only", sz == 1);
  }
  
  
  /**
   * Tests the ability to run a task that pauses for a short time
   */
  @Test
  public void runPauseTaskTest()
  {
    int time = 3;
    CcdpTaskRequest task = this.sendTaskRequest("MOCK_PAUSE", time);
    String taskId = task.getTaskId();
    
    CcdpVMResource info = this.node.getVirtualMachineInfo();
    String iid = info.getInstanceId();
    assertNotNull("The Instance ID cannot be null", iid);
    
    CcdpUtils.pause(0.2);
    this.logger.debug("Getting the information again");
    info = this.node.getVirtualMachineInfo();
    List<CcdpTaskRequest> tasks = info.getTasks();
    int sz = tasks.size();
    this.logger.debug("VM has " + sz + " tasks");
    assertTrue("Need to be running one task only", sz == 1);
    
    CcdpTaskRequest myT = tasks.get(0);
    assertTrue("Got a different Task", taskId.equals(myT.getTaskId()));
    boolean found = this.gotMessageWithState(CcdpTaskState.SUCCESSFUL, time);
    assertTrue("The Task did not finish on time", found);
  }
  
  /**
   * Tests the ability to run a task that sets the CPU to 100 % for some time
   */
  @Test
  public void runCpuTaskTest()
  {
    int time = 5;
    CcdpTaskRequest task = this.sendTaskRequest("MOCK_CPU", time);
    String taskId = task.getTaskId();
    
    CcdpUtils.pause(0.2);
    CcdpVMResource info = this.node.getVirtualMachineInfo();
    
    List<CcdpTaskRequest> tasks = info.getTasks();
    int sz = tasks.size();
    this.logger.debug("VM has " + sz + " tasks");
    assertTrue("Need to be running one task only", sz == 1);
    
    CcdpTaskRequest myT = tasks.get(0);
    assertTrue("Got a different Task", taskId.equals(myT.getTaskId()));
    boolean found = this.gotMessageWithState(CcdpTaskState.SUCCESSFUL, time);
    assertTrue("The Task did not finish on time", found);
  }
  
  /**
   * Tests the ability to run a task that fails to complete
   */
  @Test
  public void runFailedTaskTest()
  {
    int time = 5;
    CcdpTaskRequest task = this.sendTaskRequest("MOCK_FAIL", time);
    String taskId = task.getTaskId();
    
    CcdpUtils.pause(0.2);
    CcdpVMResource info = this.node.getVirtualMachineInfo();
    
    List<CcdpTaskRequest> tasks = info.getTasks();
    int sz = tasks.size();
    this.logger.debug("VM has " + sz + " tasks");
    assertTrue("Need to be running one task only", sz == 1);
    
    CcdpTaskRequest myT = tasks.get(0);
    assertTrue("Got a different Task", taskId.equals(myT.getTaskId()));
    boolean found = this.gotMessageWithState(CcdpTaskState.FAILED, time);
    assertTrue("The Task did not finish on time", found);
  }
  
  /**
   * Tests the ability to run a task for a long time, but killed during the
   * execution
   */
  @Test
  public void runKilledTaskTest()
  {
    int time = 30;
    CcdpTaskRequest task = this.sendTaskRequest("MOCK_PAUSE", time);
    String taskId = task.getTaskId();
    
    CcdpUtils.pause(0.2);
    CcdpVMResource info = this.node.getVirtualMachineInfo();
    
    List<CcdpTaskRequest> tasks = info.getTasks();
    int sz = tasks.size();
    this.logger.debug("VM has " + sz + " tasks");
    assertTrue("Need to be running one task only", sz == 1);
    
    CcdpTaskRequest myT = tasks.get(0);
    assertTrue("Got a different Task", taskId.equals(myT.getTaskId()));
    
    CcdpUtils.pause(2);
    this.logger.debug("Killing Task");
    KillTaskMessage msg = new KillTaskMessage();
    msg.setTask(task);
    this.connection.sendCcdpMessage(info.getInstanceId(), msg);
    
    boolean found = this.gotMessageWithState(CcdpTaskState.KILLED, 3);
    assertTrue("The Task did not finish on time", found);
  }
  
  
  /**
   * Sends a task message to the intended VM.  
   * 
   * @param action what to do either paused, cpu, or
   * @param time for how long
   * 
   * @return the task that was sent
   */
  private CcdpTaskRequest sendTaskRequest(String action, long time)
  {
    CcdpVMResource info = this.node.getVirtualMachineInfo();
    String iid = info.getInstanceId();
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
   * Reads incoming messages until receives one task with the expected state.
   * If it does not happen in less than the allowed time it returns false
   * 
   * @param expected the expected state
   * @param time how long to wait to get the desired state
   * 
   * @return true if the expected state is received before the allowed time
   */
  private boolean gotMessageWithState( CcdpTaskState expected, long time )
  {
    boolean done = false;
    
    long start = System.currentTimeMillis();
    CcdpTaskState current = null;
    
    while( !done )
    {
      if( !this.messages.isEmpty() )
      {
        CcdpMessage message = this.messages.remove(0);
        CcdpMessageType msgType = CcdpMessageType.get( message.getMessageType() );
        if( msgType.equals(CcdpMessage.CcdpMessageType.TASK_UPDATE) )
        {
          TaskUpdateMessage tum = (TaskUpdateMessage)message;
          CcdpTaskRequest t = tum.getTask();
          CcdpTaskState state = t.getState();
          // Want to see only when it changes
          if( !state.equals(current) )
          {
            this.logger.info("Current Task State " + state);
            current = state;
          }
          if( expected.equals(state) )
          {
            done = true;
            long dur = (System.currentTimeMillis() - start) / 1000;
            this.logger.info("Took " + dur + " seconds to run");
            return true;
          }
        }
      }
      else
        CcdpUtils.pause(0.1);
      
      long now = System.currentTimeMillis();
      if( (now - start) >= ((time + 1) * 1000) )
        fail("Waiting for more than " + time + 
             " seconds and it should be done by now");
    }
    
    return false;
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
    this.node.shutdown("Done with test");
    CcdpUtils.pause(0.25);;
    
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
