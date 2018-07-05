package com.axios.ccdp.test.unittest.MainApplication;

import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.apache.log4j.Logger;
import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.Ignore;

import com.axios.ccdp.cloud.sim.SimVirtualMachine;
import com.axios.ccdp.connections.intfs.CcdpConnectionIntf;
import com.axios.ccdp.connections.intfs.CcdpMessageConsumerIntf;
import com.axios.ccdp.connections.intfs.CcdpVMControllerIntf;
import com.axios.ccdp.factory.CcdpObjectFactory;
import com.axios.ccdp.fmwk.CcdpMainApplication;
import com.axios.ccdp.messages.CcdpMessage;
import com.axios.ccdp.messages.ResourceUpdateMessage;
import com.axios.ccdp.messages.TaskUpdateMessage;
import com.axios.ccdp.messages.ThreadRequestMessage;
import com.axios.ccdp.messages.CcdpMessage.CcdpMessageType;
import com.axios.ccdp.messages.KillTaskMessage;
import com.axios.ccdp.messages.ShutdownMessage;
import com.axios.ccdp.resources.CcdpImageInfo;
import com.axios.ccdp.resources.CcdpVMResource;
import com.axios.ccdp.resources.CcdpVMResource.ResourceStatus;
import com.axios.ccdp.tasking.CcdpTaskRequest;
import com.axios.ccdp.tasking.CcdpTaskRequest.CcdpTaskState;
import com.axios.ccdp.tasking.CcdpThreadRequest;
import com.axios.ccdp.test.unittest.JUnitTestHelper;
import com.axios.ccdp.utils.CcdpUtils;
import com.axios.ccdp.utils.CcdpUtils.CcdpNodeType;
import com.fasterxml.jackson.databind.node.ObjectNode;

public class CcdpMainApplicationTests implements CcdpMessageConsumerIntf
{
  /**
   * Generates debug print statements based on the verbosity level.
   */
  private Logger logger = Logger
      .getLogger(CcdpMainApplicationTests.class.getName());

  /**
   * Stores the actual object to test
   */
  CcdpMainApplication ccdpEngine= null;
  /**
   * Controls some of the test vms. Some are controlled 
   * by another controller in the engine
   */
  private CcdpVMControllerIntf controller =null;

  /**
   * Object used to send and receive messages 
   */
  private CcdpConnectionIntf connection;

  /**
   * channel for the testing class to get replies
   */
  private String testChannel = "MainApp-Testing";

  /**
   * channel for the testing class to get replies
   */
  private String mainChannel = null;
  /**
   * Stores a map of task and their taskId
   */
  private Map<String, CcdpTaskRequest > taskMap = null;

  /**
   * Stores the name of the class used to interact with the cloud provider
   */
  protected static String CcdpVMcontroller =   "com.axios.ccdp.cloud.aws.AWSCcdpVMControllerImpl";

  /**
   * Stores the name  name of the class used handle different storage solution
   */
  protected static String CcdpVMStorageController = "com.axios.ccdp.cloud.sim.SimCcdpStorageControllerImpl";

  /**
   * The time to wait depending on which controller class is being run
   */
  protected static double  WAIT_TIME_LAUNCH_VM = 90.0;
  
  protected static double  WAIT_TIME_SEND_TASK = 10.0;
    
