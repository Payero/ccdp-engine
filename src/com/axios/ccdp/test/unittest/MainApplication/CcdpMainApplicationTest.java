package com.axios.ccdp.test.unittest.MainApplication;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import com.axios.ccdp.factory.CcdpObjectFactory;
import com.axios.ccdp.fmwk.CcdpMainApplication;
import com.axios.ccdp.impl.cloud.docker.DockerVMControllerImpl;
import com.axios.ccdp.intfs.CcdpConnectionIntf;
import com.axios.ccdp.intfs.CcdpDatabaseIntf;
import com.axios.ccdp.intfs.CcdpMessageConsumerIntf;
import com.axios.ccdp.messages.CcdpMessage;
import com.axios.ccdp.messages.ErrorMessage;
import com.axios.ccdp.messages.ResourceUpdateMessage;
import com.axios.ccdp.messages.TaskUpdateMessage;
import com.axios.ccdp.resources.CcdpVMResource;
import com.axios.ccdp.resources.CcdpVMResource.ResourceStatus;
import com.axios.ccdp.messages.CcdpMessage.CcdpMessageType;
import com.axios.ccdp.tasking.CcdpTaskRequest;
import com.axios.ccdp.test.CcdpMsgSender;
import com.axios.ccdp.test.unittest.JUnitTestHelper;
import com.axios.ccdp.utils.AmqCleaner;
import com.axios.ccdp.utils.CcdpUtils;
import com.axios.ccdp.utils.MongoCleaner;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.spotify.docker.client.DockerClient;

// These tests are to test the state and functionality of the Main CCDP Engine Application as of 07/2019
public class CcdpMainApplicationTest implements CcdpMessageConsumerIntf
{

  /**
   * Generates debug print statements based on the verbosity level.
   */
  private static Logger logger = Logger
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
   * Stores all the VMS created so they could be cleaned up
   * at the end of each test
   */
  private List<CcdpVMResource> running_vms = null;
  /**
   * The actual object to test
   */
  private DockerVMControllerImpl docker = null;
  /**
   * Flag indicating whether or not all the created Docker Containers need to 
   * be deleted in tear down
   */
  private boolean rem_containers = true;
  /*
   * The main engine object to be tested
   */
  private CcdpMainApplication engine = null;
  
  public CcdpMainApplicationTest()
  {
    logger.debug("Initializing Main Application Unit Test");
  }
  
  /************************ JUNIT BEFORECLASS, BEFORE, and AFTER STATEMENTS ******************/
  /*
   * Used to initialize the unit test instance (load CcdpUtils)
   */
  @BeforeClass
  public static void initialize()
  {
    JUnitTestHelper.initialize();
    Logger.getRootLogger().setLevel(Level.WARN);
  }
  
  /*
   * Used to set up the pre-individual unit testing stuff
   */
  @Before
  public void setUp()
  {
    this.messages = new ArrayList<>();
    this.running_vms = new ArrayList<>();
    CcdpObjectFactory factory = CcdpObjectFactory.newInstance();
    
    JsonNode db_cfg = CcdpUtils.getDatabaseIntfCfg();
    dbClient = factory.getCcdpDatabaseIntf(db_cfg);
    dbClient.configure(db_cfg);
    dbClient.connect();
  }
  
  /*
   * Used to clean up after individual tests
   */
  @After
  public void tearDown()
  {
    // Terminate any VMs that were started during the test
    if ( engine != null )
      engine.stopCCDPApplication(true);
    
    this.messages = null;
    this.running_vms = null;
    
    // Pause so AWS based VMs have time to terminate before cleaning
    logger.debug("Waiting 15 seconds for VMs to terminate");
    CcdpUtils.pause(15);
    
    // Clean Mongo and AMQ for next test
    List<CcdpVMResource> vms = dbClient.getAllVMInformation();
    if (vms.size() == 0)
    {
      logger.info("There are no VMs in MongoDB");
    }
    else
    {
      for( CcdpVMResource vm :vms )
      {
        String vmID = vm.getInstanceId();
        logger.debug("Deleting VM " + vmID);
        dbClient.deleteVMInformation(vmID);
      }
    }
    dbClient.disconnect();
    dbClient = null;
    new AmqCleaner("tcp://ax-ccdp.com:61616", "all", null);  
    
    logger.debug("Test Complete!");
    System.out.println("***************************************************************************************************");
  }
  
