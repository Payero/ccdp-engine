package com.axios.ccdp.test.unittest.docker;

import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import com.axios.ccdp.impl.cloud.docker.DockerResourceMonitorImpl;
import com.axios.ccdp.impl.cloud.docker.DockerVMControllerImpl;
import com.axios.ccdp.factory.CcdpObjectFactory;
import com.axios.ccdp.intfs.CcdpConnectionIntf;
import com.axios.ccdp.intfs.CcdpDatabaseIntf;
import com.axios.ccdp.intfs.CcdpMessageConsumerIntf;
import com.axios.ccdp.messages.AssignSessionMessage;
import com.axios.ccdp.messages.CcdpMessage;
import com.axios.ccdp.messages.ResourceUpdateMessage;
import com.axios.ccdp.messages.RunTaskMessage;
import com.axios.ccdp.messages.TaskUpdateMessage;
import com.axios.ccdp.messages.CcdpMessage.CcdpMessageType;
import com.axios.ccdp.messages.ErrorMessage;
import com.axios.ccdp.resources.CcdpImageInfo;
import com.axios.ccdp.resources.CcdpVMResource;
import com.axios.ccdp.resources.CcdpVMResource.ResourceStatus;
import com.axios.ccdp.tasking.CcdpTaskRequest;
import com.axios.ccdp.test.unittest.TestHelperUnitTest;
import com.axios.ccdp.utils.CcdpUtils;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.spotify.docker.client.DefaultDockerClient;
import com.spotify.docker.client.DockerClient;
import com.spotify.docker.client.DockerClient.ListContainersParam;
import com.spotify.docker.client.messages.Container;

public class DockerControllerUnitTest implements CcdpMessageConsumerIntf
{

  /**
   * Generates debug print statements based on the verbosity level.
   */
  private static Logger logger = 
      Logger.getLogger(DockerControllerUnitTest.class.getName());
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
  public DockerControllerUnitTest()
  {
    logger.debug("Initializing Docker Controller Unit Test");
  }
  
  /**
   * Runs before any of the methods so is used to initialize all the objects
   * such as connections and configuration properties
   */
  @BeforeClass
  public static void initialize()
  {
    TestHelperUnitTest.initialize();
    Logger.getRootLogger().setLevel(Level.WARN);
    String url = CcdpUtils.getConfigValue("res.mon.intf.docker.url");
    if( url == null )
    {
      logger.warn("Docker URL was not defined, using default");
      url = DockerResourceMonitorImpl.DEFAULT_DOCKER_HOST;
    }
    assertNotNull(url);
    dockerClient = new DefaultDockerClient(url);
    logger.debug("Done initialize()");
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
    
    JsonNode db_node = CcdpUtils.getDatabaseIntfCfg();
    JsonNode task_msg_node = CcdpUtils.getConnnectionIntfCfg();
    CcdpObjectFactory factory = CcdpObjectFactory.newInstance();
    this.connection = factory.getCcdpConnectionInterface(task_msg_node);
    this.connection.configure(task_msg_node);
    this.connection.setConsumer(this);
    logger.debug("Done with the connections: " + task_msg_node.toString());
    
    this.dbClient = factory.getCcdpDatabaseIntf(db_node);
    this.dbClient.connect();
    logger.debug("Done with DB connection: " + db_node.toString());
    
    
    assertNotNull("Could not setup a connection with broker", this.connection);
    assertNotNull("Could not setup a database connection", this.dbClient);
    String uuid = UUID.randomUUID().toString();
    String channel = 
        task_msg_node.get( CcdpUtils.CFG_KEY_MAIN_CHANNEL).asText();
    assertNotNull("The Main Channel cannot be null", channel);
    this.connection.registerConsumer(uuid, channel);
    
    this.docker = new DockerVMControllerImpl();
    this.jsonCfg = this.mapper.createObjectNode();
    String cfg_file = System.getProperty("ccdp.config.file");
    if(cfg_file == null)
    {
      logger.debug("The ccdp.config.file is null using default config file");
      String path = System.getenv("CCDP_HOME");
      if( path == null )
        path = System.getProperty("CCDP_HOME");
      
      cfg_file =  path + "/config/ccdp-config.json";
    }
    logger.debug("The config file " + cfg_file);
    
    if( cfg_file != null )
    {
      try
      {
        CcdpUtils.loadProperties(cfg_file);
        this.jsonCfg = CcdpUtils.getResourceCfg("DOCKER");
        this.engCfg = CcdpUtils.getEngineCfg();
        this.docker.configure(this.jsonCfg);
      }
      catch( Exception e )
      {
        e.printStackTrace();
      }
    }
    
  }
  