  @BeforeClass
  public static void initialization() {
    //load the config file before every test case
    JUnitTestHelper.initialize();
  }
  @Before
  public  void setUpTest()
  {
  //making sure the name of the controller and storage are set
    assertNotNull("The name of thecontroller class shoud not be null",
        CcdpMainApplicationTests.CcdpVMcontroller);
    assertNotNull("The name of the storage class shoud not be null",
        CcdpMainApplicationTests.CcdpVMStorageController);

    // creating the factory that generates the objects used by the test class
    CcdpObjectFactory factory = CcdpObjectFactory.newInstance();
    ObjectNode task_msg_node = 
        CcdpUtils.getJsonKeysByFilter(CcdpUtils.CFG_KEY_CONN_INTF);

    this.connection = factory.getCcdpConnectionInterface(task_msg_node);
    this.connection.configure(task_msg_node);
    this.connection.setConsumer(this);
    this.logger.debug("Done with the connections: " + task_msg_node.toString());

    assertNotNull("Could not setup a connection with broker", this.connection);
    String uuid = UUID.randomUUID().toString();
    this.connection.registerConsumer(uuid, testChannel);
    this.taskMap = new  HashMap<>();
    assertTrue("The taskMap should be empty", this.taskMap.isEmpty());

    mainChannel= CcdpUtils.getProperty(CcdpUtils.CFG_KEY_MAIN_CHANNEL);
    assertNotNull("The channel for the engine cannot be null", this.mainChannel);

    //setting the controller and storage properties to the appropriate name
    CcdpUtils.setProperty("resource.intf.classname", CcdpMainApplicationTests.CcdpVMcontroller);
    CcdpUtils.setProperty("storage.intf.classname", CcdpMainApplicationTests.CcdpVMStorageController);
    
    //setting the free vm to 0 
    CcdpUtils.setProperty("resourceIntf.default.min.number.free.agents", "0");
    CcdpUtils.setProperty("resourceIntf.ec2.min.number.free.agents", "0");
    CcdpUtils.setProperty("resourceIntf.nifi.min.number.free.agents", "0");
    
    System.out.println("\n ***************************************************************************** \n");
    
  }

  /**
   * Testing the checkFreeVMRequirements() function
   * Making sure there are no vms running
   */
  @Ignore
  @Test
  public void ZeroFreeVMTest()
  {
    this.logger.info("Running ZeroFreeVMTest");
    
    ccdpEngine= new CcdpMainApplication(null);
    assertNotNull("The application should not be null", ccdpEngine);
    //waiting for the engine to get settle and launch vms if need
    double pauseTime = ccdpEngine.getTimerDelay()/1000 + 10;
    CcdpUtils.pause(pauseTime);
    Map<String, List<CcdpVMResource>> resources = ccdpEngine.getResources();
    for(String sid : resources.keySet()){
      int numberOfVM = resources.get(sid).size();
      assertEquals(0,numberOfVM);
    }
  }

  /**
   * Testing the checkFreeVMRequirements() function
   * Making sure there is only one vm running and is of session DEFAULT
   */
  @Ignore
  @Test(timeout=120000)//test fails if it takes longer than 2 min
  public void OneFreeVMforDefault()
  {
    this.logger.info("Running OneFreeVMforDefault");
    CcdpUtils.setProperty("resourceIntf.default.min.number.free.agents", "1");
    ccdpEngine= new CcdpMainApplication(null);
    assertNotNull("The application should not be null", ccdpEngine);
    //waiting for the engine to get settle and launch vms if need
    double pauseTime = ccdpEngine.getTimerDelay()/1000 + 10;
    CcdpUtils.pause(pauseTime);
    
    Map<String, List<CcdpVMResource>> resources = ccdpEngine.getResources();
    for(String sid : resources.keySet()){
      int numberOfVM = resources.get(sid).size();
      if(sid.equals("DEFAULT")) {
        assertEquals(1,numberOfVM);
      }else {
        assertEquals(0,numberOfVM);
      }
    }
    waitForResourUpdate("DEFAULT",0);
    String vmStatus = resources.get("DEFAULT").get(0).getStatus().toString();
    assertEquals("RUNNING",vmStatus);
     
  }
  /**
   * Testing the checkFreeVMRequirements() function
   * Making sure there is only one vm running and is of session EC2
   */
  @Ignore
  @Test(timeout=120000)//test fails if it takes longer than 2 min
  public void OneFreeVMforEC2()
  {
    this.logger.info("Running OneFreeVMforEC2");
    CcdpUtils.setProperty("resourceIntf.ec2.min.number.free.agents", "1");
    ccdpEngine= new CcdpMainApplication(null);
    assertNotNull("The application should not be null", ccdpEngine);
    //waiting for the engine to get settle and launch vms if need
    double pauseTime = ccdpEngine.getTimerDelay()/1000 + 10;
    CcdpUtils.pause(pauseTime);
    
    Map<String, List<CcdpVMResource>> resources = ccdpEngine.getResources();
    for(String sid : resources.keySet()){
      int numberOfVM = resources.get(sid).size();
      if(sid.equals("EC2")) {
        assertEquals(1,numberOfVM);
      }else {
        assertEquals(0,numberOfVM);
      }
    }
    waitForResourUpdate("EC2",0);
    String vmStatus = resources.get("EC2").get(0).getStatus().toString();
    assertEquals("RUNNING",vmStatus);
  }
  /**
   * Testing the checkFreeVMRequirements() function
   * Making sure there is only one vm running and is of session NIFI
   */
  @Ignore
  @Test(timeout=120000)//test fails if it takes longer than 2 min
  public void OneFreeVMforNifi()
  {
    this.logger.info("Running OneFreeVMforNifi");
    CcdpUtils.setProperty("resourceIntf.nifi.min.number.free.agents", "1");
    ccdpEngine= new CcdpMainApplication(null);
    assertNotNull("The application should not be null", ccdpEngine);
    //waiting for the engine to get settle and launch vms if need
    double pauseTime = ccdpEngine.getTimerDelay()/1000 + 10;
    CcdpUtils.pause(pauseTime);
    
    Map<String, List<CcdpVMResource>> resources = ccdpEngine.getResources();
    for(String sid : resources.keySet()){
      int numberOfVM = resources.get(sid).size();
      if(sid.equals("NIFI")) {
        assertEquals(1,numberOfVM);
      }else {
        assertEquals(0,numberOfVM);
      }
    }
    
    waitForResourUpdate("NIFI",0);
    String vmStatus = resources.get("NIFI").get(0).getStatus().toString();
    assertEquals("RUNNING",vmStatus);
  }
  