  @AfterClass
  public static void terminate()
  {
    logger.info("All Tests Complete!");
  }
  
  /****************** CCDP MAIN APPLICATION UNIT TESTS! *****************/
  
  @Test
  public void testSetupCompletion()
  {
    logger.info("Set up ran to completion");
  }
  
  /*
   * A test to make sure that the main app doesn't start any unwanted session
   * This should work for both configs, but it won't be meaningful until hybrid controllers are implemented
   */
  @Test
  public void NoFreeVms()
  {
    logger.debug("Starting NoFreeVms Test!");
    
    ObjectNode res_cfg = CcdpUtils.getResourceCfg("DOCKER").deepCopy();
    res_cfg.put("min-number-free-agents", 0);
    CcdpUtils.setResourceCfg("DOCKER", res_cfg); 
    res_cfg = CcdpUtils.getResourceCfg("EC2").deepCopy();
    res_cfg.put("min-number-free-agents", 0);
    CcdpUtils.setResourceCfg("EC2", res_cfg);
    res_cfg = CcdpUtils.getResourceCfg("DEFAULT").deepCopy();
    res_cfg.put("min-number-free-agents", 0);
    CcdpUtils.setResourceCfg("DEFAULT", res_cfg);
    
    // Start the engine and wait for it to get started
    engine = new CcdpMainApplication(null);
    CcdpUtils.pause(30);
    
    running_vms = engine.getAllCcdpVMResources();
    assertTrue("There was a VM started", running_vms.size() == 0);
    
    // Check with Mongo to ensure no VMs
    List<CcdpVMResource> MongoRecord = dbClient.getAllVMInformation();
    for ( CcdpVMResource vm : MongoRecord )
    {
      if ( vm.getStatus().equals(ResourceStatus.RUNNING) )
        fail("There is a VM running");
    }
  }
  
  /*
   * A test to make sure that the main app doesn't start an unwanted docker session
   * The Docker CFG must be linked to ccdp-config.json for this to be meaningful
   */
  @Test
  public void NoFreeVms_Docker()
  {
    logger.debug("Starting NoFreeVms_Docker Test!");
    
    ObjectNode res_cfg = CcdpUtils.getResourceCfg("DOCKER").deepCopy();
    res_cfg.put("min-number-free-agents", 0);
    CcdpUtils.setResourceCfg("DOCKER", res_cfg);
    
    // Start the engine and wait for it to get started
    engine = new CcdpMainApplication(null);
    CcdpUtils.pause(30);
    
    running_vms = engine.getAllCcdpVMResourcesOfType("DOCKER");
    assertTrue("There was a VM started", running_vms.size() == 0);
    
    // Check with Mongo to ensure no VMs
    List<CcdpVMResource> MongoRecord = dbClient.getAllVMInformationOfType("DOCKER");
    for ( CcdpVMResource vm : MongoRecord )
    {
      if ( vm.getStatus().equals(ResourceStatus.RUNNING) )
        fail("There is a VM running");
    }
  }
  
  /*
   * A test to make sure that the main app doesn't start an unwanted EC2 session
   * The AWS CFG must be linked to ccdp-config for this to be meaningful
   */
  @Test
  public void NoFreeVms_EC2()
  {
    logger.debug("Starting NoFreeVms_EC2 Test!");
    
    ObjectNode res_cfg = CcdpUtils.getResourceCfg("EC2").deepCopy();
    res_cfg.put("min-number-free-agents", 0);
    CcdpUtils.setResourceCfg("EC2", res_cfg);
    
    // Start the engine and wait for it to get started
    engine = new CcdpMainApplication(null);
    logger.debug("Waiting 80 seconds for VM to spawn");
    CcdpUtils.pause(80);
    
    running_vms = engine.getAllCcdpVMResourcesOfType("EC2");
    assertTrue("There was a VM started", running_vms.size() == 0);
    
    // Check with Mongo to ensure no VMs
    List<CcdpVMResource> MongoRecord = dbClient.getAllVMInformationOfType("EC2");
    for ( CcdpVMResource vm : MongoRecord )
    {
      if ( vm.getStatus().equals(ResourceStatus.RUNNING) )
        fail("There is a VM running");
    }
  }
  