  /**
   * Simple test to make sure the startup and tear down works properly
   */
  //@Test
  public void testSetupRoutine()
  {
    logger.debug("Testing Setup Routine");
  }
  
  
  /**
   * Tests the ability to start an instance and make sure is only one.  The 
   * instance is then terminated at teardown
   */
  @Test
  public void startSingleInstanceTest()
  {
    Logger.getRootLogger().setLevel(Level.WARN);
    Logger.getRootLogger().setAdditivity(false);
    
    CcdpImageInfo imgInf = CcdpUtils.getImageInfo("DOCKER");
    assertNotNull("Could not find Image information", imgInf);
    CcdpImageInfo image = CcdpImageInfo.copyImageInfo(imgInf);
    assertNotNull("Could not find Image information", image);
    image.setMinReq(1);
    assertTrue("The minimum should be ", image.getMinReq() == 1);
    logger.debug("Before startInstances()");
    
    this.running_vms = this.docker.startInstances(image);
    assertTrue("Wrong number of instances", this.running_vms.size() == 1);
  }
  
  
  /**
   * Tests the ability to start multiple instances using a single request
   */
  @Test
  public void startMultipleInstancesTest()
  {
    CcdpImageInfo imgInf = CcdpUtils.getImageInfo("DOCKER");
    assertNotNull("Could not find Image information", imgInf);
    CcdpImageInfo image = CcdpImageInfo.copyImageInfo(imgInf);
    assertNotNull("Could not find Image information", image);
    image.setMinReq(3);
    image.setSessionId("docker-session");
    assertTrue("The minimum should be ", image.getMinReq() == 3);
    
    this.running_vms = this.docker.startInstances(image);
    System.out.println("NUM VMS " + this.running_vms.size());
    assertTrue("Wrong number of instances", this.running_vms.size() == 3);
  }
  