  /**
   * Testing that the engine launches free VMs when needed based on the config file
   * and that it terminate VMs when it has extra. 
   */
  @Ignore
  @Test(timeout=180000)//test fails if it takes longer than 3 min
  public void TestCheckFreeVMRequirements() {
    CcdpUtils.setProperty("resourceIntf.default.min.number.free.agents", "1");
    CcdpUtils.setProperty("resourceIntf.ec2.min.number.free.agents", "1");
    CcdpUtils.setProperty("resourceIntf.nifi.min.number.free.agents", "1");
    ccdpEngine= new CcdpMainApplication(null);
    //waiting for the onEvent function to be called 
    double pauseTime = ccdpEngine.getTimerDelay()/1000;
    CcdpUtils.pause(pauseTime + 10);
    Map<String, List<CcdpVMResource>> resources = ccdpEngine.getResources();
    assertEquals(1,resources.get("DEFAULT").size());
    assertEquals(1,resources.get("EC2").size());
    assertEquals(1,resources.get("NIFI").size());
    //wait until all VMs are running and sending hb
    waitForResourUpdate("EC2",0);
    waitForResourUpdate("NIFI",0);
    waitForResourUpdate("DEFAULT",0);
    
    CcdpUtils.setProperty("resourceIntf.default.min.number.free.agents", "0");
    CcdpUtils.pause(pauseTime + 10);
    assertEquals(0,resources.get("DEFAULT").size());
    assertEquals(1,resources.get("EC2").size());
    assertEquals(1,resources.get("NIFI").size());
    
    CcdpUtils.setProperty("resourceIntf.nifi.min.number.free.agents", "0");
    CcdpUtils.pause(pauseTime + 10);
    assertEquals(0,resources.get("DEFAULT").size());
    assertEquals(1,resources.get("EC2").size());
    assertEquals(0,resources.get("NIFI").size());
    
    CcdpUtils.setProperty("resourceIntf.ec2.min.number.free.agents", "0");
    CcdpUtils.pause(pauseTime + 10);
    assertEquals(0,resources.get("DEFAULT").size());
    assertEquals(0,resources.get("EC2").size());
    assertEquals(0,resources.get("NIFI").size());
  }
  
