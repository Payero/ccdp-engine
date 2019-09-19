package com.axios.ccdp.test.unittest.MainApplication;

import static org.junit.Assert.assertEquals;
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
import org.springframework.core.annotation.Order;

import com.axios.ccdp.factory.CcdpObjectFactory;
import com.axios.ccdp.fmwk.CcdpMainApplication;
import com.axios.ccdp.intfs.CcdpDatabaseIntf;
import com.axios.ccdp.intfs.CcdpMessageConsumerIntf;
import com.axios.ccdp.messages.CcdpMessage;
import com.axios.ccdp.messages.ErrorMessage;
import com.axios.ccdp.messages.TaskUpdateMessage;
import com.axios.ccdp.resources.CcdpResourceAbs;
import com.axios.ccdp.resources.CcdpServerlessResource;
import com.axios.ccdp.resources.CcdpVMResource;
import com.axios.ccdp.resources.CcdpVMResource.ResourceStatus;
import com.axios.ccdp.messages.CcdpMessage.CcdpMessageType;
import com.axios.ccdp.tasking.CcdpTaskRequest;
import com.axios.ccdp.test.CcdpMsgSender;
import com.axios.ccdp.test.unittest.JUnitTestHelper;
import com.axios.ccdp.utils.AmqCleaner;
import com.axios.ccdp.utils.CcdpConfigParser;
import com.axios.ccdp.utils.CcdpUtils;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

// These tests are to test the state and functionality of the Main CCDP Engine Application as of 07/2019
// Written by Scott Bennett, scott.bennett@caci.com
public class CcdpMainApplicationTest implements CcdpMessageConsumerIntf
{

  /**
   * Generates debug print statements based on the verbosity level.
   */
  private static Logger logger = Logger
      .getLogger(CcdpMainApplicationTest.class.getName());

  /**
   * Object used to interact with the database
   */
  private CcdpDatabaseIntf dbClient = null;
  /**
   * Stores all incoming messages other than heartbeats
   */
  private List<CcdpMessage> messages = null;
  /**
   * Stores all the VMS created so they could be cleaned up
   * at the end of each test
   */
  private List<CcdpVMResource> running_vms = null;
  /*
   * Stores all the serverless controllers started by the engine
   */
  private List<CcdpServerlessResource> serverless_controllers = null;
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
    this.serverless_controllers = new ArrayList<>();
    CcdpObjectFactory factory = CcdpObjectFactory.newInstance();
    
    JsonNode db_cfg = CcdpUtils.getDatabaseIntfCfg();
    dbClient = factory.getCcdpDatabaseIntf(db_cfg);
    dbClient.configure(db_cfg);
    dbClient.connect();
    