  /**
   * Tests the ability to start an instance with a specific Session ID
   * This test failed when ran with all other tests, possible lack of PC power
   * To fix this, probably just increase initial wait, or run by itself
   */
  @Test
  public void startInstanceWithSessionIdTest()
  {
    CcdpImageInfo imgInf = CcdpUtils.getImageInfo("DOCKER");
    assertNotNull("Could not find Image information", imgInf);
    CcdpImageInfo image = CcdpImageInfo.copyImageInfo(imgInf);
    assertNotNull("Could not find Image information", image);
    image.setMinReq(1);
    String sessionIdToSet = "docker-session";
    image.setSessionId(sessionIdToSet);
    assertTrue("The minimum should be ", image.getMinReq() == 1);
    
    this.running_vms = this.docker.startInstances(image);
    assertNotNull("Could not instantiate VMs", this.running_vms);
    assertTrue("Wrong number of instances", this.running_vms.size() == 1);
    String channel = this.running_vms.get(0);
    
    AssignSessionMessage asgn_msg = new AssignSessionMessage();
    asgn_msg.setSessionId(sessionIdToSet);
    this.connection.sendCcdpMessage(channel, asgn_msg);
    
    logger.debug("Waiting 45 seconds for " + channel);    
    // let's wait a couple of seconds to give the agent time to set the session
    // id and send an updated heartbeat message
    boolean lazyCheck = false;
    // Lazy check for sent heartbeats to Mongo
    if ( lazyCheck )
    {
      logger.debug("Doing lazy check for heartbeats");
      CcdpUtils.pause(45);
      
      // Lazy compare for if a heartbeat was sent
      assertTrue( "Last Updated is equal to last assignment, so no heartbeat!",
         this.dbClient.getVMInformation(channel).getLastAssignmentTime() != this.dbClient.getVMInformation(channel).getLastUpdatedTime() );
    }
    // Less lazy check for heartbeats
    else 
    {
      logger.debug("Doing less lazy check for heartbeats");
      CcdpUtils.pause(25);
      
      // Config stuff, set last baseline;
      int numHeartbeats = 0;
      long HBfreq = engCfg.get(CcdpUtils.CFG_KEY_HB_FREQ).asLong();
      long lastUpdate = this.dbClient.getVMInformation(channel).getLastAssignmentTime();
      long current;
      // Check for a change in last updated, signaling a HB, using HB frequency
      for (int i = 0; i < 4; i++)
      {
        current = this.dbClient.getVMInformation(channel).getLastUpdatedTime();
        if ( current != lastUpdate)
          numHeartbeats ++;
        lastUpdate = current;
        CcdpUtils.pause(HBfreq);
      }
      logger.debug("There were " + numHeartbeats + " heartbeats registered");
      assertTrue("There were no heartbeats", numHeartbeats > 0);
    }
    // Also do SID test
    assertEquals( sessionIdToSet, this.dbClient.getVMInformation(channel).getAssignedSession() );

    //THIS DOESN'T WORK NOW THAT MONGO IS USED FOR HEARTBEATS!
    /*
    boolean found_it = false;
    logger.debug("Num Heartbeats: " + this.heartbeats.size());
    
    // iterating through all the messages
    for( CcdpMessage msg : this.heartbeats )
    {
      ResourceUpdateMessage upd = (ResourceUpdateMessage)msg;
      CcdpVMResource res = upd.getCcdpVMResource();
      // find the resource we just created
      if( res.getInstanceId().equals(channel) )
      {
        logger.debug("Found the resource: " + channel);
        String sid = res.getAssignedSession();
        if( "docker-session".equals(sid) )
        {
          found_it = true;
          break;
        }
      }        
    }
    assertTrue("Could not find a matching Session ID", found_it);
    */
  }

  /**
   * Tests the ability to start an instance without a specific Session ID
   */
  @Test
  public void startInstanceWithoutSessionIdTest()
  {
    CcdpImageInfo imgInf = CcdpUtils.getImageInfo("DOCKER");
    assertNotNull("Could not find Image information", imgInf);
    CcdpImageInfo image = CcdpImageInfo.copyImageInfo(imgInf);
    assertNotNull("Could not find Image information", image);
    image.setMinReq(1);
    assertTrue("The minimum should be ", image.getMinReq() == 1);
    
    this.running_vms = this.docker.startInstances(image);
    assertTrue("Wrong number of instances", this.running_vms.size() == 1);
    String channel = this.running_vms.get(0);
    logger.debug("Waiting 45 seconds for " + channel);
    //boolean found_it = false;
    boolean lazyCheck = false;
    
    // Lazy compare for if a heartbeat was sent
    if ( lazyCheck )
    {
      logger.debug("Doing lazy heartbeat check");
      CcdpUtils.pause(45);
       
      assertTrue("Last Updated is equal to last assignment, so no heartbeat!",
         this.dbClient.getVMInformation(channel).getLastAssignmentTime() != this.dbClient.getVMInformation(channel).getLastUpdatedTime());
    }
    // Less lazy compare for if a heartbeat was sent
    else
    {
      logger.debug("Doing less lazy heartbeat check");
      CcdpUtils.pause(25);

      // Config stuff, set time baseline for last
      long HBfreq = engCfg.get(CcdpUtils.CFG_KEY_HB_FREQ).asLong();      
      int numHeartbeats = 0;
      long lastUpdate = this.dbClient.getVMInformation(channel).getLastAssignmentTime();
      long current;
      // Check for a change in last updated, singaling a HB, using HB frequency.
      for (int i = 0; i < 4; i++)
      {
        current = this.dbClient.getVMInformation(channel).getLastUpdatedTime();
        if ( current != lastUpdate)
          numHeartbeats ++;
        lastUpdate = current;
        CcdpUtils.pause(HBfreq);
      }
      logger.debug("There were " + numHeartbeats + " heartbeats registered");
      assertTrue("There were no heartbeats", numHeartbeats > 0);
    }
    
    // THIS METHOD DOESN'T WORK NOW THAT HEARTBEATS ARE MANAGED BY MONGO!
    /*
    logger.debug("There are " + this.heartbeats.size() + " heartbeats in the list");
    // iterating through all the messages
    for( CcdpMessage msg : this.heartbeats )
    {
      ResourceUpdateMessage upd = (ResourceUpdateMessage)msg;
      CcdpVMResource res = upd.getCcdpVMResource();
      // find the resource we just created
      if( res.getInstanceId().equals(channel) )
      {
        
        String sid = res.getAssignedSession();
        logger.debug("Found the resource: " + channel + " SID " + sid);
        if( "DOCKER".equals(sid) )
        {
          found_it = true;
          break;
        }
      }        
    }
    assertTrue("There are no heartbeats in the list", this.heartbeats.size() > 0);
    assertTrue("The Session ID is different", found_it);
  */
  }
    