  /**
   * Test  removeUnresponsiveResources() function
   * test that when a single resource stops updating
   * the engine removes it and tries to shut it down. 
   * It also launches a new vm if it is necessary
   */
  @Ignore
  @Test
  public void TestRemoveUnresponsiceVM() {
    this.logger.info("Running  TestRemoveUnresponsiceVM");
    CcdpObjectFactory factory = CcdpObjectFactory.newInstance();
    ObjectNode res_ctr_node =
        CcdpUtils.getJsonKeysByFilter(CcdpUtils.CFG_KEY_RESOURCE);
    this.controller = factory.getCcdpResourceController(res_ctr_node);
    
    CcdpUtils.setProperty("resourceIntf.default.min.number.free.agents", "1");
    CcdpImageInfo imgCfg = CcdpUtils.getImageInfo(CcdpNodeType.DEFAULT);
    imgCfg.setMinReq(1);
    imgCfg.setMaxReq(1);
    imgCfg.setSessionId("DEFAULT");
    List<String> launched = this.controller.startInstances(imgCfg);
    assertEquals(1, launched.size());
    String InstanceID = launched.get(0);
    //regitering Producer for new VM
    this.connection.registerProducer(InstanceID);
    CcdpUtils.pause(WAIT_TIME_LAUNCH_VM);
    ccdpEngine= new CcdpMainApplication(null);
    //waiting for the onEvent function to be called 
    double pauseTime = ccdpEngine.getTimerDelay()/1000 + 10;
    CcdpUtils.pause(pauseTime);
  
    Map<String, List<CcdpVMResource>> resources = ccdpEngine.getResources();
    List<CcdpVMResource> vms = resources.get("DEFAULT");
    int numberOfVM = vms.size();
    assertEquals(1,numberOfVM);
    
    assertEquals(InstanceID, vms.get(0).getInstanceId());
   
    //Manually shutting down vm to test the removeUnresponsiveVM function
    List<String> vmList = new ArrayList<>();
    vmList.add(InstanceID);
    ShutdownMessage shutdownMsg = new ShutdownMessage();
    this.connection.sendCcdpMessage(InstanceID, shutdownMsg);
    this.controller.terminateInstances(vmList);
    
    //wait for the onEvent function to be called and the new vm if need to be started
    pauseTime = ccdpEngine.getTimerPeriod()/1000 + 25;
    CcdpUtils.pause( pauseTime );

    vms = resources.get("DEFAULT");
    numberOfVM = vms.size();
    assertEquals(1,numberOfVM);

    String newInstance = vms.get(0).getInstanceId();
    //assert that the old vm was removed and the 
    //new vm was launched
    assertNotEquals(InstanceID, newInstance);
  }
  
  /**
   * Test  removeUnresponsiveResources() function
   * test that when multiple resource stops updating
   * the engine removes them and tries to shut them down. 
   * It also launches a new vm if it is necessary
   */
  @Ignore
  @Test
  public void TestRemoveMultipleUnresponsiceVM() {
  CcdpUtils.setProperty("resourceIntf.default.min.number.free.agents", "1");
  CcdpUtils.setProperty("resourceIntf.ec2.min.number.free.agents", "1");
  CcdpUtils.setProperty("resourceIntf.nifi.min.number.free.agents", "1");
  ccdpEngine= new CcdpMainApplication(null);
  //waiting for the onEvent function to be called 
  double pauseTime = ccdpEngine.getTimerDelay()/1000;
  CcdpUtils.pause(pauseTime + 10);
  Map<String, List<CcdpVMResource>> resources = ccdpEngine.getResources();
  assertEquals(1,resources.get("DEFAULT").size());
  assertEquals(1,resources.get("EC2").size());
  assertEquals(1,resources.get("NIFI").size());
  waitForResourUpdate("EC2",0);
  waitForResourUpdate("NIFI",0);
  waitForResourUpdate("DEFAULT",0);
  
  String defaultVMID = resources.get("DEFAULT").get(0).getInstanceId();
  String ec2VMID = resources.get("EC2").get(0).getInstanceId();
  String nifiVMID = resources.get("NIFI").get(0).getInstanceId();
  
  ShutdownMessage shutdownMsg = new ShutdownMessage();
  this.connection.sendCcdpMessage(defaultVMID, shutdownMsg);
  this.connection.sendCcdpMessage(ec2VMID, shutdownMsg);
  this.connection.sendCcdpMessage(nifiVMID, shutdownMsg);
  //waiting for the onEvent function to be called 
  CcdpUtils.pause(pauseTime + 25);
  
  assertEquals(1,resources.get("DEFAULT").size());
  assertEquals(1,resources.get("EC2").size());
  assertEquals(1,resources.get("NIFI").size());
  
  waitForResourUpdate("EC2",0);
  waitForResourUpdate("NIFI",0);
  waitForResourUpdate("DEFAULT",0);
  
  assertNotEquals(defaultVMID,resources.get("DEFAULT").get(0).getInstanceId());
  assertNotEquals(ec2VMID , resources.get("EC2").get(0).getInstanceId());
  assertNotEquals(nifiVMID , resources.get("NIFI").get(0).getInstanceId());
  
  }
  