  /*
   * A test to make sure that the main app doesn't start an unwanted EC2 session
   * The AWS CFG must be linked to ccdp-config for this to be meaningful
   */
  @Test
  public void NoFreeVms_Default()
  {
    logger.debug("Starting NoFreeVms_Default Test!");
    
    ObjectNode res_cfg = CcdpUtils.getResourceCfg("DEFAULT").deepCopy();
    res_cfg.put("min-number-free-agents", 0);
    CcdpUtils.setResourceCfg("DEFAULT", res_cfg);
    
    // Start the engine and wait for it to get started
    engine = new CcdpMainApplication(null);
    logger.debug("Waiting 80 seconds for VM to spawn");
    CcdpUtils.pause(80);
    
    running_vms = engine.getAllCcdpVMResourcesOfType("DEFAULT");
    assertTrue("There was a VM started", running_vms.size() == 0);
    
    // Check with Mongo to ensure no VMs
    List<CcdpVMResource> MongoRecord = dbClient.getAllVMInformationOfType("DEFAULT");
    for ( CcdpVMResource vm : MongoRecord )
    {
      if ( vm.getStatus().equals(ResourceStatus.RUNNING) )
        fail("There is a VM running");
    }
  }
  
  /*
   * A test to create 1 free Docker VM for use
   * The Docker CFG must be linked to ccdp-config.json for this to be meaningful
   */
  @Test
  public void OneFreeVm_Docker()
  {
    logger.debug("Starting OneFreeVm_Docker Test!");
    // Set in the config that there should be 1 free Docker agent
    ObjectNode res_cfg = CcdpUtils.getResourceCfg("DOCKER").deepCopy();
    res_cfg.put("min-number-free-agents", 1);
    CcdpUtils.setResourceCfg("DOCKER", res_cfg);
    
    // Start the engine and wait for it to get started
    engine = new CcdpMainApplication(null);
    CcdpUtils.pause(35);
    
    // Check for the free agent
    running_vms = engine.getAllCcdpVMResourcesOfType("DOCKER");
    assertTrue("There should be a running VM", running_vms.size() == 1);
    
    // Check for node type
    CcdpVMResource vm = running_vms.get(0);
    assertTrue("The node should be of type DOCKER", "DOCKER".equals(vm.getNodeType()));
    
    // Check with Mongo to verify
    String vmId = vm.getInstanceId();
    long initialTime = dbClient.getVMInformation(vmId).getLastUpdatedTime();
    CcdpUtils.pause(7);
    assertFalse("There was no Mongo heartbeat", initialTime == dbClient.getVMInformation(vmId).getLastUpdatedTime());
  }
  
  /*
   * A test to create 1 free EC2 VM for use
   * The AWS CFG must be linked to ccdp-config.json for this to be meaningful
   */
  @Test
  public void OneFreeVm_EC2()
  {
    logger.debug("Starting OneFreeVm_EC2 Test!");
    // Set in the config that there should be 1 free Docker agent
    ObjectNode res_cfg = CcdpUtils.getResourceCfg("EC2").deepCopy();
    res_cfg.put("min-number-free-agents", 1);
    CcdpUtils.setResourceCfg("EC2", res_cfg);
    
    // Start the engine and wait for it to get started
    engine = new CcdpMainApplication(null);
    logger.debug("Waiting 80 seconds for VM to spawn");
    CcdpUtils.pause(80);
    
    // Check for the free agent
    running_vms = engine.getAllCcdpVMResourcesOfType("EC2");
    assertTrue("There should be a running VM", running_vms.size() == 1);
    
    // Check for node type
    CcdpVMResource vm = running_vms.get(0);
    assertTrue("The node should be of type EC2", "EC2".equals(vm.getNodeType()));
    
    // Check with Mongo to verify
    String vmId = vm.getInstanceId();
    long initialTime = dbClient.getVMInformation(vmId).getLastUpdatedTime();
    CcdpUtils.pause(7);
    assertFalse("There was no Mongo heartbeat", initialTime == dbClient.getVMInformation(vmId).getLastUpdatedTime());
  }
  