    if ( System.getenv("CCDP_HOME") == null )
    {
      fail("Please set the environment variable \"CCDP_HOME\" before running unit tests");
    }
  }
  
  /*
   * Used to clean up after individual tests
   */
  @After
  public void tearDown()
  {
    // Terminate any VMs that were started during the test
    if ( engine != null )
    {
      try
      {
        engine.stopCCDPApplication(true);
    
      }
      catch ( Exception e )
      {
        logger.warn("Exception caught in teardown (stop app):\n");
        e.printStackTrace();
      }
    }
    this.messages = null;
    this.running_vms = null;
    this.serverless_controllers = null;
    
    // Pause so AWS based VMs have time to terminate before cleaning
    logger.debug("Waiting 15 seconds for VMs to terminate");
    CcdpUtils.pause(15);
    
    // Clean Mongo and AMQ for next test
    List<CcdpResourceAbs> vms = dbClient.getAllInformation();
    if (vms.size() == 0)
    {
      logger.info("There are no VMs in MongoDB");
    }
    else
    {
      for( CcdpResourceAbs vm :vms )
      {
        if ( vm.getIsServerless() )
        {
          String cont_type = vm.getNodeType();
          logger.debug("Deleting controller " + cont_type);
          dbClient.deleteServerlessInformation(cont_type);
        }
        else
        {
          CcdpVMResource vmID = (CcdpVMResource) vm;
          String iid = vmID.getInstanceId();
          logger.debug("Deleting VM " + iid);
          dbClient.deleteVMInformation(iid);
        }
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
  
  //@Test
  public void testSetupCompletion()
  {
    logger.info("Set up ran to completion");
  }
  
  /*
   * A test to make sure that the main app doesn't start any unwanted session
   */
  @Test
  @Order(1)
  public void NoFreeVms()
  {
    logger.info("Starting NoFreeVms Test!");
    
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
   */
  @Test
  @Order(2)
  public void NoFreeVms_Docker()
  {
    logger.info("Starting NoFreeVms_Docker Test!");
    
    ObjectNode res_cfg = CcdpUtils.getResourceCfg("DOCKER").deepCopy();
    res_cfg.put("min-number-free-agents", 0);
    CcdpUtils.setResourceCfg("DOCKER", res_cfg); 
    res_cfg = CcdpUtils.getResourceCfg("EC2").deepCopy();
    res_cfg.put("min-number-free-agents", 1);
    CcdpUtils.setResourceCfg("EC2", res_cfg);
    res_cfg = CcdpUtils.getResourceCfg("DEFAULT").deepCopy();
    res_cfg.put("min-number-free-agents", 0);
    CcdpUtils.setResourceCfg("DEFAULT", res_cfg);
    
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
   */
  @Test
  @Order(3)
  public void NoFreeVms_EC2()
  {
    logger.info("Starting NoFreeVms_EC2 Test!");
    
    ObjectNode res_cfg = CcdpUtils.getResourceCfg("DOCKER").deepCopy();
    res_cfg.put("min-number-free-agents", 2);
    CcdpUtils.setResourceCfg("DOCKER", res_cfg); 
    res_cfg = CcdpUtils.getResourceCfg("EC2").deepCopy();
    res_cfg.put("min-number-free-agents", 0);
    CcdpUtils.setResourceCfg("EC2", res_cfg);
    res_cfg = CcdpUtils.getResourceCfg("DEFAULT").deepCopy();
    res_cfg.put("min-number-free-agents", 0);
    CcdpUtils.setResourceCfg("DEFAULT", res_cfg);
    
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
   */
  //@Test
  public void NoFreeVms_Default()
  {
    logger.info("Starting NoFreeVms_Default Test!");
    
    ObjectNode res_cfg = CcdpUtils.getResourceCfg("DOCKER").deepCopy();
    res_cfg.put("min-number-free-agents", 1);
    CcdpUtils.setResourceCfg("DOCKER", res_cfg); 
    res_cfg = CcdpUtils.getResourceCfg("EC2").deepCopy();
    res_cfg.put("min-number-free-agents", 0);
    CcdpUtils.setResourceCfg("EC2", res_cfg);
    res_cfg = CcdpUtils.getResourceCfg("DEFAULT").deepCopy();
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
  @Order(4)
  public void OneFreeVm_Docker()
  {
    logger.info("Starting OneFreeVm_Docker Test!");
    
    // Set in the config that there should be 1 free Docker agent
    ObjectNode res_cfg = CcdpUtils.getResourceCfg("DOCKER").deepCopy();
    res_cfg.put("min-number-free-agents", 1);
    CcdpUtils.setResourceCfg("DOCKER", res_cfg); 
    res_cfg = CcdpUtils.getResourceCfg("EC2").deepCopy();
    res_cfg.put("min-number-free-agents", 2);
    CcdpUtils.setResourceCfg("EC2", res_cfg);
    res_cfg = CcdpUtils.getResourceCfg("DEFAULT").deepCopy();
    res_cfg.put("min-number-free-agents", 0);
    CcdpUtils.setResourceCfg("DEFAULT", res_cfg);
    
    // Start the engine and wait for it to get started
    engine = new CcdpMainApplication(null);
    CcdpUtils.pause(80);
    
    // Check for the free agent
    running_vms = engine.getAllCcdpVMResourcesOfType("DOCKER");
    assertTrue("There should be a running VM", running_vms.size() == 1);
    
    // Check for node type
    CcdpVMResource vm = running_vms.get(0);
    assertTrue("The node should be of type DOCKER", "DOCKER".equals(vm.getNodeType()));
    
    // Check with Mongo to verify
    String vmId = vm.getInstanceId();
    long initialTime = dbClient.getVMInformation(vmId).getLastUpdatedTime();
    CcdpUtils.pause(30);
    assertFalse("There was no Mongo heartbeat", initialTime == dbClient.getVMInformation(vmId).getLastUpdatedTime());
  }
  
  /*
   * A test to create 1 free EC2 VM for use
   */
  @Test
  @Order(5)
  public void OneFreeVm_EC2()
  {
    logger.info("Starting OneFreeVm_EC2 Test!");
    
    // Set in the config that there should be 1 free Docker agent
    ObjectNode res_cfg = CcdpUtils.getResourceCfg("DOCKER").deepCopy();
    res_cfg.put("min-number-free-agents", 2);
    CcdpUtils.setResourceCfg("DOCKER", res_cfg); 
    res_cfg = CcdpUtils.getResourceCfg("EC2").deepCopy();
    res_cfg.put("min-number-free-agents", 1);
    CcdpUtils.setResourceCfg("EC2", res_cfg);
    res_cfg = CcdpUtils.getResourceCfg("DEFAULT").deepCopy();
    res_cfg.put("min-number-free-agents", 0);
    CcdpUtils.setResourceCfg("DEFAULT", res_cfg);
    
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
   */
  //@Test
  //@Order(6)
  public void OneFreeVm_Default()
  {
    logger.info("Starting OneFreeVm_Default Test!");
    
    // Set in the config that there should be 1 free Default agent
    ObjectNode res_cfg = CcdpUtils.getResourceCfg("DOCKER").deepCopy();
    res_cfg.put("min-number-free-agents", 1);
    CcdpUtils.setResourceCfg("DOCKER", res_cfg); 
    res_cfg = CcdpUtils.getResourceCfg("EC2").deepCopy();
    res_cfg.put("min-number-free-agents", 0);
    CcdpUtils.setResourceCfg("EC2", res_cfg);
    res_cfg = CcdpUtils.getResourceCfg("DEFAULT").deepCopy();
    res_cfg.put("min-number-free-agents", 1);
    CcdpUtils.setResourceCfg("DEFAULT", res_cfg);
    
    // Start the engine and wait for it to get started
    engine = new CcdpMainApplication(null);
    logger.debug("Waiting 80 seconds for VM to spawn");
    CcdpUtils.pause(80);
    
    // Check for the free agent
    running_vms = engine.getAllCcdpVMResourcesOfType("EC2");
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
   */
  @Test
  @Order(6)
  public void DockerStartupTask() 
  {
    logger.info("Starting DockerStartupTask Test!");
    
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
    engine = new CcdpMainApplication(System.getenv("CCDP_HOME") + "/data/new_tests/startupUnitTest_docker.json");
    CcdpUtils.pause(25);
   
    logger.debug("Checking for 1 VM w/ Task");
    running_vms = engine.getAllCcdpVMResources();
    assertTrue("There should only be 1 VM running.", running_vms.size() == 1);
    CcdpVMResource vm = running_vms.get(0);
    assertTrue("The VM should be of node type Docker","DOCKER".equals(vm.getNodeType()));
    assertTrue("The VM should have a task", vm.getNumberTasks() > 0);
    
    // Let task complete, should despawn VM 
    CcdpUtils.pause(75);
    logger.debug("Checking for despawn");
    running_vms = engine.getAllCcdpVMResources();
    assertTrue("The tasked VM should've despawned by now", running_vms.size() == 0);
  }
  
  /*
   * This test shows that the MainApp can launch a AWS VM that executes a task on creation
   */
  @Test
  @Order(7)
  public void EC2StartupTask() 
  {
    logger.info("Starting EC2StartupTask Test!");
    
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
    engine = new CcdpMainApplication(System.getenv("CCDP_HOME") + "/data/new_tests/startupUnitTest_ec2.json");
    CcdpUtils.pause(50);
    
    logger.debug("Checking Size and Node-type");
    running_vms = engine.getAllCcdpVMResources();
    assertTrue("There should only be 1 VM running.", running_vms.size() == 1);
    CcdpVMResource vm = running_vms.get(0);
    assertTrue("The VM should be of node type EC2","EC2".equals(vm.getNodeType()));
    assertTrue("The VM should have a task", vm.getNumberTasks() > 0);
    
    // Let task complete, should despawn VM 
    CcdpUtils.pause(75);
    logger.debug("Checking Despawn");
    running_vms = engine.getAllCcdpVMResources();
    assertTrue("The tasked VM should've despawned by now", running_vms.size() == 0);
  }
  
  /*
   * This test shows that the MainApp can launch a default VM that executes a task on creation
   */
  //@Test
  public void DefaultStartupTask() 
  {
    logger.info("Starting DefaultStartupTask Test!");
    
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
    engine = new CcdpMainApplication(System.getenv("CCDP_HOME") + "/data/new_tests/startupUnitTest_default.json");
    CcdpUtils.pause(50);
   
    logger.debug("Checking node count and type");
    running_vms = engine.getAllCcdpVMResources();
    assertTrue("There should only be 1 VM running.", running_vms.size() == 1);
    CcdpVMResource vm = running_vms.get(0);
    
    // Node type for defaults is EC2, see Default task above....
    assertTrue("The VM should be of node type DEFAULT","EC2".equals(vm.getNodeType()));
    assertTrue("The VM should have a task", vm.getNumberTasks() > 0);
    
    // Let task complete, should despawn VM 
    CcdpUtils.pause(65);
    running_vms = engine.getAllCcdpVMResources();
    assertTrue("The tasked VM should've despawned by now", running_vms.size() == 0);
  }
  
  /*
   * This test determines if Docker containers are spawned an terminated correctly to keep the
   * number of free agents correctly
   */
  @Test
  @Order(8)
  public void SpawnAndDespawnDocker()
  {
    logger.info("Starting DockerSpawnAndDespawn Test!");
    
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
    String task_filename = System.getenv("CCDP_HOME") + "/data/new_tests/startupUnitTest_docker.json";
    this.sendJob(task_filename);
    
    // Wait for new VM to spawn up
    CcdpUtils.pause(25);
    
    // Test proper execution
    logger.debug("Checking to see if there is 2 VMs");
    running_vms = engine.getAllCcdpVMResources();
    assertTrue("There should be two VMs running", running_vms.size() == 2);
    for (CcdpVMResource vm : running_vms)
    {
      if ( vm.getInstanceId().equals(original))
        assertTrue("The original VM should have the assigned task", vm.getNumberTasks() > 0);
      assertTrue("The nodes should be of type Docker", vm.getNodeType().equals("DOCKER"));
    }
    
    //Wait for task to complete
    CcdpUtils.pause(55);
    logger.debug("Task should be done now, check there is only 1 VM and it isn't the original");
    running_vms = engine.getAllCcdpVMResources();
    assertTrue("One of the VMs shoud've been stopped", running_vms.size() == 1);
    assertFalse("The original VM should have been despawned", running_vms.get(0).getInstanceId().equals(original));
  }
  
  /*
   * This test determines if EC2 instances are spawned an terminated correctly to keep the
   * number of free agents correctly
   */
  @Test
  @Order(9)
  public void SpawnAndDespawnEC2()
  {
    logger.info("Starting DockerSpawnAndDespawn Test!");
    
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
    CcdpUtils.pause(50);
    
    logger.debug("Check that there is still only 1 VM");
    running_vms = engine.getAllCcdpVMResources();
    assertTrue("There should only be 1 VM", running_vms.size() == 1);
    String original = running_vms.get(0).getInstanceId();
    
    // Send task, it should spawn a new vm and give the task to the old vm
    String task_filename = System.getenv("CCDP_HOME") + "/data/new_tests/startupUnitTest_ec2.json";
    this.sendJob(task_filename);
    
    // Wait for new VM to spawn up
    CcdpUtils.pause(35);
    
    // Test proper execution
    logger.debug("Checking to see if there is 2 VMs");
    running_vms = engine.getAllCcdpVMResources();
    assertTrue("There should be two VMs running", running_vms.size() == 2);
    for (CcdpVMResource vm : running_vms)
    {
      if ( vm.getInstanceId().equals(original))
        assertTrue("The original VM should have the assigned task", vm.getNumberTasks() > 0);
      assertTrue("The nodes should be of type EC2", vm.getNodeType().equals("EC2"));
    }
    
    //Wait for task to complete
    CcdpUtils.pause(70);
    logger.debug("Task should be done now, check there is only 1 VM and it isn't the original");
    running_vms = engine.getAllCcdpVMResources();
    assertTrue("One of the VMs shoud've been stopped", running_vms.size() == 1);
    assertFalse("The original VM should have been despawned", running_vms.get(0).getInstanceId().equals(original));
  }
  
  /*
   * This test spawns a docker instance to do a task after a VM of another type
   * is already running
   */
  @Test
  @Order(10)
  public void spawnDockerForTask()
  {
    logger.info("Starting DockerSpawnAndDespawn Test!");
    
    ObjectNode res_cfg = CcdpUtils.getResourceCfg("DOCKER").deepCopy();
    res_cfg.put("min-number-free-agents", 0);
    CcdpUtils.setResourceCfg("DOCKER", res_cfg); 
    res_cfg = CcdpUtils.getResourceCfg("EC2").deepCopy();
    res_cfg.put("min-number-free-agents", 2);
    CcdpUtils.setResourceCfg("EC2", res_cfg);
    res_cfg = CcdpUtils.getResourceCfg("DEFAULT").deepCopy();
    res_cfg.put("min-number-free-agents", 0);
    CcdpUtils.setResourceCfg("DEFAULT", res_cfg);
    
    // Start engine and give free agent time to spawn
    logger.debug("Starting engine and spawning FA");
    engine = new CcdpMainApplication(null);
    CcdpUtils.pause(50);
    
    logger.debug("Check that there are still only 2 VMs");
    running_vms = engine.getAllCcdpVMResources();
    assertTrue("There should only be 2 VMs", running_vms.size() == 2);
    List<String> originals = new ArrayList<>();
    for (CcdpVMResource running : running_vms)
    {
      String id = running.getInstanceId();
      originals.add(id);
    }
    
    // Send task, it should spawn a new vm and give the task to the old vm
    String task_filename = System.getenv("CCDP_HOME") + "/data/new_tests/startupUnitTest_docker.json";
    this.sendJob(task_filename);
    
    // Wait for new VM to spawn up
    CcdpUtils.pause(30);
    
    // Test proper execution
    logger.debug("Checking to see if there is 3 VMs");
    running_vms = engine.getAllCcdpVMResources();
    assertTrue("There should be 3 VMs running", running_vms.size() == 3);
    for (CcdpVMResource vm : running_vms)
    {
      if ( !originals.contains(vm.getInstanceId()) )
      {
        assertTrue("The Docker VM should have the assigned task", vm.getNumberTasks() > 0);
      }
    }
    
    //Wait for task to complete
    CcdpUtils.pause(70);
    logger.debug("Task should be done now, check there is only 2 VMs");
    running_vms = engine.getAllCcdpVMResources();
    assertTrue("One of the VMs shoud've been stopped", running_vms.size() == 2);
    for (CcdpVMResource vm : running_vms)
    {
      if ( "DOCKER".equals(vm.getNodeType()) )
      {
        fail("The Docker VM is running still.");
      }
    }

  }
  
  /*
   * This test spawns a EC2 instance to do a task after a VM of another type
   * is already running
   */
  @Test
  @Order(11)
  public void spawnEC2ForTask()
  {
    logger.info("Starting EC2SpawnAndDespawn Test!");
    
    ObjectNode res_cfg = CcdpUtils.getResourceCfg("DOCKER").deepCopy();
    res_cfg.put("min-number-free-agents", 2);
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
    CcdpUtils.pause(45);
    
    logger.debug("Check that there are still only 2 VMs");
    running_vms = engine.getAllCcdpVMResources();
    assertTrue("There should only be 2 VMs", running_vms.size() == 2);
    List<String> originals = new ArrayList<>();
    for (CcdpVMResource running : running_vms)
    {
      String id = running.getInstanceId();
      originals.add(id);
    }
    
    // Send task, it should spawn a new vm and give the task to the old vm
    String task_filename = System.getenv("CCDP_HOME") + "/data/new_tests/startupUnitTest_ec2.json";
    this.sendJob(task_filename);
    
    // Wait for new VM to spawn up
    CcdpUtils.pause(60);
    
    // Test proper execution
    logger.debug("Checking to see if there is 3 VMs");
    running_vms = engine.getAllCcdpVMResources();
    assertTrue("There should be 3 VMs running", running_vms.size() == 3);
    for (CcdpVMResource vm : running_vms)
    {
      if ( !originals.contains(vm.getInstanceId()) )
      {
        assertTrue("The Docker VM should have the assigned task", vm.getNumberTasks() > 0);
      }
    }
    
    //Wait for task to complete
    CcdpUtils.pause(70);
    logger.debug("Task should be done now, check there is only 2 VMs");
    running_vms = engine.getAllCcdpVMResources();
    assertTrue("One of the VMs shoud've been stopped", running_vms.size() == 2);
    for (CcdpVMResource vm : running_vms)
    {
      if ( "EC2".equals(vm.getNodeType()) )
      {
        fail("The Docker VM is running still.");
      }
    }

  }
  
  /*
   * This test spawns a docker instance to do a task after a VM of another type
   * is already running
   */
  //@Test
  public void spawnDefaultForTask()
  {
    logger.info("Starting DefaultSpawnAndDespawn Test!");
    
    ObjectNode res_cfg = CcdpUtils.getResourceCfg("DOCKER").deepCopy();
    res_cfg.put("min-number-free-agents", 2);
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
    CcdpUtils.pause(45);
    
    logger.debug("Check that there are still only 2 VMs");
    running_vms = engine.getAllCcdpVMResources();
    assertTrue("There should only be 2 VMs", running_vms.size() == 2);
    List<String> originals = new ArrayList<>();
    for (CcdpVMResource running : running_vms)
    {
      String id = running.getInstanceId();
      originals.add(id);
    }
    
    // Send task, it should spawn a new vm and give the task to the old vm
    String task_filename = System.getenv("CCDP_HOME") + "/data/new_tests/startupUnitTest_default.json";
    this.sendJob(task_filename);
    
    // Wait for new VM to spawn up
    CcdpUtils.pause(60);
    
    // Test proper execution
    logger.debug("Checking to see if there is 3 VMs");
    running_vms = engine.getAllCcdpVMResources();
    assertTrue("There should be 3 VMs running", running_vms.size() == 3);
    for (CcdpVMResource vm : running_vms)
    {
      if ( !originals.contains(vm.getInstanceId()) )
      {
        assertTrue("The Default VM should have the assigned task", vm.getNumberTasks() > 0);
      }
    }
    
    //Wait for task to complete
    CcdpUtils.pause(65);
    logger.debug("Task should be done now, check there is only 2 VMs");
    running_vms = engine.getAllCcdpVMResources();
    assertTrue("One of the VMs shoud've been stopped", running_vms.size() == 2);
    for (CcdpVMResource vm : running_vms)
    {
      if ( "EC2".equals(vm.getNodeType()) )
      {
        fail("The Docker VM is running still.");
      }
    }
  }
  
  /*
   * This test assigns tasks to a VM until it exceeds the max number of tasks
   * allowed on a VM, and see if a new VM is spawned
   * ENSURE THAT NUMBERTASKSCONTROLLERIMPL IS SET IN CCDP-CONFIG
   */
  @Test
  @Order(12)
  public void NumberTasksControllerTest()
  {
    logger.info("Starting NumberTasksController Test!");
        
    // Set no free agents
    ObjectNode res_cfg = CcdpUtils.getResourceCfg("DOCKER").deepCopy();
    res_cfg.put("min-number-free-agents", 0);
    CcdpUtils.setResourceCfg("DOCKER", res_cfg); 
    res_cfg = CcdpUtils.getResourceCfg("EC2").deepCopy();
    res_cfg.put("min-number-free-agents", 0);
    CcdpUtils.setResourceCfg("EC2", res_cfg);
    res_cfg = CcdpUtils.getResourceCfg("DEFAULT").deepCopy();
    res_cfg.put("min-number-free-agents", 0);
    CcdpUtils.setResourceCfg("DEFAULT", res_cfg);
    
    // Start the engine and let it configure
    logger.debug("Starting engine");
    engine = new CcdpMainApplication(null);
    CcdpUtils.pause(10);
    running_vms = engine.getAllCcdpVMResources();
    assertTrue("There shouldn't be any VMs running right now", running_vms.size() == 0);
    
    // Assign 5 tasks
    String task_filename = System.getenv("CCDP_HOME") + "/data/new_tests/numTasksUnitTest_docker.json";       
    this.sendJob(task_filename);
    
    CcdpUtils.pause(25);
    // Check there is one VM with five tasks
    logger.debug("Checking for 1 VM with 5 Tasks");
    running_vms = engine.getAllCcdpVMResources();
    assertTrue("There should be 1 VM", running_vms.size() == 1);
    CcdpVMResource vm = running_vms.get(0);
    assertTrue("The VM should have 5 tasks", vm.getNumberTasks() == 5);
    
    String originalID = vm.getInstanceId();
    
    // Send one more task
    task_filename = System.getenv("CCDP_HOME") + "/data/new_tests/startupUnitTest_docker.json";

    this.sendJob(task_filename);
    CcdpUtils.pause(20);
    
    //Check VMs state
    logger.debug("Check if there are 2 VMs");
    running_vms = engine.getAllCcdpVMResources();
    assertTrue("There should be 2 VMs", running_vms.size() == 2);
    for ( CcdpVMResource res : running_vms)
    {
      if (res.getInstanceId().equals(originalID))
        assertTrue("This VM should have 5 tasks", res.getNumberTasks() == 5);
      else
        assertTrue("This VM should only have 1 task", res.getNumberTasks() == 1);
    }
    
    //Wait for everything to complete
    CcdpUtils.pause(45);
    logger.debug("Checking original VM termianted");
    running_vms = engine.getAllCcdpVMResources();
    assertTrue("There should only be 1 VM running", running_vms.size() == 1);
    assertTrue("The 5 tasked VM should have despawned", running_vms.get(0).getInstanceId() != originalID);
    
    CcdpUtils.pause(50);
    logger.debug("Checking all VMs were removed");
    running_vms = engine.getAllCcdpVMResources();
    assertTrue("All VMs should have despawned", running_vms.size() == 0);
  }
  
  /*
   * This test sends a task that should spawn a session in "single-tasked" mode, a mode designed
   * for tasks that require a lot of cpu. Then another regular task is sent. A second VM should
   * be spawned for this
   */
  @Test
  @Order(13)
  public void singleTaskedTest()
  {
    logger.info("Starting singleTasked Test!");
        
    // Set no free agents
    ObjectNode res_cfg = CcdpUtils.getResourceCfg("DOCKER").deepCopy();
    res_cfg.put("min-number-free-agents", 0);
    CcdpUtils.setResourceCfg("DOCKER", res_cfg); 
    res_cfg = CcdpUtils.getResourceCfg("EC2").deepCopy();
    res_cfg.put("min-number-free-agents", 0);
    CcdpUtils.setResourceCfg("EC2", res_cfg);
    res_cfg = CcdpUtils.getResourceCfg("DEFAULT").deepCopy();
    res_cfg.put("min-number-free-agents", 0);
    CcdpUtils.setResourceCfg("DEFAULT", res_cfg);
    
    // Start the engine and let it configure
    logger.debug("Starting engine");
    engine = new CcdpMainApplication(null);
    CcdpUtils.pause(10);
    running_vms = engine.getAllCcdpVMResources();
    assertTrue("There shouldn't be any VMs running right now", running_vms.size() == 0);
    
    // Assign 110 CPU task
    String task_filename = System.getenv("CCDP_HOME") + "/data/new_tests/singleTasked_docker.json";
    this.sendJob(task_filename);
    
    // Assign 2 regular CPU task
    task_filename = System.getenv("CCDP_HOME") + "/data/new_tests/startupUnitTest_docker.json";
    int sentTasks = 0;
    final int maxNumTasks = 2;
    while (sentTasks < maxNumTasks)
    {
      this.sendJob(task_filename);
      sentTasks++;
    }
    CcdpUtils.pause(25);
    
    // Check for 2 VMs
    running_vms = engine.getAllCcdpVMResources();
    assertTrue("There should be two VMs", running_vms.size() == 2);
    for ( CcdpVMResource res : running_vms)
    {
      if ( !res.isSingleTasked() )
        assertTrue("This VM should have 2 tasks", res.getNumberTasks() == 2);
      else
        assertTrue("A single-tasked VM should only have 1 task, duh", res.getNumberTasks() == 1);
    }
    
    CcdpUtils.pause(75);
    running_vms = engine.getAllCcdpVMResources();
    assertTrue("There should be no VMs", running_vms.size() == 0);
  } 
  
  /*
   * This test assigns tasks to a VM until it exceeds the max number of tasks
   * allowed on a VM, and see if a new VM is spawned
   * ENSURE THAT AVGLOADCONTROLLERIMPL IS SET IN CCDP-CONFIG
   * This no work
   */
  //@Test
  public void AvgLoadControllerTest()
  {
    logger.info("Starting AvgLoadController Test!");
    final int NumTasksToLaunch = 5;
        
    // Set no free agents
    ObjectNode res_cfg = CcdpUtils.getResourceCfg("DOCKER").deepCopy();
    res_cfg.put("min-number-free-agents", 0);
    CcdpUtils.setResourceCfg("DOCKER", res_cfg); 
    res_cfg = CcdpUtils.getResourceCfg("EC2").deepCopy();
    res_cfg.put("min-number-free-agents", 0);
    CcdpUtils.setResourceCfg("EC2", res_cfg);
    res_cfg = CcdpUtils.getResourceCfg("DEFAULT").deepCopy();
    res_cfg.put("min-number-free-agents", 0);
    CcdpUtils.setResourceCfg("DEFAULT", res_cfg);
    
    // Start the engine and let it configure
    logger.debug("Starting engine");
    engine = new CcdpMainApplication(null);
    CcdpUtils.pause(10);
    running_vms = engine.getAllCcdpVMResources();
    assertTrue("There shouldn't be any VMs running right now", running_vms.size() == 0);
    
    // Assign 5 tasks
    String task_filename = System.getenv("CCDP_HOME") + "/data/new_tests/numTasksUnitTest_docker.json";
    int sentTasks = 0;
    
    while (sentTasks < NumTasksToLaunch)
    {
      this.sendJob(task_filename);
      sentTasks++;
    }
    CcdpUtils.pause(35);
    // Check there is one VM with three tasks
    logger.debug("Checking for 1 VM with 5 Tasks");
    running_vms = engine.getAllCcdpVMResources();
    //assertTrue("There should be 1 VM", running_vms.size() == 1);
    //CcdpVMResource vm = running_vms.get(0);
    //assertTrue("The VM should have 5 tasks", vm.getNumberTasks() == 5);
    
    //String originalID = vm.getInstanceId();
    
    // Send one more task
    task_filename = System.getenv("CCDP_HOME") + "/data/new_tests/startupUnitTest_docker.json";
    this.sendJob(task_filename);
    CcdpUtils.pause(10);
    
    //Check VMs state
    logger.debug("Check if there are 2 VMs");
    running_vms = engine.getAllCcdpVMResources();
    //assertTrue("There should be 2 VMs", running_vms.size() == 2);
    //for ( CcdpVMResource res : running_vms)
    //{
    //   //assertTrue("This VM should have 5 tasks", res.getNumberTasks() == 5);
    //  else
        //assertTrue("This VM should only have 1 task", res.getNumberTasks() == 1);
   // }
    
    //Wait for everything to complete
    CcdpUtils.pause(30);
    logger.debug("Checking original VM termianted");
    running_vms = engine.getAllCcdpVMResources();
    //assertTrue("There should only be 1 VM running", running_vms.size() == 1);
    //assertTrue("The 5 tasked VM should have despawned", running_vms.get(0).getInstanceId() != originalID);
    
    CcdpUtils.pause(30);
    logger.debug("Checking all VMs were removed");
    running_vms = engine.getAllCcdpVMResources();
    //assertTrue("All VMs should have despawned", running_vms.size() == 0);
  }
  
  /*
   * This test spawns assigns a task to both Docker and EC2 agents to be sure that the proper
   * VMs are given the tasks
   */ 
  @Test
  @Order(14)
  public void DockerAndEC2Task()
  {
    logger.info("Starting DockerAndEC2Task Test!");
        
    // Set no free agents
    ObjectNode res_cfg = CcdpUtils.getResourceCfg("DOCKER").deepCopy();
    res_cfg.put("min-number-free-agents", 0);
    CcdpUtils.setResourceCfg("DOCKER", res_cfg); 
    res_cfg = CcdpUtils.getResourceCfg("EC2").deepCopy();
    res_cfg.put("min-number-free-agents", 0);
    CcdpUtils.setResourceCfg("EC2", res_cfg);
    res_cfg = CcdpUtils.getResourceCfg("DEFAULT").deepCopy();
    res_cfg.put("min-number-free-agents", 0);
    CcdpUtils.setResourceCfg("DEFAULT", res_cfg);
    
    // Start the engine and let it configure
    logger.debug("Starting engine");
    engine = new CcdpMainApplication(null);
    CcdpUtils.pause(10);
    running_vms = engine.getAllCcdpVMResources();
    assertTrue("There shouldn't be any VMs running right now", running_vms.size() == 0);
    
    String task_filename = System.getenv("CCDP_HOME") + "/data/new_tests/startupUnitTest_ec2.json";
    this.sendJob(task_filename);
    task_filename = System.getenv("CCDP_HOME") + "/data/new_tests/startupUnitTest_docker.json";
    CcdpUtils.pause(35);
    this.sendJob(task_filename);
    CcdpUtils.pause(35);
    
    logger.debug("Checking for two VMs with tasks");
    running_vms = engine.getAllCcdpVMResources();
    assertTrue("There should be 2 VMs running", running_vms.size() == 2);
    
    for (CcdpVMResource res : running_vms)
    {
      if ( res.getNodeType().equals("DOCKER") )
      {
        assertTrue("The docker VM should have 1 task", res.getNumberTasks() == 1);
      }
      else if ( res.getNodeType().equals("EC2") )
      {
        assertTrue("The docker VM should have 1 task", res.getNumberTasks() == 1);
      }
      else
        fail("There should only be Docker and Ec2 instances.");
    }
    
    CcdpUtils.pause(75);
    running_vms = engine.getAllCcdpVMResources();
    assertTrue("Both VMs should have despawned by now", running_vms.size() == 0);
  }
  
  /*
   * This test spawns assigns a task to both Docker and EC2 agents to be sure that the proper
   * VMs are given the tasks
   */
  
  //@Test
  public void DockerAndDefaultTask()
  {
    logger.info("Starting DockerAndDefaultTask Test!");
        
    // Set no free agents
    ObjectNode res_cfg = CcdpUtils.getResourceCfg("DOCKER").deepCopy();
    res_cfg.put("min-number-free-agents", 0);
    CcdpUtils.setResourceCfg("DOCKER", res_cfg); 
    res_cfg = CcdpUtils.getResourceCfg("EC2").deepCopy();
    res_cfg.put("min-number-free-agents", 0);
    CcdpUtils.setResourceCfg("EC2", res_cfg);
    res_cfg = CcdpUtils.getResourceCfg("DEFAULT").deepCopy();
    res_cfg.put("min-number-free-agents", 0);
    CcdpUtils.setResourceCfg("DEFAULT", res_cfg);
    
    // Start the engine and let it configure
    logger.debug("Starting engine");
    engine = new CcdpMainApplication(null);
    CcdpUtils.pause(10);
    running_vms = engine.getAllCcdpVMResources();
    assertTrue("There shouldn't be any VMs running right now", running_vms.size() == 0);
    
    String task_filename = System.getenv("CCDP_HOME") + "/data/new_tests/startupUnitTest_default.json";
    this.sendJob(task_filename);
    task_filename = System.getenv("CCDP_HOME") + "/data/new_tests/startupUnitTest_docker.json";
    CcdpUtils.pause(35);
    this.sendJob(task_filename);
    CcdpUtils.pause(35);
    
    logger.debug("Checking for two VMs with tasks");
    running_vms = engine.getAllCcdpVMResources();
    assertTrue("There should be 2 VMs running", running_vms.size() == 2);
    
    for (CcdpVMResource res : running_vms)
    {
      if ( res.getNodeType().equals("DOCKER") )
      {
        assertTrue("The docker VM should have 1 task", res.getNumberTasks() == 1);
      }
      else if ( res.getNodeType().equals("EC2") )
      {
        assertTrue("The docker VM should have 1 task", res.getNumberTasks() == 1);
      }
      else
        fail("There should only be Docker and EC2 instances.");
    }
    
    CcdpUtils.pause(75);
    running_vms = engine.getAllCcdpVMResources();
    assertTrue("Both VMs should have despawned by now", running_vms.size() == 0);
  }
  
  /*
   * This test spawns assigns a task to both Default and EC2 agents to be sure that the proper
   * VMs are given the tasks
   */
  
  //@Test
  public void EC2AndDefaultTask()
  {
    logger.info("Starting DockerAndDefaultTask Test!");
        
    // Set no free agents
    ObjectNode res_cfg = CcdpUtils.getResourceCfg("DOCKER").deepCopy();
    res_cfg.put("min-number-free-agents", 0);
    CcdpUtils.setResourceCfg("DOCKER", res_cfg); 
    res_cfg = CcdpUtils.getResourceCfg("EC2").deepCopy();
    res_cfg.put("min-number-free-agents", 0);
    CcdpUtils.setResourceCfg("EC2", res_cfg);
    res_cfg = CcdpUtils.getResourceCfg("DEFAULT").deepCopy();
    res_cfg.put("min-number-free-agents", 0);
    CcdpUtils.setResourceCfg("DEFAULT", res_cfg);
    
    // Start the engine and let it configure
    logger.debug("Starting engine");
    engine = new CcdpMainApplication(null);
    CcdpUtils.pause(10);
    running_vms = engine.getAllCcdpVMResources();
    assertTrue("There shouldn't be any VMs running right now", running_vms.size() == 0);
    
    String task_filename = System.getenv("CCDP_HOME") + "/data/new_tests/startupUnitTest_default.json";
    this.sendJob(task_filename);
    task_filename = System.getenv("CCDP_HOME") + "/data/new_tests/startupUnitTest_ec2.json";
    this.sendJob(task_filename);
    CcdpUtils.pause(50);
    
    logger.debug("Checking for two VMs with tasks");
    running_vms = engine.getAllCcdpVMResources();
    assertTrue("There should be 2 VMs running", running_vms.size() == 2);
    
    for (CcdpVMResource res : running_vms)
    {
      if ( res.getNodeType().equals("DEFAULT") )
      {
        assertTrue("The docker VM should have 1 task", res.getNumberTasks() == 1);
      }
      else if ( res.getNodeType().equals("EC2") )
      {
        assertTrue("The docker VM should have 1 task", res.getNumberTasks() == 1);
      }
      else
        fail("There should only be DEFAULT and EC2 instances.");
    }
    
    CcdpUtils.pause(75);
    running_vms = engine.getAllCcdpVMResources();
    assertTrue("Both VMs should have despawned by now", running_vms.size() == 0);
  }
  
  /*
   * This test spawns a docker and an ec2 instance for jobs contained in a single file
   */
  @Test
  @Order(15)
  public void combinedJobFileTest()
  {
    logger.info("Starting combinedJobFile Test!");
    
    ObjectNode res_cfg = CcdpUtils.getResourceCfg("DOCKER").deepCopy();
    res_cfg.put("min-number-free-agents", 0);
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
    CcdpUtils.pause(15);
    
    logger.debug("Check that there are still no VMs");
    running_vms = engine.getAllCcdpVMResources();
    assertTrue("There should only be no VMs", running_vms.size() == 0);
    
    // Send task, it should spawn a new vm and give the task to the old vm
    String task_filename = System.getenv("CCDP_HOME") + "/data/new_tests/docker_and_ec2_jobs.json";
    this.sendJob(task_filename);
    
    // Wait for new VM to spawn up
    CcdpUtils.pause(35);
    logger.debug("Checking node types and tasks");
    running_vms = engine.getAllCcdpVMResources();
    assertTrue("There should be 2 VMs running", running_vms.size() == 2);
    for (CcdpVMResource res : running_vms)
    {
      assertTrue("The node should be of type Docker or EC2", 
          res.getNodeType().equals("DOCKER") || res.getNodeType().equals("EC2"));
      
      if ( res.getNodeType().equals("DOCKER"))
      {
        assertTrue("The VM should have 1 task", res.getNumberTasks() == 1);
      }
      else if ( res.getNodeType().equals("EC2") )
      {
        assertTrue("The VM should have 1 task", res.getNumberTasks() == 1);
      }
    }
    
    //Wait for task to complete
    CcdpUtils.pause(80);
    logger.debug("Tasks should be done now");
    running_vms = engine.getAllCcdpVMResources();
    assertTrue("The VMs shoud've been stopped", running_vms.size() == 0);
  }
  
  /*
   * This test spawns two docker instances for jobs of different session contained in a single file
   */
  @Test
  @Order(16)
  public void differentSessionJobFileTest()
  {
    logger.info("Starting differentSessionJobFile Test!");
    
    ObjectNode res_cfg = CcdpUtils.getResourceCfg("DOCKER").deepCopy();
    res_cfg.put("min-number-free-agents", 0);
    CcdpUtils.setResourceCfg("DOCKER", res_cfg); 
    res_cfg = CcdpUtils.getResourceCfg("EC2").deepCopy();
    res_cfg.put("min-number-free-agents", 0);
    CcdpUtils.setResourceCfg("EC2", res_cfg);
    res_cfg = CcdpUtils.getResourceCfg("DEFAULT").deepCopy();
    res_cfg.put("min-number-free-agents", 0);
    CcdpUtils.setResourceCfg("DEFAULT", res_cfg);
    
    // Start engine and give free agent time to spawn
    logger.debug("Starting engine");
    engine = new CcdpMainApplication(null);
    CcdpUtils.pause(15);
    
    logger.debug("Check that there are still no VMs");
    running_vms = engine.getAllCcdpVMResources();
    assertTrue("There should only be no VMs", running_vms.size() == 0);
    
    // Send task, it should spawn a new vm and give the task to the old vm
    String task_filename = System.getenv("CCDP_HOME") + "/data/new_tests/two_job_docker_test.json";
    this.sendJob(task_filename);
    
    // Wait for new VM to spawn up
    CcdpUtils.pause(35);
    logger.debug("Checking node types and tasks");
    running_vms = engine.getAllCcdpVMResources();
    assertTrue("There should be 2 VMs running", running_vms.size() == 2);
    boolean test1 = false;
    for (CcdpVMResource res : running_vms)
    {
      assertTrue("The node should be of type Docker", res.getNodeType().equals("DOCKER"));
      
      if ( res.getAssignedSession().equals("test-1") ) 
      {
        assertTrue("Test-1 was found in a previous iteration", test1 == false);
        test1 = true;
      }   
    }
    
    //Wait for task to complete
    CcdpUtils.pause(80);
    logger.debug("Tasks should be done now");
    running_vms = engine.getAllCcdpVMResources();
    assertTrue("The VMs shoud've been stopped", running_vms.size() == 0);
  }
  
  /*
   * This test spawns two docker VM of different sessions with tasks, then adds a task
   * to one of the sessions, ensuring that there are still 2 VMs, 1 with 2 tasks
   */
  @Test
  @Order(17)
  public void addTaskToExisitingSessionTest()
  {
    logger.info("Starting differentSessionJobFile Test!");
    
    ObjectNode res_cfg = CcdpUtils.getResourceCfg("DOCKER").deepCopy();
    res_cfg.put("min-number-free-agents", 0);
    CcdpUtils.setResourceCfg("DOCKER", res_cfg); 
    res_cfg = CcdpUtils.getResourceCfg("EC2").deepCopy();
    res_cfg.put("min-number-free-agents", 0);
    CcdpUtils.setResourceCfg("EC2", res_cfg);
    res_cfg = CcdpUtils.getResourceCfg("DEFAULT").deepCopy();
    res_cfg.put("min-number-free-agents", 0);
    CcdpUtils.setResourceCfg("DEFAULT", res_cfg);
    
    // Start engine and give free agent time to spawn
    logger.debug("Starting engine");
    engine = new CcdpMainApplication(null);
    CcdpUtils.pause(15);
    
    logger.debug("Check that there are still no VMs");
    running_vms = engine.getAllCcdpVMResources();
    assertTrue("There should only be no VMs", running_vms.size() == 0);
    
    // Send task, it should spawn a new vm and give the task to the old vm
    String task_filename = System.getenv("CCDP_HOME") + "/data/new_tests/two_job_docker_test.json";
    this.sendJob(task_filename);
    
    // Wait for new VM to spawn up
    CcdpUtils.pause(35);
    logger.debug("Checking node types and tasks");
    running_vms = engine.getAllCcdpVMResources();
    assertTrue("There should be 2 VMs running", running_vms.size() == 2);
    boolean test1 = false;
    for (CcdpVMResource res : running_vms)
    {
      assertTrue("The node should be of type Docker", res.getNodeType().equals("DOCKER"));
      
      if ( res.getAssignedSession().equals("test-1") ) 
      {
        assertTrue("Test-1 was found in a previous iteration", test1 == false);
        test1 = true;
      }   
    }
    task_filename = System.getenv("CCDP_HOME") + "/data/new_tests/startupUnitTest_docker.json";
    this.sendJob(task_filename);
    
    CcdpUtils.pause(10);
    logger.debug("Checking node types and tasks, round 2");
    running_vms = engine.getAllCcdpVMResources();
    assertTrue("There should be 2 VMs running", running_vms.size() == 2);
    for (CcdpVMResource res : running_vms)
    {
      assertTrue("The node should be of type Docker", res.getNodeType().equals("DOCKER"));
      
      if ( res.getAssignedSession().equals("test-1") ) 
        assertTrue("test-1 session VM should have 2 tasks", res.getNumberTasks() == 2);
        
    }
    
    CcdpUtils.pause(30);
    logger.debug("Checking 1 VM was terminated post task");
    running_vms = engine.getAllCcdpVMResources();
    assertTrue("Session test-2 VM should have despawned", running_vms.size() == 1);
    CcdpVMResource vm = running_vms.get(0);
    assertTrue("The VM should be of session test-1", vm.getAssignedSession().equals("test-1"));
    
    //Wait for task to complete
    CcdpUtils.pause(55);
    logger.debug("Tasks should be done now");
    running_vms = engine.getAllCcdpVMResources();
    assertTrue("The VMs shoud've been stopped", running_vms.size() == 0);
  }
  
  /*
   * This test checks with the database to ensure that the correct number of controllers
   * is spawned for serverless tasks
   */
  @Test
  @Order(18)
  public void checkServerlessControllers()
  {
    logger.debug("Starting checkServerlessControllers test!");
    
    int numControllers = CcdpUtils.getServerlessTypes().size();
    ObjectNode res_cfg = CcdpUtils.getResourceCfg("DOCKER").deepCopy();
    res_cfg.put("min-number-free-agents", 0);
    CcdpUtils.setResourceCfg("DOCKER", res_cfg); 
    res_cfg = CcdpUtils.getResourceCfg("EC2").deepCopy();
    res_cfg.put("min-number-free-agents", 0);
    CcdpUtils.setResourceCfg("EC2", res_cfg);
    res_cfg = CcdpUtils.getResourceCfg("DEFAULT").deepCopy();
    res_cfg.put("min-number-free-agents", 0);
    CcdpUtils.setResourceCfg("DEFAULT", res_cfg);
      
    // Start engine and give free agent time to spawn
    logger.debug("Starting engine");
    engine = new CcdpMainApplication(null);
    CcdpUtils.pause(15);
    
    logger.debug("Check that there are still no VMs");
    running_vms = engine.getAllCcdpVMResources();
    assertTrue("There should only be no VMs", running_vms.size() == 0);
    
    logger.debug("Check that there are the corect number of controllers");
    serverless_controllers = engine.getAllCcdpServerlessResources();
    assertEquals("There should only be no VMs", serverless_controllers.size(), numControllers); 
  }
  
  /*
   * This test sends a serverless task and checks that it gets allocated with the correct controller
   */
  @Test
  @Order(19)
  public void OneTaskForLambda()
  {
    logger.debug("Starting checkServerlessControllers test!");
    
    int numControllers = CcdpUtils.getServerlessTypes().size();
    ObjectNode res_cfg = CcdpUtils.getResourceCfg("DOCKER").deepCopy();
    res_cfg.put("min-number-free-agents", 0);
    CcdpUtils.setResourceCfg("DOCKER", res_cfg); 
    res_cfg = CcdpUtils.getResourceCfg("EC2").deepCopy();
    res_cfg.put("min-number-free-agents", 0);
    CcdpUtils.setResourceCfg("EC2", res_cfg);
    res_cfg = CcdpUtils.getResourceCfg("DEFAULT").deepCopy();
    res_cfg.put("min-number-free-agents", 0);
    CcdpUtils.setResourceCfg("DEFAULT", res_cfg);
      
    // Start engine and give free agent time to spawn
    logger.debug("Starting engine");
    engine = new CcdpMainApplication(null);
    CcdpUtils.pause(15);
    
    logger.debug("Check that there are still no VMs");
    running_vms = engine.getAllCcdpVMResources();
    assertTrue("There should only be no VMs", running_vms.size() == 0);
    
    logger.debug("Check that there are the corect number of controllers");
    serverless_controllers = engine.getAllCcdpServerlessResources();
    assertEquals("There should only be no VMs", serverless_controllers.size(), numControllers); 
    
    String Filename = System.getenv("CCDP_HOME") + "/data/new_tests/AWSLambda-UnitTest.json/";
    this.sendJob(Filename);
    
    CcdpUtils.pause(10);
    
    running_vms = engine.getAllCcdpVMResources();
    serverless_controllers = engine.getAllCcdpServerlessResources();
    
    assertEquals("There should be no agents running.", running_vms.size(), 0);
    assertEquals("There should only be no VMs", serverless_controllers.size(), numControllers); 
    logger.debug("Check the serverless controller has the task");
    for (CcdpServerlessResource cont : serverless_controllers)
    {
      if ( cont.getNodeType().equals("AWS Lambda") )
        assertEquals("The AWS Controller should have the task", cont.getTasks().size(), 1);
      else
        assertEquals("Non-AWS Lambda controllers should have no tasks", cont.getTasks().size(), 0);
    }
    
    CcdpUtils.pause(30);
    
    logger.debug("Checking to see that the controller finished the task");
    running_vms = engine.getAllCcdpVMResources();
    serverless_controllers = engine.getAllCcdpServerlessResources();
    
    assertEquals("There should be no agents running.", running_vms.size(), 0);
    assertEquals("There should only be no VMs", serverless_controllers.size(), numControllers); 
    logger.debug("Check the serverless controller has the task");
    for (CcdpServerlessResource cont : serverless_controllers)
    {
      assertEquals("All controllers should have no tasks", cont.getTasks().size(), 0);
    }
  }
  /*
   * This test sends a serverless task and a server bound task checks that it gets allocated 
   * with the correct controller
   */
  @Test
  @Order(20)
  public void ServerlessAndServerboundJob()
  {
    logger.debug("Starting ServerlessAndServerboundJob test!");
    
    int numControllers = CcdpUtils.getServerlessTypes().size();
    ObjectNode res_cfg = CcdpUtils.getResourceCfg("DOCKER").deepCopy();
    res_cfg.put("min-number-free-agents", 0);
    CcdpUtils.setResourceCfg("DOCKER", res_cfg); 
    res_cfg = CcdpUtils.getResourceCfg("EC2").deepCopy();
    res_cfg.put("min-number-free-agents", 0);
    CcdpUtils.setResourceCfg("EC2", res_cfg);
    res_cfg = CcdpUtils.getResourceCfg("DEFAULT").deepCopy();
    res_cfg.put("min-number-free-agents", 0);
    CcdpUtils.setResourceCfg("DEFAULT", res_cfg);
      
    // Start engine and give free agent time to spawn
    logger.debug("Starting engine");
    engine = new CcdpMainApplication(null);
    CcdpUtils.pause(20);
    
    logger.debug("Check that there are still no VMs");
    running_vms = engine.getAllCcdpVMResources();
    assertTrue("There should only be no VMs", running_vms.size() == 0);
    
    logger.debug("Check that there are the corect number of controllers");
    serverless_controllers = engine.getAllCcdpServerlessResources();
    assertEquals("There should only be no VMs", serverless_controllers.size(), numControllers); 
    
    String Filename = System.getenv("CCDP_HOME") + "/data/new_tests/LambdaWithVMTask-UnitTest.json";
    this.sendJob(Filename);
    
    CcdpUtils.pause(15);
    
    running_vms = engine.getAllCcdpVMResources();
    serverless_controllers = engine.getAllCcdpServerlessResources();
    assertEquals("There should be 1 agents running.", running_vms.size(), 1);
    CcdpVMResource vm = running_vms.get(0);
    assertTrue("The VM should be Docker", vm.getNodeType().equals("DOCKER"));
    assertEquals("The VM should have 1 task", vm.getTasks().size(), 1);
    
    assertEquals("There should only be no VMs", serverless_controllers.size(), numControllers); 
    logger.debug("Check the serverless controller has the task");
    for (CcdpServerlessResource cont : serverless_controllers)
    {
      if ( cont.getNodeType().equals("AWS Lambda") )
        assertEquals("The AWS Controller should have the task", cont.getTasks().size(), 1);
      else
        assertEquals("Non-AWS Lambda controllers should have no tasks", cont.getTasks().size(), 0);
    }
    
    CcdpUtils.pause(65);
    
    logger.debug("Checking to see that the controller and VM finished the task");
    running_vms = engine.getAllCcdpVMResources();
    serverless_controllers = engine.getAllCcdpServerlessResources();
    
    assertEquals("There should be no agents running.", running_vms.size(), 0);
    assertEquals("There should be " + numControllers + " controllers", serverless_controllers.size(), numControllers); 
    logger.debug("Check the serverless controller has the task");
    for (CcdpServerlessResource cont : serverless_controllers)
    {
      assertEquals("All controllers should have no tasks", cont.getTasks().size(), 0);
    }
    
  }
  
  /*
   * This tests multiple jobs being sent to the serverless controller at the same time
   */
  @Test
  @Order(21)
  public void MultipleServerlessJobs()
  {
    logger.debug("Starting MultipleServerlessJobs test!");
    
    int numControllers = CcdpUtils.getServerlessTypes().size();
    ObjectNode res_cfg = CcdpUtils.getResourceCfg("DOCKER").deepCopy();
    res_cfg.put("min-number-free-agents", 0);
    CcdpUtils.setResourceCfg("DOCKER", res_cfg); 
    res_cfg = CcdpUtils.getResourceCfg("EC2").deepCopy();
    res_cfg.put("min-number-free-agents", 0);
    CcdpUtils.setResourceCfg("EC2", res_cfg);
    res_cfg = CcdpUtils.getResourceCfg("DEFAULT").deepCopy();
    res_cfg.put("min-number-free-agents", 0);
    CcdpUtils.setResourceCfg("DEFAULT", res_cfg);
      
    // Start engine and give free agent time to spawn
    logger.debug("Starting engine");
    engine = new CcdpMainApplication(null);
    CcdpUtils.pause(20);
    
    logger.debug("Check that there are still no VMs");
    running_vms = engine.getAllCcdpVMResources();
    assertTrue("There should only be no VMs", running_vms.size() == 0);
    
    logger.debug("Check that there are the corect number of controllers");
    serverless_controllers = engine.getAllCcdpServerlessResources();
    assertEquals("There was an incorect number of controllers", serverless_controllers.size(), numControllers); 
    
    int numJobs = 3;
    String Filename = System.getenv("CCDP_HOME") + "/data/new_tests/AWSLambda-UnitTest.json";
    for (int i = 0; i < numJobs; i++)
      this.sendJob(Filename);
    
    CcdpUtils.pause(10);
    
    running_vms = engine.getAllCcdpVMResources();
    serverless_controllers = engine.getAllCcdpServerlessResources();
    assertEquals("There should be no agents running.", running_vms.size(), 0);
    assertEquals("There should only be no VMs", serverless_controllers.size(), numControllers); 
    logger.debug("Check the serverless controller has the task");
    for (CcdpServerlessResource cont : serverless_controllers)
    {
      if ( cont.getNodeType().equals("AWS Lambda") )
        assertEquals("The AWS Controller should have the task", 3, cont.getTasks().size());
      else
        assertEquals("Non-AWS Lambda controllers should have no tasks", 0, cont.getTasks().size());
    }
    
    CcdpUtils.pause(25);
    
    logger.debug("Checking to see that the controller and VM finished the task");
    running_vms = engine.getAllCcdpVMResources();
    serverless_controllers = engine.getAllCcdpServerlessResources();
    
    assertEquals("There should be no agents running.", running_vms.size(), 0);
    assertEquals("There should only be no VMs", serverless_controllers.size(), numControllers); 
    logger.debug("Check the serverless controller has the task");
    for (CcdpServerlessResource cont : serverless_controllers)
    {
      assertEquals("All controllers should have no tasks", cont.getTasks().size(), 0);
    }
    
  }
  
  /*
   * This tests multiple jobs being sent to the serverless controller at the same time
   */
  @Test
  @Order(22)
  public void MultipleServerlessOfDifferentSession()
  {
    logger.debug("Starting MultipleServerlessJobs test!");
    
    int numControllers = CcdpUtils.getServerlessTypes().size();
    ObjectNode res_cfg = CcdpUtils.getResourceCfg("DOCKER").deepCopy();
    res_cfg.put("min-number-free-agents", 0);
    CcdpUtils.setResourceCfg("DOCKER", res_cfg); 
    res_cfg = CcdpUtils.getResourceCfg("EC2").deepCopy();
    res_cfg.put("min-number-free-agents", 0);
    CcdpUtils.setResourceCfg("EC2", res_cfg);
    res_cfg = CcdpUtils.getResourceCfg("DEFAULT").deepCopy();
    res_cfg.put("min-number-free-agents", 0);
    CcdpUtils.setResourceCfg("DEFAULT", res_cfg);
      
    // Start engine and give free agent time to spawn
    logger.debug("Starting engine");
    engine = new CcdpMainApplication(null);
    CcdpUtils.pause(20);
    
    logger.debug("Check that there are still no VMs");
    running_vms = engine.getAllCcdpVMResources();
    assertTrue("There should only be no VMs", running_vms.size() == 0);
    
    logger.debug("Check that there are the corect number of controllers");
    serverless_controllers = engine.getAllCcdpServerlessResources();
    assertEquals("There should only be no VMs", serverless_controllers.size(), numControllers); 
    
    String Filename = System.getenv("CCDP_HOME") + "/data/new_tests/TwoLambdaDiffSession.json";
    this.sendJob(Filename);
    
    CcdpUtils.pause(10);
    
    running_vms = engine.getAllCcdpVMResources();
    serverless_controllers = engine.getAllCcdpServerlessResources();
    assertEquals("There should be no agents running.", running_vms.size(), 0);
    assertEquals("There should only be no VMs", serverless_controllers.size(), numControllers); 
    logger.debug("Check the serverless controller has the task");
    
    boolean test1Found = false;
    boolean test2Found = false;
    for (CcdpServerlessResource cont : serverless_controllers)
    {
      if ( cont.getNodeType().equals("AWS Lambda") )
      {
        assertEquals("AWS Lambda controller should have no tasks", cont.getTasks().size(), 2);
        for (CcdpTaskRequest task : cont.getTasks())
        {
          if ( task.getSessionId().equals("test-1") )
            test1Found = true;
          else if ( task.getSessionId().equals("test-2") )
            test2Found = true;
          else
            fail("A session that isn't test-1 or test-2 was found");
        }
      }
      else
        assertEquals("Non-AWS Lambda controllers should have no tasks", cont.getTasks().size(), 0);
    }
    
    assertTrue("Test-1 was not found.", test1Found);
    assertTrue("Test-2 was not found.", test2Found);

    CcdpUtils.pause(30);
    
    logger.debug("Checking to see that the controller and VM finished the task");
    running_vms = engine.getAllCcdpVMResources();
    serverless_controllers = engine.getAllCcdpServerlessResources();
    
    assertEquals("There should be no agents running.", running_vms.size(), 0);
    assertEquals("There should only be no VMs", serverless_controllers.size(), numControllers); 
    logger.debug("Check the serverless controller has the task");
    for (CcdpServerlessResource cont : serverless_controllers)
    {
      assertEquals("All controllers should have no tasks", cont.getTasks().size(), 0);
    }
    
  }
  
  /*
   * A sanity check for Docker, providing multi-session, single and multi-tasked VMs
   * with free agents
   */ 
  @Test
  @Order(23)
  public void DockerSanityCheck()
  {
    logger.info("Starting EC2SanityCheck Test!");
        
    // Set no free agents
    ObjectNode res_cfg = CcdpUtils.getResourceCfg("DOCKER").deepCopy();
    res_cfg.put("min-number-free-agents", 1);
    CcdpUtils.setResourceCfg("DOCKER", res_cfg); 
    res_cfg = CcdpUtils.getResourceCfg("EC2").deepCopy();
    res_cfg.put("min-number-free-agents", 0);
    CcdpUtils.setResourceCfg("EC2", res_cfg);
    res_cfg = CcdpUtils.getResourceCfg("DEFAULT").deepCopy();
    res_cfg.put("min-number-free-agents", 0);
    CcdpUtils.setResourceCfg("DEFAULT", res_cfg);
    
    // Start the engine and let it configure
    logger.debug("Starting engine");
    engine = new CcdpMainApplication(null);
    CcdpUtils.pause(40);
    running_vms = engine.getAllCcdpVMResources();
    assertTrue("There should be 1 VM running right now", running_vms.size() == 1);
    
    String task_filename = System.getenv("CCDP_HOME") + "/data/new_tests/sanityCheck-UnitTest-Docker.json";
    this.sendJob(task_filename);
    CcdpUtils.pause(45);
    
    logger.debug("Checking VMs");
    running_vms = engine.getAllCcdpVMResources();
    assertTrue("There should be 5 VMs running", running_vms.size() == 5);
    
    boolean st1, st2, FA, reg1, reg2;
    st1 = st2 = FA = reg1 = reg2 = false;
    
    for (CcdpVMResource res : running_vms)
    {
      String sid = res.getAssignedSession();
      if ( res.getNodeType().equals("EC2") )
      {
        fail("There should be no EC2 VMs");
      }
      else if ( res.getNodeType().equals("DOCKER") )
      {
        if ( res.isSingleTasked() )
        {
          assertEquals("VM should only have 1 task", 1, res.getNumberTasks());
          if ( sid.equals("test-1") ) 
            st1 = true;
          else if ( sid.equals("test-2") )
            st2 = true;
          else
            fail("Single Tasked session unknown");            
        }
        else if ( sid.equals(CcdpUtils.FREE_AGENT_SID) )
          FA = true;
        
        else if ( sid.equals("test-1") )
        {
          assertEquals("The VM should have 2 tasks", 2, res.getNumberTasks());
          reg1 = true;
        }
        else if ( sid.equals("test-2") )
        {
          assertEquals("The VM should have 2 tasks", 2, res.getNumberTasks());
          reg2 = true;
        }
        else
          fail("What went wrong??");        
      }
      else
        fail("Node type unrecognized.");
    }
    
    assertTrue("A VM type was missing", st1 && st2 && FA && reg1 && reg2);
    
    CcdpUtils.pause(110);
    running_vms = engine.getAllCcdpVMResources();
    assertTrue("There should only be 1 Free Agent", running_vms.size() == 1);
  }
  
  /*
   * A sanity check for EC2, providing multi-session, single and multi-tasked VMs
   * with free agents
   */ 
  @Test
  @Order(24)
  public void EC2SanityCheck()
  {
    logger.info("Starting EC2SanityCheck Test!");
        
    // Set no free agents
    ObjectNode res_cfg = CcdpUtils.getResourceCfg("DOCKER").deepCopy();
    res_cfg.put("min-number-free-agents", 0);
    CcdpUtils.setResourceCfg("DOCKER", res_cfg); 
    res_cfg = CcdpUtils.getResourceCfg("EC2").deepCopy();
    res_cfg.put("min-number-free-agents", 1);
    CcdpUtils.setResourceCfg("EC2", res_cfg);
    res_cfg = CcdpUtils.getResourceCfg("DEFAULT").deepCopy();
    res_cfg.put("min-number-free-agents", 0);
    CcdpUtils.setResourceCfg("DEFAULT", res_cfg);
    
    // Start the engine and let it configure
    logger.debug("Starting engine");
    engine = new CcdpMainApplication(null);
    CcdpUtils.pause(40);
    running_vms = engine.getAllCcdpVMResources();
    assertTrue("There should be 1 VM running right now", running_vms.size() == 1);
    
    String task_filename = System.getenv("CCDP_HOME") + "/data/new_tests/sanityCheck-UnitTest-EC2.json";
    this.sendJob(task_filename);
    CcdpUtils.pause(45);
    
    logger.debug("Checking VMs");
    running_vms = engine.getAllCcdpVMResources();
    assertTrue("There should be 5 VMs running", running_vms.size() == 5);
    
    boolean st1, st2, FA, reg1, reg2;
    st1 = st2 = FA = reg1 = reg2 = false;
    
    for (CcdpVMResource res : running_vms)
    {
      String sid = res.getAssignedSession();
      if ( res.getNodeType().equals("DOCKER") )
      {
        fail("There should be no Docker VMs");
      }
      else if ( res.getNodeType().equals("EC2") )
      {
        if ( res.isSingleTasked() )
        {
          assertEquals("VM should only have 1 task", 1, res.getNumberTasks());
          if ( sid.equals("test-1") ) 
            st1 = true;
          else if ( sid.equals("test-2") )
            st2 = true;
          else
            fail("Single Tasked session unknown");            
        }
        else if ( sid.equals(CcdpUtils.FREE_AGENT_SID) )
          FA = true;
        
        else if ( sid.equals("test-1") )
        {
          assertEquals("The VM should have 2 tasks", 2, res.getNumberTasks());
          reg1 = true;
        }
        else if ( sid.equals("test-2") )
        {
          assertEquals("The VM should have 2 tasks", 2, res.getNumberTasks());
          reg2 = true;
        }
        else
          fail("What went wrong??");        
      }
      else
        fail("Node type unrecognized.");
    }
    
    assertTrue("A VM type was missing", st1 && st2 && FA && reg1 && reg2);
    
    CcdpUtils.pause(110);
    running_vms = engine.getAllCcdpVMResources();
    assertTrue("There should only be 1 Free Agent", running_vms.size() == 1);
  }
  
  /*
   * Serverless Sanity check for allocating multiple serverless tasks in multiple sessions
   */
  @Test
  @Order(25)
  public void ServerlessSanityCheck()
  {
    logger.debug("Starting ServerlessSanityCheck test!");
    
    int numControllers = CcdpUtils.getServerlessTypes().size();
    ObjectNode res_cfg = CcdpUtils.getResourceCfg("DOCKER").deepCopy();
    res_cfg.put("min-number-free-agents", 1);
    CcdpUtils.setResourceCfg("DOCKER", res_cfg); 
    res_cfg = CcdpUtils.getResourceCfg("EC2").deepCopy();
    res_cfg.put("min-number-free-agents", 1);
    CcdpUtils.setResourceCfg("EC2", res_cfg);
    res_cfg = CcdpUtils.getResourceCfg("DEFAULT").deepCopy();
    res_cfg.put("min-number-free-agents", 0);
    CcdpUtils.setResourceCfg("DEFAULT", res_cfg);
      
    // Start engine and give free agent time to spawn
    logger.debug("Starting engine");
    engine = new CcdpMainApplication(null);
    CcdpUtils.pause(50);
    
    logger.debug("Check that there are still no VMs");
    running_vms = engine.getAllCcdpVMResources();
    assertTrue("There should only be 2 FA VMs", running_vms.size() == 2);
    
    logger.debug("Check that there are the corect number of controllers");
    serverless_controllers = engine.getAllCcdpServerlessResources();
    assertEquals("There should be " + numControllers +" controllers", numControllers, serverless_controllers.size()); 
    
    logger.debug("Sending Serverless");
    String Filename = System.getenv("CCDP_HOME") + "/data/new_tests/sanityCheck-UnitTest-Serverless.json";
    this.sendJob(Filename);
    
    CcdpUtils.pause(13);
    
    logger.debug("Getting all resources");
    running_vms = engine.getAllCcdpVMResources();
    serverless_controllers = engine.getAllCcdpServerlessResources();
    
    int serverless1Count, serverless2Count;
    serverless1Count = serverless2Count = 0;
    String test1SID = "test-1";
    String test2SID = "test-2";
    
    assertEquals("There should be 2 agents running.", running_vms.size(), 2);
    assertEquals("There should only be " + numControllers + " controllers", serverless_controllers.size(), numControllers); 
    logger.debug("Check the serverless controller has the task");

    for (CcdpServerlessResource cont : serverless_controllers)
    {
      if ( cont.getNodeType().equals("AWS Lambda") )
      {
        assertTrue("There should be 4 task for AWS Lambda.", cont.getTasks().size() == 4);
        for ( CcdpTaskRequest task : cont.getTasks() )
        {
          if ( task.getSessionId().equals(test1SID) )
            serverless1Count++;
          else if ( task.getSessionId().equals(test2SID) )
            serverless2Count++;
          else
            fail("Serverless task session not recognized");
        }
      }
      else
        assertEquals("Non-AWS Lambda controllers should have no tasks", cont.getTasks().size(), 0);
    }
    
    assertTrue("Not all serverless accounted for", serverless1Count == 2 && serverless2Count == 2);
        
    CcdpUtils.pause(20);

    logger.debug("Checking to see that the controller finished the tasks");
    running_vms = engine.getAllCcdpVMResources();
    serverless_controllers = engine.getAllCcdpServerlessResources();
    
    assertEquals("There should be 2 free agents running.", running_vms.size(), 2);
    assertEquals("There should only be " + numControllers  +" controllers", serverless_controllers.size(), numControllers); 
    logger.debug("Check the serverless controller has the task");
    for ( CcdpServerlessResource cont : serverless_controllers )
    {
      assertEquals("All controllers should have no tasks", cont.getTasks().size(), 0);
    }    
  }
  
  /*
   * Sanity check for all resource types, single tasked, multiple sessions
   */
  @Test
  @Order(26)
  public void SanityCheck()
  {
    logger.debug("Starting SanityCheck test!");
    
    int numControllers = CcdpUtils.getServerlessTypes().size();
    ObjectNode res_cfg = CcdpUtils.getResourceCfg("DOCKER").deepCopy();
    res_cfg.put("min-number-free-agents", 1);
    CcdpUtils.setResourceCfg("DOCKER", res_cfg); 
    res_cfg = CcdpUtils.getResourceCfg("EC2").deepCopy();
    res_cfg.put("min-number-free-agents", 1);
    CcdpUtils.setResourceCfg("EC2", res_cfg);
    res_cfg = CcdpUtils.getResourceCfg("DEFAULT").deepCopy();
    res_cfg.put("min-number-free-agents", 0);
    CcdpUtils.setResourceCfg("DEFAULT", res_cfg);
      
    // Start engine and give free agent time to spawn
    logger.debug("Starting engine");
    engine = new CcdpMainApplication(null);
    CcdpUtils.pause(50);
    
    logger.debug("Check that there are still no VMs");
    running_vms = engine.getAllCcdpVMResources();
    assertTrue("There should only be 2 FA VMs", running_vms.size() == 2);
    
    logger.debug("Check that there are the corect number of controllers");
    serverless_controllers = engine.getAllCcdpServerlessResources();
    assertEquals("There should be " + numControllers +" controllers", numControllers, serverless_controllers.size()); 
    
    logger.debug("Sending EC2");
    String Filename = System.getenv("CCDP_HOME") + "/data/new_tests/sanityCheck-UnitTest-EC2.json";
    this.sendJob(Filename);
    CcdpUtils.pause(15);
    
    logger.debug("Sending Docker");
    Filename = System.getenv("CCDP_HOME") + "/data/new_tests/sanityCheck-UnitTest-Docker.json";
    this.sendJob(Filename);
    CcdpUtils.pause(10);
    
    logger.debug("Sending Serverless");
    Filename = System.getenv("CCDP_HOME") + "/data/new_tests/sanityCheck-UnitTest-Serverless.json";
    this.sendJob(Filename);
    
    CcdpUtils.pause(15);
    
    logger.debug("Getting all resources");
    running_vms = engine.getAllCcdpVMResources();
    serverless_controllers = engine.getAllCcdpServerlessResources();
    
    boolean stDocker1, stDocker2, stEC21, stEC22, docker1, docker2, ec21, ec22, dockerFA, ec2FA;
    int docker1Count, docker2Count, ec21Count, ec22Count, serverless1Count, 
    serverless2Count, dockerFACount, ec2FACount;
    stDocker1 = stDocker2 = stEC21 = stEC22 = docker1 = docker2 = ec21 = ec22 = dockerFA = ec2FA = false;
    docker1Count = docker2Count = ec21Count = ec22Count = serverless1Count = serverless2Count = dockerFACount = ec2FACount = 0;
    String test1SID = "test-1";
    String test2SID = "test-2";
    
    assertEquals("There should be 10 agents running.", running_vms.size(), 10);
    assertEquals("There should only be " + numControllers + " controllers", serverless_controllers.size(), numControllers); 
    logger.debug("Check the serverless controller has the task");

    for (CcdpServerlessResource cont : serverless_controllers)
    {
      if ( cont.getNodeType().equals("AWS Lambda") )
      {
        assertTrue("There should be 4 task for AWS Lambda.", cont.getTasks().size() == 4);
        for ( CcdpTaskRequest task : cont.getTasks() )
        {
          if ( task.getSessionId().equals(test1SID) )
            serverless1Count++;
          else if ( task.getSessionId().equals(test2SID) )
            serverless2Count++;
          else
            fail("Serverless task session not recognized");
        }
      }
      else
        assertEquals("Non-AWS Lambda controllers should have no tasks", cont.getTasks().size(), 0);
    }
    
    for (CcdpVMResource agent : running_vms )
    {
      String sid = agent.getAssignedSession();
      String type = agent.getNodeType();
      
      if ( agent.isSingleTasked() )
      {
        if ( agent.getTasks().size() > 1 )
          fail("A single tasked VM has more than 1 task, failing");
        if ( sid.equals(test1SID))
        {
          if ( type.equals(CcdpConfigParser.DOCKER_IMG_NAME) )
          {
            stDocker1 = true;
            docker1Count++;
          }
          else if ( type.equals(CcdpConfigParser.EC2_IMG_NAME) )
          {
            stEC21 = true;
            ec21Count++;
          }
          else
            fail("Single Task VM type not recognized");
        }
        else if ( sid.equals(test2SID))
        {
          if ( type.equals(CcdpConfigParser.DOCKER_IMG_NAME) )
          {
            stDocker2 = true;
            docker2Count++;
          }
          else if ( type.equals(CcdpConfigParser.EC2_IMG_NAME) )
          {
            stEC22 = true;
            ec22Count++;
          }
          else
            fail("Single Task VM type not recognized");
        }
        else
          fail("Single task VM session not recognized");    
      }
      else if ( sid.equals(CcdpUtils.FREE_AGENT_SID))
      {
        if ( type.equals(CcdpConfigParser.DOCKER_IMG_NAME) )
          dockerFACount++;
        else if ( type.equals(CcdpConfigParser.EC2_IMG_NAME) )
          ec2FACount++;
        else
          fail("FA Node Type no recognized");
      }
      else if ( sid.equals(test1SID) )
      {
        if ( agent.getTasks().size() != 2 )
          fail("Incorrect number of tasks on normal VM");
        if (type.equals(CcdpConfigParser.DOCKER_IMG_NAME) )
        {
          docker1Count++;
        }
        else if ( type.equals(CcdpConfigParser.EC2_IMG_NAME) )
        {
          ec21Count++;
        }
        else
          fail("Session test-1 node type not found");
      }
      else if ( sid.equals(test2SID) )
      {
        if ( agent.getTasks().size() != 2 )
          fail("Incorrect number of tasks on normal VM");
        if (type.equals(CcdpConfigParser.DOCKER_IMG_NAME) )
        {
          docker2Count++;
        }
        else if ( type.equals(CcdpConfigParser.EC2_IMG_NAME) )
        {
          ec22Count++;
        }
        else
          fail("Session test-1 node type not found");
      }
      else
        fail("Somehow you missed every condition possible, good job.");
    }
    
    // Now do the math, there should be 4 serverless tasks, and 10 VMs
    if (docker1Count == 2)
      docker1 = true;
    if (docker2Count == 2)
      docker2 = true;
    if (ec21Count == 2)
      ec21 = true;
    if (ec22Count == 2)
      ec22 = true;
    if (dockerFACount == 1)
      dockerFA = true;
    if ( ec2FACount == 1 )
      ec2FA = true;
    assertTrue("Not all agents accounted for", docker1 && docker2 && ec21 && ec22 && dockerFA && ec2FA && stDocker1
        && stDocker2 && stEC21 && stEC22);

    assertTrue("Not all serverless accounted for", serverless1Count == 2 && serverless2Count == 2);
        
    CcdpUtils.pause(140);

    logger.debug("Checking to see that the controller and VM finished the task");
    running_vms = engine.getAllCcdpVMResources();
    serverless_controllers = engine.getAllCcdpServerlessResources();
    boolean finalDockerVM, finalEC2VM;
    finalDockerVM = finalEC2VM = false;
    
    assertEquals("There should be 2 free agents running.", 2, running_vms.size());
    assertEquals("There should only be " + numControllers  +" controllers", serverless_controllers.size(), numControllers); 
    logger.debug("Check the serverless controller has the task");
    for ( CcdpServerlessResource cont : serverless_controllers )
    {
      assertEquals("All controllers should have no tasks", cont.getTasks().size(), 0);
    }
    for ( CcdpVMResource agent : running_vms )
    {
      String type = agent.getNodeType();
      if ( !agent.getAssignedSession().equals(CcdpUtils.FREE_AGENT_SID) )
        fail("Remaining agents are not free agents.");
      if ( type.equals(CcdpConfigParser.DOCKER_IMG_NAME) )
        finalDockerVM = true;
      else if ( type.equals(CcdpConfigParser.EC2_IMG_NAME) )
        finalEC2VM = true;
      else
        fail("Final VM type not recognized.");
    }
    assertTrue("Final VMs not correct", finalDockerVM && finalEC2VM );
    
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
  
  private void sendJob ( String Filename )
  {
    try
    {
      logger.debug("Sending task");
      byte[] data = Files.readAllBytes( Paths.get( Filename ) );
      String job = new String(data, "utf-8");
      new CcdpMsgSender(null, job, null, null);
    }
    catch ( Exception e )
    {
      logger.error("Error loading file, exception thrown");
      e.printStackTrace();
      fail("The task was not able to be sent");
    }
  }
}