  /**
   * Test that the engine receives a task and assigns it to a vm
   */
  @Ignore
  @Test
  public void handlingThreadRequest()
  {
    this.logger.info("Running handlingThreadRequest");
    try{
      //changing the number of free require agents
      CcdpUtils.setProperty("resourceIntf.default.min.number.free.agents", "1");
      //running main engine
      ccdpEngine= new CcdpMainApplication(null);

      assertNotNull("The application should not be null", ccdpEngine);
      //waiting for the engine to get settle and launch vms if need
      double pauseTime = ccdpEngine.getTimerDelay()/1000 + 10;
      CcdpUtils.pause(pauseTime);

      Map<String, List<CcdpVMResource>> resources = ccdpEngine.getResources();
      int numberOfVM = resources.get("DEFAULT").size();
      assertEquals(1,numberOfVM);
      
      List<String> cmd = new ArrayList<String>();
      cmd.add( "/data/ccdp/ccdp-engine/python/ccdp_mod_test.py");
      cmd.add("-a");
      cmd.add("testRandomTime");
      cmd.add( "-p");
      cmd.add("min=10,max=20");
      String taskId = sendTaskRequest("DEFAULT","random time","Test1",
          testChannel, 0.0, cmd,null, this.mainChannel);
      //wait for the task to be launched and the new vm if need to be started
      pauseTime = ccdpEngine.getTimerPeriod()/1000 + 10;
      CcdpUtils.pause( pauseTime );
      
      numberOfVM = resources.get("DEFAULT").size();
      assertEquals(1, numberOfVM);
      numberOfVM = resources.get("Test1").size();
      assertEquals(1, numberOfVM);
      CcdpVMResource vm =  resources.get("Test1").get(0);
      assertEquals(1, vm.getTasks().size());

      String defaultVM = resources.get("DEFAULT").get(0).getInstanceId();
      String test1VM = resources.get("Test1").get(0).getInstanceId();
      assertNotEquals(defaultVM,test1VM);
      waitForTaskStatus("SUCCESSFUL", taskId);
      assertEquals(CcdpTaskState.SUCCESSFUL, taskMap.get(taskId).getState());
      //wait for resources to be updated
      pauseTime = ccdpEngine.getTimerPeriod()/1000 + 10;
      CcdpUtils.pause( pauseTime );
      
      //after the task is completed there should not be any request left
      assertEquals(0, ccdpEngine.getRequests().size());
      

    }catch(Exception e) {
      System.out.println(e);
    }
  }
   /**
   * Test That the engine runs multiple task in a single vm when needed
   * and that it only running one task on a vm when needed. 
   */
  @Ignore 
  @Test(timeout=180000)//test fails if it takes longer than 3 min
  public void RunningMultipleTaskRequest() {
    
    CcdpUtils.setProperty("resourceIntf.default.min.number.free.agents", "1");
    CcdpUtils.setProperty("resourceIntf.nifi.min.number.free.agents", "1");

    ccdpEngine= new CcdpMainApplication(null);
    //waiting for the onEvent function to be called 
    double pauseTime = ccdpEngine.getTimerDelay()/1000 + 10;
    CcdpUtils.pause(pauseTime);
  
    Map<String, List<CcdpVMResource>> resources = ccdpEngine.getResources();
    
    //making sure there are two VMs running based on the config properties
    assertEquals(1,resources.get("DEFAULT").size());
    assertEquals(1,resources.get("NIFI").size());
    String defaultVMid = resources.get("DEFAULT").get(0).getInstanceId();
    String nifiVMid = resources.get("NIFI").get(0).getInstanceId();
    
    //making sure that the two running VMs are not equals
    assertNotEquals(defaultVMid, nifiVMid);
    
    //test sending new tast 
    List<String> cmd = new ArrayList<String>();
    cmd.add( "/data/ccdp/ccdp-engine/python/ccdp_mod_test.py");
    cmd.add("-a");
    cmd.add("testRandomTime");
    cmd.add( "-p");
    cmd.add("min=10,max=20");
    List<String> nifi_cmd = new ArrayList<String>();
    nifi_cmd.add( "/data/ccdp/run_nifi.sh");
    nifi_cmd.add("run");
    String taskId1 = sendTaskRequest("DEFAULT","random time","Test1",
        testChannel, 0.0, cmd,null, this.mainChannel);
    String taskId2 = sendTaskRequest("DEFAULT","random time","Test1",
        testChannel, 0.0, cmd,null, this.mainChannel);
    String nifiTask = sendTaskRequest("NIFI","NiFi Start","Test-nifi",
        testChannel, 100.0, nifi_cmd, "Starts NiFi Application", this.mainChannel);
    //wait for the task to be launched and the new vm if need to be started
    pauseTime = ccdpEngine.getTimerPeriod()/1000 + 10;
    CcdpUtils.pause( pauseTime );
    
    //making sure each session only has one vm 
    assertEquals(1,resources.get("DEFAULT").size());
    assertEquals(1,resources.get("Test1").size());
    assertEquals(1,resources.get("Test-nifi").size());
    //making sure we give time to the nife vm to be launched
    //mainly because the sim controller sometimes kill the VM randomly 
    if(resources.get("NIFI").size() == 0) {
      CcdpUtils.pause( pauseTime );
    }
    assertEquals(1,resources.get("NIFI").size());
    
    //making sure that the tasks are running on the appropriate session and VM
    assertEquals(2,  resources.get("Test1").get(0).getTasks().size());
    assertEquals(1,  resources.get("Test-nifi").get(0).getTasks().size());
    
    
    String defaultVM = resources.get("DEFAULT").get(0).getInstanceId();
    String test1VM = resources.get("Test1").get(0).getInstanceId();
    String nifiTestVM = resources.get("Test-nifi").get(0).getInstanceId();
    String nifiVM = resources.get("NIFI").get(0).getInstanceId();
    
    //making sure that task were assigned to the existent VMs base on the Node-type required
    assertEquals(defaultVMid, test1VM);
    assertEquals(nifiVMid, nifiTestVM);
    
    //making sure every instance running on the different session are different
    assertNotEquals(defaultVM,test1VM);
    assertNotEquals(defaultVM,nifiTestVM);
    assertNotEquals(nifiVM,test1VM);
    assertNotEquals(nifiVM,nifiTestVM);
    assertNotEquals(nifiTestVM,test1VM);
    
    //there has to be three different request running 
    assertEquals(3,ccdpEngine.getRequests().size());
   
    waitForTaskStatus("RUNNING", nifiTask);
    assertEquals(CcdpTaskState.RUNNING, taskMap.get(nifiTask).getState());
    waitForTaskStatus("SUCCESSFUL", taskId1);
    assertEquals(CcdpTaskState.SUCCESSFUL, taskMap.get(taskId1).getState());
    waitForTaskStatus("SUCCESSFUL", taskId2);
    assertEquals(CcdpTaskState.SUCCESSFUL, taskMap.get(taskId2).getState());
    CcdpUtils.pause( 3 );
  //there has to be only one request running since taskId1 and taskId2 where successfully completed
    assertEquals(1,ccdpEngine.getRequests().size());
    
    //testing the killTask function
    //killing/stopping the nifi process running on the Test-nifi session
    KillTaskMessage killTask = new KillTaskMessage();
    nifi_cmd.remove(1);
    nifi_cmd.add("stop");
    CcdpTaskRequest task = taskMap.get(nifiTask);
    task.setCommand(nifi_cmd);
    killTask.setTask(task);
    connection.sendCcdpMessage(this.mainChannel, killTask);
    
    //waiting until nifi task is killed and there should not be any 
    //request left in the queue 
    int request_Left = ccdpEngine.getRequests().size();
    while(request_Left > 0) {
      request_Left = ccdpEngine.getRequests().size();
    }
    
    CcdpUtils.pause( pauseTime );
    //making sure there are two VMs running based on the config properties
    assertEquals(1,resources.get("DEFAULT").size());
    assertEquals(1,resources.get("NIFI").size());
    assertEquals(0,resources.get("Test1").size());
    assertEquals(0,resources.get("Test-nifi").size());
  }
  /**
   * Test the task allocation based on the NumberTasksComtrollerImpl
   * if the task's CPU not equals 100 the engine should not assign more than
   *  taskContrIntf.allocate.no.more.than task in one vm 
   */
  @Ignore
  @Test
  public void TaskAllocationBasedOn_NumberTasksControllerImpl() {
    CcdpUtils.setProperty("resourceIntf.default.min.number.free.agents", "1");
    CcdpUtils.setProperty("task.allocator.intf.classname","com.axios.ccdp.controllers.NumberTasksControllerImpl");
    CcdpUtils.setProperty("taskContrIntf.allocate.no.more.than", "2");
    ccdpEngine= new CcdpMainApplication(null);
    //waiting for the onEvent function to be called 
    double pauseTime = ccdpEngine.getTimerDelay()/1000 + 10;
    CcdpUtils.pause(pauseTime);
  
    Map<String, List<CcdpVMResource>> resources = ccdpEngine.getResources();
   //making sure there are two VMs running based on the config properties
    assertEquals(1,resources.get("DEFAULT").size());
    
    waitForResourUpdate("DEFAULT",0);
    
    List<String> cmd = new ArrayList<String>();
    cmd.add( "/data/ccdp/ccdp-engine/python/ccdp_mod_test.py");
    cmd.add("-a");
    cmd.add("testCpuUsage");
    cmd.add( "-p");
    cmd.add("120");
    String taskId1 = sendTaskRequest("NIFI","Test CPU","Test1",
        testChannel, 0.0, cmd,null, this.mainChannel);
    
    CcdpUtils.pause(30);
    
    String taskId2 = sendTaskRequest("NIFI","Test CPU","Test1",
        testChannel, 0.0, cmd,null, this.mainChannel);

    //waiting until nifi task is killed and there should not be any 
    //request left in the queue 
    int request_Left = ccdpEngine.getRequests().size();
    while(request_Left > 0) {
      request_Left = ccdpEngine.getRequests().size();
    }
    
    CcdpUtils.pause(pauseTime);
    
  }
  /**
   * Test the task allocation based on the AvgLoadControllerImpl
   * if the task's the CPU not equals 100 the engine should not assign more task to a vm
   * if the mem usage greater or equals taskContrIntf.allocate.avg.load.mem or the 
   * cpuLoad usage greater or equals askContrIntf.allocate.avg.load.cpu
   * 
   */
  