  /**
   * Tests the ability to start and stop an instance
   */
  @Test
  public void startAndStopInstanceTest()
  {
    CcdpImageInfo imgInf = CcdpUtils.getImageInfo("DOCKER");
    assertNotNull("Could not find Image information", imgInf);
    CcdpImageInfo image = CcdpImageInfo.copyImageInfo(imgInf);
    assertNotNull("Could not find Image information", image);
    image.setMinReq(1);
    assertTrue("The minimum should be ", image.getMinReq() == 1);
    String img_id = image.getImageId();
    assertNotNull("The Image Id cannot me null", img_id);
    
    this.running_vms = this.docker.startInstances(image);
    logger.debug("Running " + this.running_vms.size() + " Containers");
    assertTrue("Wrong number of instances", this.running_vms.size() == 1);
    
    List<CcdpVMResource> vms = this.docker.getAllInstanceStatus();
    
    logger.debug("Running VMs " + this.running_vms.size() + " and instances " + vms.size());
    assertTrue("getAllInstanceStatus() does not match launched VMs", 
                vms.size() == this.running_vms.size() );
    
    this.docker.stopInstances(this.running_vms);
    vms = this.docker.getAllInstanceStatus();
    
    for( CcdpVMResource res : vms )
    {
      String iid = res.getInstanceId();
      ResourceStatus status = this.docker.getInstanceState(iid);
      assertNotNull("Could not find Resource " + iid, status);
      logger.debug("VM Status " + status);
      assertTrue("The VM is not stopped", status.equals(ResourceStatus.STOPPED));
    }
  }
  
  
  /**
   * Tests the ability to start multiple instances and stopping just one
   */
  @Test
  public void startManyAndStopSingleInstanceTest()
  {
    CcdpImageInfo imgInf = CcdpUtils.getImageInfo("DOCKER");
    assertNotNull("Could not find Image information", imgInf);
    CcdpImageInfo image = CcdpImageInfo.copyImageInfo(imgInf);
    assertNotNull("Could not find Image information", image);
    image.setMinReq(3);
    assertTrue("The minimum should be ", image.getMinReq() == 3);
    
    this.running_vms = this.docker.startInstances(image);
    assertTrue("Wrong number of instances", this.running_vms.size() == 3);
    List<CcdpVMResource> vms = this.docker.getAllInstanceStatus();
    logger.debug("the size of the list is " + vms.size());
    CcdpVMResource vm = vms.get(1);
    String testId = vm.getInstanceId();
    logger.debug("Going to stop VM " + testId);
    List<String> stopIds = new ArrayList<>();
    stopIds.add(vm.getInstanceId());
    logger.debug("Waiting to get updated heartbeats");
    CcdpUtils.pause(12);
    this.docker.stopInstances(stopIds);
    vms = this.docker.getAllInstanceStatus();
    
    for( CcdpVMResource res : vms )
    {
      String iid = res.getInstanceId();
      ResourceStatus status = this.docker.getInstanceState(iid);
      assertNotNull("Could not find Resource " + iid, status);
      logger.debug("VM Status " + status);
      if( testId.equals(iid) )
        assertTrue("The VM is not stopped", status.equals(ResourceStatus.STOPPED));
      else
        assertTrue("The VM is not running", status.equals(ResourceStatus.RUNNING));
      
    }
  }
  
  
  /**
   * Tests the ability to check tasks assigned to VM using MainApp tasking.
   * Make sure no other docker containers are running when this test is run.
   * Changed "MOCK PAUSE" job to cmd in {HOME}/data/rand_time_docker.json.
   * Originally, this test failed because it got VM info from dockerClient instead
   * of just using Mongo...................why?
   */
  @Test
  public void checksTasksRunningOnVMTest()
  {
    CcdpImageInfo imgInf = CcdpUtils.getImageInfo("DOCKER");
    assertNotNull("Could not find Image information", imgInf);
    CcdpImageInfo image = CcdpImageInfo.copyImageInfo(imgInf);
    assertNotNull("Could not find Image information", image);
    image.setMinReq(1);
    assertTrue("The minimum should be ", image.getMinReq() == 1);
    
    this.running_vms = this.docker.startInstances(image);
    assertTrue("Wrong number of instances", this.running_vms.size() == 1);
    List<CcdpVMResource> vms = this.docker.getAllInstanceStatus();
    
    logger.debug("Waiting 45 seconds for VMs to spool up");  
    CcdpUtils.pause(45);
    
    CcdpVMResource vm = vms.get(0);
    String testId = vm.getInstanceId();
    logger.debug("VM 0 has Assigned Session: " + vm.getAssignedSession());
    
    //assertTrue("The Session ID is different", "DOCKER".equals(vm.getAssignedSession()));
    
    // I don't think any of this works, changing it -SRB
    CcdpTaskRequest task1 = this.sendTaskRequest(testId);
    //CcdpTaskRequest task2 = this.sendTaskRequest(testId, "MOCK_PAUSE", 5);
    
    
    CcdpUtils.pause(15);
    //vms = this.docker.getAllInstanceStatus();
    vms = this.dbClient.getAllVMInformation();
    logger.debug(vms.toString());
    
    for( CcdpVMResource res : vms )
    {
      String iid = res.getInstanceId();
      ResourceStatus status = this.docker.getInstanceState(iid);
      assertNotNull("Could not find Resource " + iid, status);
      logger.debug("VM Status " + status + " with IID " + iid);
      assertTrue("The VM is not running", status.equals(ResourceStatus.RUNNING));
      if( testId.equals(iid) )
      {
        List<CcdpTaskRequest> tasks = res.getTasks();
        logger.debug(tasks.toString());
        assertTrue("Did not find task", tasks.size() == 1);
        for( CcdpTaskRequest task : tasks )
        {
          
          String tid = task.getTaskId();
          //if( !tid.equals(task1.getTaskId() ) && !tid.equals(task2.getTaskId()) )
          if( !tid.equals(task1.getTaskId() ))
            fail("The running Task does not match");
        }
      }
    }
  }
  
  
  /**
   * Tests the ability of getting the instance id of a non-existing instance, 
   * a request with null, and a valid instance id
   */
  @Test
  public void getInstanceStateTest()
  {
    ResourceStatus state = this.docker.getInstanceState(null);
    assertNull(state);
    
    state = this.docker.getInstanceState("my-bogus-id");
    assertNull(state);
    
    CcdpImageInfo imgInf = CcdpUtils.getImageInfo("DOCKER");
    assertNotNull("Could not find Image information", imgInf);
    CcdpImageInfo image = CcdpImageInfo.copyImageInfo(imgInf);
    assertNotNull("Could not find Image information", image);
    image.setMinReq(1);
    assertTrue("The minimum should be ", image.getMinReq() == 1);
    
    List<String> vms = this.docker.startInstances(image);
    assertTrue("Wrong number of instances", vms.size() == 1);
    
    state = this.docker.getInstanceState(vms.get(0));
    assertNotNull("The state came back null", state);
    
  }
  
  
  /**
   * Tests the ability to retrieve Status of the Remote resources based on the
   * tags associated with that server
   * Method not implemented, see DockerVMContollerImpl
   */
  //@Test
  public void getStatusFilteredByTagsTest()
  {
    List<String> iids = new ArrayList<>();
    Map<String, String> tags = new HashMap<>();
    tags.put("Name", "Server-One");
    tags.put("Group", "Test");
    
    logger.debug("Creating the first instance");
    CcdpImageInfo imgInf = CcdpUtils.getImageInfo("DOCKER");
    assertNotNull("Could not find Image information", imgInf);
    CcdpImageInfo image = CcdpImageInfo.copyImageInfo(imgInf);
    assertNotNull("Could not find Image information", image);
    image.setMinReq(1);
    assertTrue("The minimum should be ", image.getMinReq() == 1);

    image.setTags(tags);
    
    iids.add( this.docker.startInstances(image).get(0) );
    
    assertTrue("Should have only one instance", this.docker.getAllInstanceStatus().size() == 1);
    logger.debug("Creating the Second Instance");
    Map<String, String> tags2 = new HashMap<>();
    tags2.put("Name", "Server-Two");
    tags2.put("Group", "Test");
    
    assertNotNull("Could not find Image information", imgInf);
    image = CcdpImageInfo.copyImageInfo(imgInf);
    assertNotNull("Could not find Image information", image);
    image.setMinReq(1);
    assertTrue("The minimum should be ", image.getMinReq() == 1);

    image.setTags(tags2);
    
    iids.add( this.docker.startInstances(image).get(0) );
    assertTrue("Should have only two instance", this.docker.getAllInstanceStatus().size() == 2);
    
    logger.debug("Creating the Third Instance");
    Map<String, String> tags3 = new HashMap<>();
    tags3.put("Name", "Server-Three");
    tags3.put("Group", "Other-Test");
    
    assertNotNull("Could not find Image information", imgInf);
    image = CcdpImageInfo.copyImageInfo(imgInf);
    assertNotNull("Could not find Image information", image);
    image.setMinReq(1);
    assertTrue("The minimum should be ", image.getMinReq() == 1);

    image.setTags(tags3);
    
    iids.add( this.docker.startInstances(image).get(0) );
    assertTrue("Should have only three instance", this.docker.getAllInstanceStatus().size() == 3);
    
    List<CcdpVMResource> res = this.docker.getAllInstanceStatus();
    for(CcdpVMResource vm : res )
    {
      logger.debug(vm.toPrettyPrint() );
      logger.debug("----------------------------------------------------");
      
    }
    
    logger.debug("Now the actual tests");
    List<CcdpVMResource> vms = null;
    assertTrue("Have more than three instances", iids.size() == 3);
    ObjectMapper mapper = new ObjectMapper();
    ObjectNode node = mapper.createObjectNode();
    node.put("Name", "Server-Two");
    vms = this.docker.getStatusFilteredByTags(node);
    assertNotNull("The results should not be null", vms);
    assertTrue("The Second Server should have been found", vms.size() == 1);
    
    node = mapper.createObjectNode();
    node.put("Group", "Test");
    
    vms = this.docker.getStatusFilteredByTags(node);
    assertNotNull("The results should not be null", vms);
    assertTrue("Should have found two servers", vms.size() == 2);
    
    vms = this.docker.getStatusFilteredByTags(null);
    assertNotNull("The results should not be null", vms);
    assertTrue("Should be empty", vms.size() == 0);
    
    node = mapper.createObjectNode();
    vms = this.docker.getStatusFilteredByTags(null);
    assertNotNull("The results should not be null", vms);
    assertTrue("Should be empty", vms.size() == 0);
    
  }
  