  /*
   * A test to create 1 free Default VM for use
   * The Docker CFG must be linked to ccdp-config.json for this to be meaningful
   */
  @Test
  public void OneFreeVm_Default()
  {
    logger.debug("Starting OneFreeVm_Default Test!");
    // Set in the config that there should be 1 free Default agent
    ObjectNode res_cfg = CcdpUtils.getResourceCfg("DEFAULT").deepCopy();
    res_cfg.put("min-number-free-agents", 1);
    CcdpUtils.setResourceCfg("DEFAULT", res_cfg);
    
    // Start the engine and wait for it to get started
    engine = new CcdpMainApplication(null);
    logger.debug("Waiting 80 seconds for VM to spawn");
    CcdpUtils.pause(80);
    
    // Check for the free agent
    running_vms = engine.getAllCcdpVMResources();
    assertTrue("There should be a running VM", running_vms.size() == 1);
    
    // Check for node type
    // Default Nodes change their node type to EC2 because they use the same start
    // script and tarball as EC2 instances. This could be fixed easily but is that really necessary...?
    CcdpVMResource vm = running_vms.get(0);
    assertTrue("The node should be of type DEFAULT", "EC2".equals(vm.getNodeType()));
    
    // Check with Mongo to verify
    String vmId = vm.getInstanceId();
    long initialTime = dbClient.getVMInformation(vmId).getLastUpdatedTime();
    CcdpUtils.pause(7);
    assertFalse("There was no Mongo heartbeat", initialTime == dbClient.getVMInformation(vmId).getLastUpdatedTime());
  }
  
  /*
   * This test shows that the MainApp can launch a docker VM that executes a task on creation
   * The Docker CFG must be linked to ccdp-config.json for this to be meaningful
   */
  @Test
  public void DockerStartupTask() 
  {
    logger.debug("Starting DockerStartupTask Test!");
    
    ObjectNode res_cfg = CcdpUtils.getResourceCfg("DOCKER").deepCopy();
    res_cfg.put("min-number-free-agents", 0);
    CcdpUtils.setResourceCfg("DOCKER", res_cfg); 
    res_cfg = CcdpUtils.getResourceCfg("EC2").deepCopy();
    res_cfg.put("min-number-free-agents", 0);
    CcdpUtils.setResourceCfg("EC2", res_cfg);
    res_cfg = CcdpUtils.getResourceCfg("DEFAULT").deepCopy();
    res_cfg.put("min-number-free-agents", 0);
    CcdpUtils.setResourceCfg("DEFAULT", res_cfg);
    
    // start application with rand_time task
    // YOU WILL NEED TO CHANGE THE PATH FOR THIS TO WORK FOR YOU
    engine = new CcdpMainApplication("/projects/users/srbenne/workspace/engine/data/new_tests/startupUnitTest_docker.json");
    CcdpUtils.pause(30);
   
    running_vms = engine.getAllCcdpVMResources();
    assertTrue("There should only be 1 VM running.", running_vms.size() == 1);
    CcdpVMResource vm = running_vms.get(0);
    assertTrue("The VM should be of node type Docker","DOCKER".equals(vm.getNodeType()));
    assertTrue("The VM should have a task", vm.getNumberTasks() > 0);
    
    // Let task complete, should despawn VM 
    CcdpUtils.pause(35);
    running_vms = engine.getAllCcdpVMResources();
    assertTrue("The tasked VM should've despawned by now", running_vms.size() == 0);
  }
  
  /*
   * This test shows that the MainApp can launch a AWS VM that executes a task on creation
   * The AWS CFG must be linked to ccdp-config.json for this to be meaningful
   */
  @Test
  public void EC2StartupTask() 
  {
    logger.debug("Starting DefaultStartupTask Test!");
    
    ObjectNode res_cfg = CcdpUtils.getResourceCfg("DOCKER").deepCopy();
    res_cfg.put("min-number-free-agents", 0);
    CcdpUtils.setResourceCfg("DOCKER", res_cfg); 
    res_cfg = CcdpUtils.getResourceCfg("EC2").deepCopy();
    res_cfg.put("min-number-free-agents", 0);
    CcdpUtils.setResourceCfg("EC2", res_cfg);
    res_cfg = CcdpUtils.getResourceCfg("DEFAULT").deepCopy();
    res_cfg.put("min-number-free-agents", 0);
    CcdpUtils.setResourceCfg("DEFAULT", res_cfg);
    
    // start application with rand_time task
    // YOU WILL NEED TO CHANGE THE PATH FOR THIS TO WORK FOR YOU
    engine = new CcdpMainApplication("/projects/users/srbenne/workspace/engine/data/new_tests/startupUnitTest_ec2.json");
    CcdpUtils.pause(50);
   
    running_vms = engine.getAllCcdpVMResources();
    assertTrue("There should only be 1 VM running.", running_vms.size() == 1);
    CcdpVMResource vm = running_vms.get(0);
    assertTrue("The VM should be of node type EC2","EC2".equals(vm.getNodeType()));
    assertTrue("The VM should have a task", vm.getNumberTasks() > 0);
    
    // Let task complete, should despawn VM 
    CcdpUtils.pause(35);
    running_vms = engine.getAllCcdpVMResources();
    assertTrue("The tasked VM should've despawned by now", running_vms.size() == 0);
  }
  