  @Test 
  public void TaskAllocationBasedOn_AvgLoadControllerImpl() {
    CcdpUtils.setProperty("resourceIntf.nifi.min.number.free.agents", "1");
    CcdpUtils.setProperty("task.allocator.intf.classname","com.axios.ccdp.controllers.AvgLoadControllerImpl");
    CcdpUtils.setProperty("taskContrIntf.allocate.avg.load.cpu","75");
    CcdpUtils.setProperty("taskContrIntf.allocate.avg.load.mem","15");
    
    ccdpEngine= new CcdpMainApplication(null);
    //waiting for the onEvent function to be called 
    double pauseTime = ccdpEngine.getTimerDelay()/1000 + 10;
    CcdpUtils.pause(pauseTime);
  
    Map<String, List<CcdpVMResource>> resources = ccdpEngine.getResources();
   //making sure there are two VMs running based on the config properties
    assertEquals(1,resources.get("NIFI").size());
    
    waitForResourUpdate("NIFI",0);
    
    List<String> cmd = new ArrayList<String>();
    cmd.add( "/data/ccdp/ccdp-engine/python/ccdp_mod_test.py");
    cmd.add("-a");
    cmd.add("testCpuUsage");
    cmd.add( "-p");
    cmd.add("90");
    String taskId1 = sendTaskRequest("NIFI","Test CPU 1","Test1",
        testChannel, 0.0, cmd,null, this.mainChannel);
    
    CcdpUtils.pause(25);
    //because the task sent make the CPU go to 100% the AvgLoad controller
    //should allocate a new vm to this session
    assertEquals(2,resources.get("Test1").size() );
    assertEquals(1,resources.get("NIFI").size());
    
    cmd.remove(4);
    cmd.add("50");
    String taskId2 = sendTaskRequest("NIFI","Test CPU 2","Test1",
        testChannel, 0.0, cmd,null, this.mainChannel);
    
    CcdpUtils.pause(30);
    
    //because the two tasks sent make the CPU go to 100% the AvgLoad controller
    //should allocate a new vm to this session
    assertEquals(3,resources.get("Test1").size() );
    assertEquals(1,resources.get("NIFI").size());
    
    cmd.remove(4);
    cmd.add("30");
    String taskId3 = sendTaskRequest("NIFI","Test CPU 3","Test1",
        testChannel, 0.0, cmd,null, this.mainChannel);
    
    CcdpUtils.pause(25);
    //because the three tasks sent make the CPU go to 100% the AvgLoad controller
    //should allocate a new vm to this session
    assertEquals(4,resources.get("Test1").size() );
    assertEquals(1,resources.get("NIFI").size());
    
    //waiting until nifi task is killed and there should not be any 
    //request left in the queue 
    int request_Left = ccdpEngine.getRequests().size();
    while(request_Left > 0) {
      request_Left = ccdpEngine.getRequests().size();
    }
    
    CcdpUtils.pause(pauseTime);
    
  }
  