  /**
   * Tests the ability to get a single instance based on the id.
   * Method not implemented, see DockerVMControllerImpl
   */
  //@Test
  public void getStatusByIdTest()
  {
    CcdpImageInfo imgInf = CcdpUtils.getImageInfo("DOCKER");
    assertNotNull("Could not find Image information", imgInf);
    CcdpImageInfo image = CcdpImageInfo.copyImageInfo(imgInf);
    assertNotNull("Could not find Image information", image);
    image.setMinReq(5);
    assertTrue("The minimum should be ", image.getMinReq() == 5);

    List<String> iids = this.docker.startInstances(image);
    assertEquals("Shoud have five instances", iids.size(), 5);
    
    String id = iids.get(1);
    CcdpVMResource vm = this.docker.getStatusFilteredById(id);
    assertNotNull("Should not be null", vm);
    assertEquals("Should be the same VM", id, vm.getInstanceId());
    
    vm = this.docker.getStatusFilteredById(null);
    assertNull("ID passed was null so it should be null", vm);
    
    vm = this.docker.getStatusFilteredById("bogus-id");
    assertNull("Invalid id so it should be null", vm);
  }
  
  
  /***************************************************************************/
  /**                            Helper Classes                             **/
  /***************************************************************************/
  
  /**
   * Sends a task message to the intended VM.  
   * 
   * @param iid the instance id for the VM
   * @param action what to do either paused, cpu, or
   * @param time for how long
   * 
   * @return the task that was sent
   */
  private CcdpTaskRequest sendTaskRequest(String iid)
  {
    assertNotNull("The Instance ID cannot be null", iid);
    
    CcdpTaskRequest task = new CcdpTaskRequest();
    
    task.setSessionId("test-session");
    task.setNodeType("DOCKER");
    List<String> cmd = new ArrayList<>();
    cmd.add("/data/ccdp/ccdp-engine/python/ccdp_mod_test.py");
    cmd.add("-a");
    cmd.add("testRandomTime");
    cmd.add("-p");
    cmd.add("min=10,max=15");
    task.setCommand(cmd);
    RunTaskMessage msg = new RunTaskMessage();
    logger.debug("Task: " + task.toString());
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
  
  /**
   * This method is invoked after every test and can be used to do some 
   * cleaning after running the tests
   */
  @After
  public void tearDownTest()
  {
    logger.debug("Cleaning up after the test");
    if( this.connection != null ) this.connection.disconnect();
    if( this.messages != null ) this.messages.clear();
    this.messages = null;
    // delete all the containers created 
    if( this.rem_containers && this.running_vms != null )
    {
      logger.debug("Terminating " + this.running_vms.size() + " VMs");
      this.docker.stopInstances(this.running_vms);
      this.running_vms = null;
    }
    
    try
    {
      ListContainersParam params = ListContainersParam.filter("status", "exited");
      List<Container> ids = dockerClient.listContainers(params);
      for( Container c : ids )
      {
        String id = c.id();
        try
        {
          logger.debug("Removing Container " + id);
          dockerClient.removeContainer(id);          
        }
        catch (Exception e)
        {
          logger.info("Could not remove " + id);
          continue;
        }

      }
    }
    catch( Exception e )
    {
      logger.info("Could not get a list of the containers");
    }
    
  }
  
  /**
   * Runs once at the end of the unit testing and can be used to close all the 
   * connections open during initialization
   */
  @AfterClass
  public static void terminate()
  {
    dockerClient.close();
  }
  
}
