package com.axios.ccdp.test.unittest.MainApplication;

import java.util.ArrayList;
import java.util.List;

import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;

import com.axios.ccdp.impl.cloud.docker.DockerVMControllerImpl;
import com.axios.ccdp.intfs.CcdpConnectionIntf;
import com.axios.ccdp.intfs.CcdpDatabaseIntf;
import com.axios.ccdp.intfs.CcdpMessageConsumerIntf;
import com.axios.ccdp.messages.CcdpMessage;
import com.axios.ccdp.messages.ErrorMessage;
import com.axios.ccdp.messages.ResourceUpdateMessage;
import com.axios.ccdp.messages.TaskUpdateMessage;
import com.axios.ccdp.messages.CcdpMessage.CcdpMessageType;
import com.axios.ccdp.tasking.CcdpTaskRequest;
import com.axios.ccdp.test.unittest.JUnitTestHelper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.spotify.docker.client.DockerClient;

public class CcdpMainApplicationTest implements CcdpMessageConsumerIntf
{

  /**
   * Generates debug print statements based on the verbosity level.
   */
  private Logger logger = Logger
      .getLogger(CcdpMainApplicationTest.class.getName());
  /**
   * Object used to send and receive messages 
   */
  private CcdpConnectionIntf connection;
  /**
   * Object used to interact with the database
   */
  private CcdpDatabaseIntf dbClient = null;
  /**
   * Stores all incoming messages other than heartbeats
   */
  private List<CcdpMessage> messages = null;
  /**
   * Stores all incoming heartbeat messages
   */
  private List<CcdpMessage> heartbeats = null;
  /**
   * Stores the object to query the docker engine
   */
  private static DockerClient dockerClient = null;
  /**
   * Stores the configuration for the tests
   */
  private JsonNode jsonCfg;
  /**
   * Stores engine config for the tests
   */
  private JsonNode engCfg;
  /**
   * Generates all the JSON objects used during the tests
   */
  private ObjectMapper mapper = new ObjectMapper();
  /**
   * Stores all the ids from all the VMS created so they could be cleaned up
   * at the end of each test
   */
  private List<String> running_vms = null;
  /**
   * The actual object to test
   */
  private DockerVMControllerImpl docker = null;
  /**
   * Flag indicating whether or not all the created Docker Containers need to 
   * be deleted in tear down
   */
  private boolean rem_containers = true;
  /**
   * Instantiates a new object, but it does not perform any action
   */
  public CcdpMainApplicationTest()
  {
    logger.debug("Initializing Main Application Unit Test");
  }
  
  /************************ JUNIT BEFORECLASS, BEFORE, and AFTER STATEMENTS ******************/
  /*
   * Used to initialize the unit test instance (load CcdpUtils)
   */
  @BeforeClass
  public void initialize()
  {
    JUnitTestHelper.initialize();
    Logger.getRootLogger().setLevel(Level.WARN);
  }
  
  /*
   * Used to set up the pre-individual unit testing stuff
   */
  @Before
  public void testSetUp()
  {
    this.messages = new ArrayList<>();
    this.heartbeats = new ArrayList<>();
    
    
  }
  
  /*
   * Used to clean up after individual tests
   */
  @After
  public void testTearDown()
  {
    this.messages = null;
    this.heartbeats = null;
    
    
  }
  
  /****************** CCDP MAIN APPLICATION UNIT TESTS! *****************/
  
  
  
  
  /******************** HELPER AND SUPER CLASS FUNCTIONS! *****************/
  
  /**
   * Receives all the messages from the VM.  If the message is a heartbeat 
   * (ResourceUpdateMessage) then is stored in the heartbeats list otherwise
   * is stored in the messages list. This is a method from the super.
   * 
   * @param message the message sent to the unit test object
   */
  public void onCcdpMessage( CcdpMessage message )
  {
    CcdpMessageType msgType = CcdpMessageType.get( message.getMessageType() );
    logger.debug("Got a new Message: " + msgType.toString());
    switch( msgType )
    {
      case RESOURCE_UPDATE:
        // This shouldn't ever happen, using Mongo for Resource updates
        ResourceUpdateMessage msg = (ResourceUpdateMessage)message;
        this.heartbeats.add(msg);
        break;
      case TASK_UPDATE:
        CcdpTaskRequest task = ((TaskUpdateMessage)message).getTask();
        String tid = task.getTaskId();
        CcdpTaskRequest.CcdpTaskState state = task.getState();
        logger.debug(tid + " Updated task to " + state.toString());
        this.messages.add(message);
        break;
      case ERROR_MSG:
        ErrorMessage err = (ErrorMessage)message;
        logger.debug(err.getErrorMessage());
      default:
        this.messages.add(message);
    }
  }
}