  /**
   * Helper Function Used to send task request to the engine
   * @param NodeType task NodeType
   * @param TaskName task name
   * @param SessionId what group/session the task belongs to 
   * @param ReplyTo the channel where the replies will be send to
   * @param cpu how much cpu is require for the task
   * @param command the command to be executed
   * @param Description
   * @param channel where to send the task request
   */
  private String sendTaskRequest(String NodeType, String TaskName, String SessionId,
      String ReplyTo, double cpu,  List<String> command, String Description, String channel) {
    CcdpTaskRequest task = new CcdpTaskRequest();
    task.setNodeType(NodeType);
    task.setName(TaskName);
    task.setSessionId(SessionId);
    task.setReplyTo(ReplyTo);
    task.setCPU(cpu);
    task.setDescription(Description);
    task.setCommand(command);
    List<CcdpTaskRequest> tasks = new ArrayList<CcdpTaskRequest>();
    tasks.add(task);
    CcdpThreadRequest req = new CcdpThreadRequest();
    req.setTasks(tasks);
    req.setSessionId(SessionId);
    ThreadRequestMessage msg = new ThreadRequestMessage();
    msg.setRequest(req);
    connection.sendCcdpMessage(channel, msg);

    this.taskMap.put(task.getTaskId(), task);
    return task.getTaskId();
  }
  /**
   * Helper function used to check when the vm 
   * has started to send hb and the status is now 
   * set to running
   * @param sid session id of the vm we are waiting for updates
   * @param the index in which the vm is store in the list
   */
  private void waitForResourUpdate(String sid, int index) {
    boolean stateUpdated = false;
    
    while(!stateUpdated) {
      
      if(!ResourceStatus.LAUNCHED.equals(
          ccdpEngine.getResources().get(sid).get(index).getStatus())) 
      {
        stateUpdated = true;
      }
    }
  }
  /**
  * Functions used to wait until the specified status of the task 
  * has been set/obtained
  * @param status the desired tasks status that we need to wait for
  * @param taskId the id of the task we want to wait for the status
  **/
  private void waitForTaskStatus(String status, String taskId){
    boolean foundStatus = false;
    while(!foundStatus){
       String state = taskMap.get(taskId).getState().toString();
       if(state.equals(status)){
         foundStatus = true;
       }
    }
  }
  @Override
  public void onCcdpMessage(CcdpMessage message)
  {
    this.logger.debug("Got a new message");
    CcdpMessageType msgType = CcdpMessageType.get( message.getMessageType() );
    this.logger.debug("Got a new Message: " + msgType.toString());
    switch( msgType )
    {
    case RESOURCE_UPDATE:
      ResourceUpdateMessage msg = (ResourceUpdateMessage)message;
      this.logger.debug(msg.toString());
      break;
    case TASK_UPDATE:
      CcdpTaskRequest task = ((TaskUpdateMessage)message).getTask();
      String tid = task.getTaskId();
      CcdpTaskRequest.CcdpTaskState state = task.getState();
      this.logger.debug(tid + " Updated task to " + state.toString());
      this.taskMap.put(tid, task);
    default:
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
    ccdpEngine.stopCCDPApplication(true);
    this.connection.disconnect();
    this.taskMap.clear();
  }




}