  /*
   * This test shows that the MainApp can launch a default VM that executes a task on creation
   * The AWS CFG must be linked to ccdp-config.json for this to be meaningful
   */
  @Test
  public void DefaultStartupTask() 
  {
    logger.debug("Starting DefaultStartupTask Test!");
    
    ObjectNode res_cfg = CcdpUtils.getResourceCfg("DOCKER").deepCopy();
    res_cfg.put("min-number-free-agents", 0);
    CcdpUtils.setResourceCfg("DOCKER", res_cfg); 
    res_cfg = CcdpUtils.getResourceCfg("EC2").deepCopy();
    res_cfg.put("min-number-free-agents", 0);
    CcdpUtils.setResourceCfg("EC2", res_cfg);
    res_cfg = CcdpUtils.getResourceCfg("DEFAULT").deepCopy();
    res_cfg.put("min-number-free-agents", 0);
    CcdpUtils.setResourceCfg("DEFAULT", res_cfg);
    
    // start application with rand_time task
    // YOU WILL NEED TO CHANGE THE PATH FOR THIS TO WORK FOR YOU
    engine = new CcdpMainApplication("/projects/users/srbenne/workspace/engine/data/new_tests/startupUnitTest_default.json");
    CcdpUtils.pause(50);
   
    running_vms = engine.getAllCcdpVMResources();
    assertTrue("There should only be 1 VM running.", running_vms.size() == 1);
    CcdpVMResource vm = running_vms.get(0);
    
    // Node type for defaults is EC2, see Default task above....
    assertTrue("The VM should be of node type DEFAULT","EC2".equals(vm.getNodeType()));
    assertTrue("The VM should have a task", vm.getNumberTasks() > 0);
    
    // Let task complete, should despawn VM 
    CcdpUtils.pause(35);
    running_vms = engine.getAllCcdpVMResources();
    assertTrue("The tasked VM should've despawned by now", running_vms.size() == 0);
  }
  
  /*
   * This test determines if Docker containers are spawned an terminated correctly to keep the
   * number of free agents correctly
   * The Docker CFG must be linked to ccdp-config.json for this to be meaningful
   */
  @Test
  public void SpawnAndDespawnDocker()
  {
    logger.debug("Starting DockerSpawnAndDespawn Test!");
    
    ObjectNode res_cfg = CcdpUtils.getResourceCfg("DOCKER").deepCopy();
    res_cfg.put("min-number-free-agents", 1);
    CcdpUtils.setResourceCfg("DOCKER", res_cfg); 
    res_cfg = CcdpUtils.getResourceCfg("EC2").deepCopy();
    res_cfg.put("min-number-free-agents", 0);
    CcdpUtils.setResourceCfg("EC2", res_cfg);
    res_cfg = CcdpUtils.getResourceCfg("DEFAULT").deepCopy();
    res_cfg.put("min-number-free-agents", 0);
    CcdpUtils.setResourceCfg("DEFAULT", res_cfg);
    
    // Start engine and give free agent time to spawn
    logger.debug("Starting engine and spawning FA");
    engine = new CcdpMainApplication(null);
    CcdpUtils.pause(50);
    
    logger.debug("Check that there is still only 1 VM");
    running_vms = engine.getAllCcdpVMResources();
    assertTrue("There should only be 1 VM", running_vms.size() == 1);
    String original = running_vms.get(0).getInstanceId();
    
    // Send task, it should spawn a new vm and give the task to the old vm
    String task_filename = "/projects/users/srbenne/workspace/engine/data/new_tests/startupUnitTest_docker.json";
    try
    {
      logger.debug("Sending task");
      byte[] data = Files.readAllBytes( Paths.get( task_filename ) );
      String job = new String(data, "utf-8");
      new CcdpMsgSender(null, job, null, null);
    }
    catch ( Exception e )
    {
      logger.error("Error loading file, exception thrown");
      e.printStackTrace();
    }
    
    // Wait for new VM to spawn up
    CcdpUtils.pause(20);
    
    // Test proper execution
    logger.debug("Checking to see if there is 2 VMs");
    running_vms = engine.getAllCcdpVMResources();
    assertTrue("There should be two VMs running", running_vms.size() == 2);
    for (CcdpVMResource vm : running_vms)
    {
      if ( vm.getInstanceId().equals(original))
        assertTrue("The original VM should have the assigned task", vm.getNumberTasks() > 0);
    }
    
    //Wait for task to complete
    CcdpUtils.pause(15);
    logger.debug("Task should be done now, check there is only 1 VM and it isn't the original");
    running_vms = engine.getAllCcdpVMResources();
    assertTrue("One of the VMs shoud've been stopped", running_vms.size() == 1);
    assertFalse("The original VM should have been despawned", running_vms.get(0).getInstanceId().equals(original));
  }
  
  /*
   * This test determines if EC2 instances are spawned an terminated correctly to keep the
   * number of free agents correctly
   * The AWS CFG must be linked to ccdp-config.json for this to be meaningful
   */
  @Test
  public void SpawnAndDespawnEC2()
  {
    logger.debug("Starting DockerSpawnAndDespawn Test!");
    
    ObjectNode res_cfg = CcdpUtils.getResourceCfg("DOCKER").deepCopy();
    res_cfg.put("min-number-free-agents", 0);
    CcdpUtils.setResourceCfg("DOCKER", res_cfg); 
    res_cfg = CcdpUtils.getResourceCfg("EC2").deepCopy();
    res_cfg.put("min-number-free-agents", 1);
    CcdpUtils.setResourceCfg("EC2", res_cfg);
    res_cfg = CcdpUtils.getResourceCfg("DEFAULT").deepCopy();
    res_cfg.put("min-number-free-agents", 0);
    CcdpUtils.setResourceCfg("DEFAULT", res_cfg);
    
    // Start engine and give free agent time to spawn
    logger.debug("Starting engine and spawning FA");
    engine = new CcdpMainApplication(null);
    CcdpUtils.pause(45);
    
    logger.debug("Check that there is still only 1 VM");
    running_vms = engine.getAllCcdpVMResources();
    assertTrue("There should only be 1 VM", running_vms.size() == 1);
    String original = running_vms.get(0).getInstanceId();
    
    // Send task, it should spawn a new vm and give the task to the old vm
    String task_filename = "/projects/users/srbenne/workspace/engine/data/new_tests/startupUnitTest_ec2.json";
    try
    {
      logger.debug("Sending task");
      byte[] data = Files.readAllBytes( Paths.get( task_filename ) );
      String job = new String(data, "utf-8");
      new CcdpMsgSender(null, job, null, null);
    }
    catch ( Exception e )
    {
      logger.error("Error loading file, exception thrown");
      e.printStackTrace();
    }
    
    // Wait for new VM to spawn up
    CcdpUtils.pause(15);
    
    // Test proper execution
    logger.debug("Checking to see if there is 2 VMs");
    running_vms = engine.getAllCcdpVMResources();
    assertTrue("There should be two VMs running", running_vms.size() == 2);
    for (CcdpVMResource vm : running_vms)
    {
      if ( vm.getInstanceId().equals(original))
        assertTrue("The original VM should have the assigned task", vm.getNumberTasks() > 0);
    }
    
    //Wait for task to complete
    CcdpUtils.pause(60);
    logger.debug("Task should be done now, check there is only 1 VM and it isn't the original");
    running_vms = engine.getAllCcdpVMResources();
    assertTrue("One of the VMs shoud've been stopped", running_vms.size() == 1);
    assertFalse("The original VM should have been despawned", running_vms.get(0).getInstanceId().equals(original));
  }
  
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
