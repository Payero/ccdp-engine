package com.axios.ccdp.test.unittest.MainApplication;

import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;

import org.apache.log4j.Logger;
import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.Ignore;


import com.axios.ccdp.connections.intfs.CcdpConnectionIntf;
import com.axios.ccdp.connections.intfs.CcdpMessageConsumerIntf;
import com.axios.ccdp.factory.CcdpObjectFactory;
import com.axios.ccdp.fmwk.CcdpMainApplication;
import com.axios.ccdp.messages.CcdpMessage;
import com.axios.ccdp.messages.ResourceUpdateMessage;
import com.axios.ccdp.messages.TaskUpdateMessage;
import com.axios.ccdp.messages.ThreadRequestMessage;
import com.axios.ccdp.messages.CcdpMessage.CcdpMessageType;
import com.axios.ccdp.messages.KillTaskMessage;
import com.axios.ccdp.messages.ShutdownMessage;
import com.axios.ccdp.resources.CcdpVMResource;
import com.axios.ccdp.resources.CcdpVMResource.ResourceStatus;
import com.axios.ccdp.tasking.CcdpTaskRequest;
import com.axios.ccdp.tasking.CcdpTaskRequest.CcdpTaskState;
import com.axios.ccdp.tasking.CcdpThreadRequest;
import com.axios.ccdp.test.unittest.JUnitTestHelper;
import com.axios.ccdp.utils.CcdpUtils;
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
	protected static String CcdpVMcontroller = "com.axios.ccdp.cloud.docker.DockerVMControllerImpl";

	/**
	 * Stores the name  of the class used handle different storage solution
	 */
	protected static String CcdpVMStorageController = "com.axios.ccdp.cloud.docker.DockerStorageControllerImpl";

	/**
	 * Stores the name of the class used to handle the system monitoring
	 */
	protected static String ClassMonitorIntf = "com.axios.ccdp.cloud.docker.DockerResourceMonitorImpl";
	/**
	 * Stores how many more second we want to add to the timer delay to allow the test and engine to process
	 * information/request 
	 * It is used for pausing
	 */
	protected static double addSecond = 15;

	@BeforeClass
	public static void initialization() {
		//load the config file before every test case
		JUnitTestHelper.initialize();
		System.out.println("Im in the class with the test cases");
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

		//setting resource monitor property to the appropriate name
		CcdpUtils.setProperty("res.mon.intf.classname", CcdpMainApplicationTests.ClassMonitorIntf);

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

	@Test
	public void ZeroFreeVMTest()
	{
		this.logger.info("Running ZeroFreeVMTest");

		ccdpEngine= new CcdpMainApplication(null);
		assertNotNull("The application should not be null", ccdpEngine);
		//waiting for the engine to get settle and launch vms if need
		double pauseTime = ccdpEngine.getTimerDelay()/1000 + addSecond;
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

	@Test(timeout=120000)//test fails if it takes longer than 2 min
	public void OneFreeVMforDefault()
	{
		this.logger.info("Running OneFreeVMforDefault");
		//CcdpUtils.setProperty("resourceIntf.default.min.number.free.agents", "1");
		CcdpUtils.setProperty("resourceIntf.default.min.number.free.agents", "3");
		ccdpEngine= new CcdpMainApplication(null);
		assertNotNull("The application should not be null", ccdpEngine);
		//waiting for the engine to get settle and launch vms if need
		double pauseTime = ccdpEngine.getTimerDelay()/1000 + addSecond;
		CcdpUtils.pause(pauseTime);

		Map<String, List<CcdpVMResource>> resources = ccdpEngine.getResources();
		for(String sid : resources.keySet()){
			int numberOfVM = resources.get(sid).size();
			if(sid.equals("DEFAULT")) {
				assertEquals(3,numberOfVM);
			}else {
				assertEquals(0,numberOfVM);
			}
		}
		waitUntilVMisRunning("DEFAULT");
		String vmStatus = resources.get("DEFAULT").get(0).getStatus().toString();
		assertEquals("RUNNING",vmStatus);
	}
	/**
	 * Testing the checkFreeVMRequirements() function
	 * Making sure there is only one vm running and is of session EC2
	 */

	@Test (timeout=120000)//test fails if it takes longer than 2 min
	public void OneFreeVMforEC2()
	{
		this.logger.info("Running OneFreeVMforEC2");
		CcdpUtils.setProperty("resourceIntf.ec2.min.number.free.agents", "1");
		ccdpEngine= new CcdpMainApplication(null);
		assertNotNull("The application should not be null", ccdpEngine);
		//waiting for the engine to get settle and launch vms if need
		double pauseTime = ccdpEngine.getTimerDelay()/1000 + addSecond;
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
		waitUntilVMisRunning("EC2");
		String vmStatus = resources.get("EC2").get(0).getStatus().toString();
		assertEquals("RUNNING",vmStatus);
	}
	/**
	 * Testing the checkFreeVMRequirements() function
	 * Making sure there is only one vm running and is of session NIFI
	 */

	@Test(timeout=120000)//test fails if it takes longer than 2 min
	public void OneFreeVMforNifi()
	{
		this.logger.info("Running OneFreeVMforNifi");
		CcdpUtils.setProperty("resourceIntf.nifi.min.number.free.agents", "1");
		ccdpEngine= new CcdpMainApplication(null);
		assertNotNull("The application should not be null", ccdpEngine);
		//waiting for the engine to get settle and launch vms if need
		double pauseTime = ccdpEngine.getTimerDelay()/1000 + addSecond;
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

		waitUntilVMisRunning("NIFI");
		String vmStatus = resources.get("NIFI").get(0).getStatus().toString();
		assertEquals("RUNNING",vmStatus);
	}

	/**
	 * Testing that the engine launches free VMs when needed based on the config file
	 * and that it terminate VMs when it has extra. 
	 */

	@Test(timeout=180000)//test fails if it takes longer than 3 min
	public void TestCheckFreeVMRequirements() {
		CcdpUtils.setProperty("resourceIntf.default.min.number.free.agents", "1");
		CcdpUtils.setProperty("resourceIntf.ec2.min.number.free.agents", "1");
		CcdpUtils.setProperty("resourceIntf.nifi.min.number.free.agents", "1");
		ccdpEngine= new CcdpMainApplication(null);
		//waiting for the onEvent function to be called the fist time 
		double pauseTime = ccdpEngine.getTimerDelay()/1000 + addSecond;
		CcdpUtils.pause(pauseTime);

		Map<String, List<CcdpVMResource>> resources = ccdpEngine.getResources();
		assertEquals(1,resources.get("DEFAULT").size());
		assertEquals(1,resources.get("EC2").size());
		assertEquals(1,resources.get("NIFI").size());
		//wait until all VMs are running and sending hb
		waitUntilVMisRunning("EC2");
		waitUntilVMisRunning("NIFI");
		waitUntilVMisRunning("DEFAULT");
		//waiting for the onEvent function to be called
		pauseTime = ccdpEngine.getTimerPeriod()/1000 + addSecond;

		CcdpUtils.setProperty("resourceIntf.default.min.number.free.agents", "0");
		CcdpUtils.pause(pauseTime);
		assertEquals(0,resources.get("DEFAULT").size());
		assertEquals(1,resources.get("EC2").size());
		assertEquals(1,resources.get("NIFI").size());

		CcdpUtils.setProperty("resourceIntf.nifi.min.number.free.agents", "0");
		CcdpUtils.pause(pauseTime);
		assertEquals(0,resources.get("DEFAULT").size());
		assertEquals(1,resources.get("EC2").size());
		assertEquals(0,resources.get("NIFI").size());

		CcdpUtils.setProperty("resourceIntf.ec2.min.number.free.agents", "0");
		CcdpUtils.pause(pauseTime);
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

	@Test (timeout=240000)//test fails if it takes longer than 4 min
	public void TestRemoveUnresponsiveVM() {
		this.logger.info("Running  TestRemoveUnresponsiceVM");
		CcdpUtils.setProperty("resourceIntf.default.min.number.free.agents", "1");
		ccdpEngine= new CcdpMainApplication(null);
		//waiting for the onEvent function to be called 
		double pauseTime = ccdpEngine.getTimerDelay()/1000 + addSecond;
		CcdpUtils.pause(pauseTime);

		Map<String, List<CcdpVMResource>> resources = ccdpEngine.getResources();
		assertEquals(1,resources.get("DEFAULT").size());
		waitUntilVMisRunning("DEFAULT");

		String defaultVMID = resources.get("DEFAULT").get(0).getInstanceId();

		//Manually shutting down vm to test the removeUnresponsiveVM function
		ShutdownMessage shutdownMsg = new ShutdownMessage();
		this.connection.sendCcdpMessage(defaultVMID, shutdownMsg);

		//wait for the onEvent function to be called and the new vm if need to be started
		pauseTime = ccdpEngine.getTimerPeriod()/1000 + addSecond;
		CcdpUtils.pause( 2 * pauseTime );

		assertEquals(1,resources.get("DEFAULT").size());

		waitUntilVMisRunning("DEFAULT");

		//assert that the old vm was removed and the 
		//new vm was launched
		assertNotEquals(defaultVMID,resources.get("DEFAULT").get(0).getInstanceId());
	}

	/**
	 * Test  removeUnresponsiveResources() function
	 * test that when multiple resource stops updating
	 * the engine removes them and tries to shut them down. 
	 * It also launches a new vm if it is necessary
	 */

	@Test (timeout=240000)//test fails if it takes longer than 4 min
	public void TestRemoveMultipleUnresponsiveVM() {
		CcdpUtils.setProperty("resourceIntf.default.min.number.free.agents", "1");
		CcdpUtils.setProperty("resourceIntf.ec2.min.number.free.agents", "1");
		CcdpUtils.setProperty("resourceIntf.nifi.min.number.free.agents", "1");
		ccdpEngine= new CcdpMainApplication(null);
		//waiting for the onEvent function to be called 
		double pauseTime = ccdpEngine.getTimerDelay()/1000 + addSecond;
		CcdpUtils.pause(pauseTime);
		Map<String, List<CcdpVMResource>> resources = ccdpEngine.getResources();
		assertEquals(1,resources.get("DEFAULT").size());
		assertEquals(1,resources.get("EC2").size());
		assertEquals(1,resources.get("NIFI").size());
		waitUntilVMisRunning("EC2");
		waitUntilVMisRunning("NIFI");
		waitUntilVMisRunning("DEFAULT");

		String defaultVMID = resources.get("DEFAULT").get(0).getInstanceId();
		String ec2VMID = resources.get("EC2").get(0).getInstanceId();
		String nifiVMID = resources.get("NIFI").get(0).getInstanceId();

		ShutdownMessage shutdownMsg = new ShutdownMessage();
		this.connection.sendCcdpMessage(defaultVMID, shutdownMsg);
		this.connection.sendCcdpMessage(ec2VMID, shutdownMsg);
		this.connection.sendCcdpMessage(nifiVMID, shutdownMsg);
		//waiting for the onEvent function to be called 
		//waiting for the onEvent function to be called
		pauseTime = ccdpEngine.getTimerPeriod()/1000 + addSecond;
		CcdpUtils.pause(2 * pauseTime);

		assertEquals(1,resources.get("DEFAULT").size());
		assertEquals(1,resources.get("EC2").size());
		assertEquals(1,resources.get("NIFI").size());

		waitUntilVMisRunning("EC2");
		waitUntilVMisRunning("NIFI");
		waitUntilVMisRunning("DEFAULT");

		assertNotEquals(defaultVMID,resources.get("DEFAULT").get(0).getInstanceId());
		assertNotEquals(ec2VMID , resources.get("EC2").get(0).getInstanceId());
		assertNotEquals(nifiVMID , resources.get("NIFI").get(0).getInstanceId());

	}

	/**
	 * Test that the engine receives a task and assigns it to a vm
	 */

	@Test (timeout=180000)//test fails if it takes longer than 3 min
	public void handlingThreadRequest()
	{
		this.logger.info("Running handlingThreadRequest");

		//changing the number of free require agents
		CcdpUtils.setProperty("resourceIntf.default.min.number.free.agents", "1");
		//running main engine
		ccdpEngine= new CcdpMainApplication(null);

		assertNotNull("The application should not be null", ccdpEngine);
		//waiting for the engine to get settle and launch vms if need
		double pauseTime = ccdpEngine.getTimerDelay()/1000 + addSecond;
		CcdpUtils.pause(pauseTime);

		Map<String, List<CcdpVMResource>> resources = ccdpEngine.getResources();
		int numberOfVM = resources.get("DEFAULT").size();
		assertEquals(1,numberOfVM);

		List<String> cmd = new ArrayList<String>();
		cmd.add( "/data/ccdp/ccdp-engine/python/ccdp_mod_test.py");
		cmd.add("-a");
		cmd.add("testRandomTime");
		cmd.add( "-p");
		cmd.add("min=25,max=35");
		String taskId = sendTaskRequest("DEFAULT","random time","Test1",
				testChannel, 0.0, cmd,null, this.mainChannel);
		//wait for the task to be launched and the new vm if need to be started
		pauseTime = ccdpEngine.getTimerPeriod()/1000 + addSecond;
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
		CcdpUtils.pause( pauseTime );

		//after the task is completed there should not be any request left
		assertEquals(0, ccdpEngine.getRequests().size());

		waitUntilVMisRunning("DEFAULT");
	}
	/**
	 * Test That the engine runs multiple task in a single vm when needed
	 * and that it only running one task on a vm when needed. 
	 */
	@Ignore
	@Test(timeout=180000)//test fails if it takes longer than 3 min
	public void RunningMultipleTaskRequest() {
		CcdpUtils.setProperty("task.allocator.intf.classname","com.axios.ccdp.controllers.NumberTasksControllerImpl");
		CcdpUtils.setProperty("taskContrIntf.allocate.no.more.than", "5");
		CcdpUtils.setProperty("resourceIntf.default.min.number.free.agents", "1");
		CcdpUtils.setProperty("resourceIntf.nifi.min.number.free.agents", "1");

		ccdpEngine= new CcdpMainApplication(null);
		//waiting for the onEvent function to be called 
		double pauseTime = ccdpEngine.getTimerDelay()/1000 + addSecond;
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
		cmd.add("min=25,max=35");
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
		pauseTime = ccdpEngine.getTimerPeriod()/1000 + addSecond;
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
		ConcurrentLinkedQueue<CcdpThreadRequest> request = ccdpEngine.getRequests();
		while(request.size() > 0) {
			CcdpUtils.pause(1);
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
	 *  
	 *  Test sending multiple task for a single session session
	 */

	@Test (timeout=180000)//test fails if it takes longer than 3 min
	public void TaskAllocationBasedOn_NumberTasksControllerImpl_1() {
		CcdpUtils.setProperty("task.allocator.intf.classname","com.axios.ccdp.controllers.NumberTasksControllerImpl");
		CcdpUtils.setProperty("taskContrIntf.allocate.no.more.than", "2");
		ccdpEngine= new CcdpMainApplication(null);
		//waiting for the onEvent function to be called 
		double pauseTime = ccdpEngine.getTimerDelay()/1000 + addSecond;
		CcdpUtils.pause(pauseTime);

		List<String> cmd = new ArrayList<String>();
		cmd.add( "/data/ccdp/ccdp-engine/python/ccdp_mod_test.py");
		cmd.add("-a");
		cmd.add("testRandomTime");
		cmd.add( "-p");
		cmd.add("min=25,max=35");
		String taskId1 = sendTaskRequest("DEFAULT","Signal Term","Test1",
				testChannel, 0.0, cmd,null, this.mainChannel);

		String taskId2 = sendTaskRequest("DEFAULT","Signal Term","Test1",
				testChannel, 0.0, cmd,null, this.mainChannel);

		String taskId3 = sendTaskRequest("DEFAULT","Signal Term","Test1",
				testChannel, 0.0, cmd,null, this.mainChannel);

		String taskId4 = sendTaskRequest("DEFAULT","Signal Term","Test1",
				testChannel, 0.0, cmd,null, this.mainChannel);

		String taskId5 = sendTaskRequest("DEFAULT","Signal Term","Test1",
				testChannel, 0.0, cmd,null, this.mainChannel);

		String taskId6 = sendTaskRequest("DEFAULT","Signal Term","Test1",
				testChannel, 0.0, cmd,null, this.mainChannel);

		//waiting for the onEvent function to be called
		pauseTime = ccdpEngine.getTimerPeriod()/1000 + addSecond;
		CcdpUtils.pause(pauseTime);

		Map<String, List<CcdpVMResource>> resources = ccdpEngine.getResources();

		assertEquals(4,resources.get("Test1").size());

		//asserts that each vm has either 2 or 0 tasks since they are 6 tasks
		//and by the all the vms are at their ma
		for (CcdpVMResource vm : resources.get("Test1")) {
			boolean taskSize = vm.getTasks().size() == 2 || vm.getTasks().size()==0; 
			assertTrue(taskSize);
		}

		//waiting until there are not request left in the queue 
		//waiting until there no request left in the queue
		ConcurrentLinkedQueue<CcdpThreadRequest> request = ccdpEngine.getRequests();
		while(request.size() > 0) {
			CcdpUtils.pause(1);
		}
		//since task are assigned by the order they arrive to the queue
		//we can check that the vm is the same for two specific task only
		assertEquals(taskMap.get(taskId1).getHostId(),taskMap.get(taskId2).getHostId());
		assertEquals(taskMap.get(taskId3).getHostId(),taskMap.get(taskId4).getHostId());
		assertEquals(taskMap.get(taskId5).getHostId(),taskMap.get(taskId6).getHostId());

		//if the above it true the host for taskId1, taskIde3, taskId5 should not be the same
		assertNotEquals(taskMap.get(taskId1).getHostId(),taskMap.get(taskId3).getHostId());
		assertNotEquals(taskMap.get(taskId1).getHostId(),taskMap.get(taskId5).getHostId());
		assertNotEquals(taskMap.get(taskId3).getHostId(),taskMap.get(taskId5).getHostId());

		assertEquals(CcdpTaskState.SUCCESSFUL, taskMap.get(taskId1).getState());
		assertEquals(CcdpTaskState.SUCCESSFUL, taskMap.get(taskId2).getState());
		assertEquals(CcdpTaskState.SUCCESSFUL, taskMap.get(taskId3).getState());
		assertEquals(CcdpTaskState.SUCCESSFUL, taskMap.get(taskId4).getState());
		assertEquals(CcdpTaskState.SUCCESSFUL, taskMap.get(taskId5).getState());
		assertEquals(CcdpTaskState.SUCCESSFUL, taskMap.get(taskId6).getState());
	}

	/**
	 * Test the task allocation based on the NumberTasksComtrollerImpl
	 * if the task's CPU not equals 100 the engine should not assign more than
	 *  taskContrIntf.allocate.no.more.than task in one vm 
	 *  
	 *  Test sending task to multiple sessions and making sure task returns successfully
	 */

	@Test (timeout=180000)//test fails if it takes longer than 3 min
	public void TaskAllocationBasedOn_NumberTasksControllerImpl_2() {
		CcdpUtils.setProperty("task.allocator.intf.classname","com.axios.ccdp.controllers.NumberTasksControllerImpl");
		CcdpUtils.setProperty("taskContrIntf.allocate.no.more.than", "2");
		ccdpEngine= new CcdpMainApplication(null);
		//waiting for the onEvent function to be called 
		double pauseTime = ccdpEngine.getTimerDelay()/1000 + addSecond;
		CcdpUtils.pause(pauseTime);

		List<String> cmd = new ArrayList<String>();
		cmd.add( "/data/ccdp/ccdp-engine/python/ccdp_mod_test.py");
		cmd.add("-a");
		cmd.add("testRandomTime");
		cmd.add( "-p");
		cmd.add("min=25,max=35");
		String taskId1 = sendTaskRequest("DEFAULT","Signal Term","Group1",
				testChannel, 0.0, cmd,null, this.mainChannel);
		CcdpUtils.pause(5);

		String taskId2 = sendTaskRequest("DEFAULT","Signal Term","Group1",
				testChannel, 0.0, cmd,null, this.mainChannel);
		CcdpUtils.pause(5);

		String taskId3 = sendTaskRequest("DEFAULT","Signal Term","Group1",
				testChannel, 0.0, cmd,null, this.mainChannel);
		CcdpUtils.pause(5);

		String taskId4 = sendTaskRequest("DEFAULT","Signal Term","Group2",
				testChannel, 0.0, cmd,null, this.mainChannel);

		CcdpUtils.pause(5);

		String taskId5 = sendTaskRequest("DEFAULT","Signal Term","Group3",
				testChannel, 0.0, cmd,null, this.mainChannel);

		//waiting for the onEvent function to be called
		pauseTime = ccdpEngine.getTimerPeriod()/1000 + addSecond;
		CcdpUtils.pause(pauseTime);

		Map<String, List<CcdpVMResource>> resources = ccdpEngine.getResources();

		assertEquals(2,resources.get("Group1").size());
		assertEquals(1,resources.get("Group2").size());
		assertEquals(1,resources.get("Group3").size());

		for (CcdpVMResource vm : resources.get("Group1")) {
			boolean taskSize = vm.getTasks().size() == 2 || vm.getTasks().size()==1; 
			assertTrue("The number of task in the vm must be 2 or 0 ", taskSize);
		}

		assertTrue(resources.get("Group2").get(0).getTasks().size() ==1);
		assertTrue(resources.get("Group3").get(0).getTasks().size() ==1);


		//waiting until there are not request left in the queue 
		//waiting until there no request left in the queue
		ConcurrentLinkedQueue<CcdpThreadRequest> request = ccdpEngine.getRequests();
		while(request.size() > 0) {
			CcdpUtils.pause(1);
		}

		//making sure the host for task in different session is not the same
		assertNotEquals(taskMap.get(taskId1).getHostId(),taskMap.get(taskId3).getHostId());
		assertNotEquals(taskMap.get(taskId1).getHostId(),taskMap.get(taskId4).getHostId());
		assertNotEquals(taskMap.get(taskId1).getHostId(),taskMap.get(taskId5).getHostId());

		assertEquals(CcdpTaskState.SUCCESSFUL, taskMap.get(taskId1).getState());
		assertEquals(CcdpTaskState.SUCCESSFUL, taskMap.get(taskId2).getState());
		assertEquals(CcdpTaskState.SUCCESSFUL, taskMap.get(taskId3).getState());
		assertEquals(CcdpTaskState.SUCCESSFUL, taskMap.get(taskId4).getState());
		assertEquals(CcdpTaskState.SUCCESSFUL, taskMap.get(taskId5).getState());

		CcdpUtils.pause(pauseTime);

		assertEquals(0,resources.get("Group1").size());
		assertEquals(0,resources.get("Group2").size());
		assertEquals(0,resources.get("Group3").size());
	}

	/**
	 * Test the task allocation based on first fit
	 * if the task has a requested CPU greater that 0 
	 * or less than 100
	 * the task will be allocated base on how much the vm has left to offer
	 * 
	 */

	@Test (timeout=300000)//test fails if it takes longer than 5 min
	public void TaskAllocationBasedOn_firstFit() {
		CcdpUtils.setProperty("task.allocator.intf.classname","com.axios.ccdp.controllers.NumberTasksControllerImpl");
		CcdpUtils.setProperty("taskContrIntf.allocate.no.more.than", "2");
		CcdpUtils.setProperty("resourceIntf.default.min.number.free.agents", "3");
		ccdpEngine= new CcdpMainApplication(null);
		//waiting for the onEvent function to be called 
		double pauseTime = ccdpEngine.getTimerDelay()/1000 + addSecond;
		CcdpUtils.pause(pauseTime);

		Map<String, List<CcdpVMResource>> resources = ccdpEngine.getResources();
		//making sure there are two VMs running based on the config properties
		assertEquals(3,resources.get("DEFAULT").size());

		List<String> cmd = new ArrayList<String>();
		cmd.add( "/data/ccdp/ccdp-engine/python/ccdp_mod_test.py");
		cmd.add("-a");
		cmd.add("testCpuUsage");
		cmd.add( "-p");
		cmd.add("15");

		String taskId1 = sendTaskRequest("DEFAULT","Test CPU 1","DEFAULT",
				testChannel, 10.0, cmd,null, this.mainChannel);

		String taskId2 = sendTaskRequest("DEFAULT","Test CPU 2","DEFAULT",
				testChannel, 40.0, cmd,null, this.mainChannel);

		String taskId3 = sendTaskRequest("DEFAULT","Test CPU 3","DEFAULT",
				testChannel, 70.0, cmd,null, this.mainChannel);

		String taskId4 = sendTaskRequest("DEFAULT","Test CPU 4","DEFAULT",
				testChannel, 20.0, cmd,null, this.mainChannel);

		String taskId5 = sendTaskRequest("DEFAULT","Test CPU 5","DEFAULT",
				testChannel, 90.0, cmd,null, this.mainChannel);

		String taskId6 = sendTaskRequest("DEFAULT","Test CPU 6","DEFAULT",
				testChannel, 100.0, cmd,null, this.mainChannel);

		//waiting until there no request left in the queue
		ConcurrentLinkedQueue<CcdpThreadRequest> request = ccdpEngine.getRequests();
		while(request.size() > 0) {
			CcdpUtils.pause(1);
		}

		//taskId6 requieres cpu = 100 so there should not be any other task sharing the same host
		assertNotEquals(taskMap.get(taskId6).getHostId(),taskMap.get(taskId1).getHostId());
		assertNotEquals(taskMap.get(taskId6).getHostId(),taskMap.get(taskId2).getHostId());
		assertNotEquals(taskMap.get(taskId6).getHostId(),taskMap.get(taskId3).getHostId());
		assertNotEquals(taskMap.get(taskId6).getHostId(),taskMap.get(taskId4).getHostId());
		assertNotEquals(taskMap.get(taskId6).getHostId(),taskMap.get(taskId5).getHostId());
		assertEquals(CcdpTaskState.SUCCESSFUL, taskMap.get(taskId1).getState());
		assertEquals(CcdpTaskState.SUCCESSFUL, taskMap.get(taskId2).getState());
		assertEquals(CcdpTaskState.SUCCESSFUL, taskMap.get(taskId3).getState());
		assertEquals(CcdpTaskState.SUCCESSFUL, taskMap.get(taskId4).getState());
		assertEquals(CcdpTaskState.SUCCESSFUL, taskMap.get(taskId5).getState());
		assertEquals(CcdpTaskState.SUCCESSFUL, taskMap.get(taskId6).getState());

		CcdpUtils.pause(5);

		//send task again but at different times
		cmd.remove(4);
		cmd.add("60");
		taskId1 = sendTaskRequest("DEFAULT","Test CPU 1","DEFAULT",
				testChannel, 10.0, cmd,null, this.mainChannel);

		CcdpUtils.pause(8);

		taskId2 = sendTaskRequest("DEFAULT","Test CPU 2","DEFAULT",
				testChannel, 40.0, cmd,null, this.mainChannel);

		CcdpUtils.pause(8);

		taskId3 = sendTaskRequest("DEFAULT","Test CPU 3","DEFAULT",
				testChannel, 70.0, cmd,null, this.mainChannel);

		CcdpUtils.pause(8);

		taskId4 = sendTaskRequest("DEFAULT","Test CPU 4","DEFAULT",
				testChannel, 20.0, cmd,null, this.mainChannel);

		CcdpUtils.pause(8);

		taskId5 = sendTaskRequest("DEFAULT","Test CPU 5","DEFAULT",
				testChannel, 90.0, cmd,null, this.mainChannel);

		CcdpUtils.pause(8);

		taskId6 = sendTaskRequest("DEFAULT","Test CPU 6","DEFAULT",
				testChannel, 100.0, cmd,null, this.mainChannel);

		//waiting until there no request left in the queue
		while(request.size() > 0) {
			CcdpUtils.pause(1);

		}

		assertNotEquals(taskMap.get(taskId1).getHostId(),taskMap.get(taskId2).getHostId());
		assertNotEquals(taskMap.get(taskId2).getHostId(),taskMap.get(taskId3).getHostId());
		assertNotEquals(taskMap.get(taskId3).getHostId(),taskMap.get(taskId4).getHostId());
		//task 4 and 5 could be on the same vm or not depending on how fast the vm task to be launched

		//task 6 again should have its own vm 
		assertNotEquals(taskMap.get(taskId6).getHostId(),taskMap.get(taskId1).getHostId());
		assertNotEquals(taskMap.get(taskId6).getHostId(),taskMap.get(taskId2).getHostId());
		assertNotEquals(taskMap.get(taskId6).getHostId(),taskMap.get(taskId3).getHostId());
		assertNotEquals(taskMap.get(taskId6).getHostId(),taskMap.get(taskId4).getHostId());
		assertNotEquals(taskMap.get(taskId6).getHostId(),taskMap.get(taskId5).getHostId());
		assertEquals(CcdpTaskState.SUCCESSFUL, taskMap.get(taskId1).getState());
		assertEquals(CcdpTaskState.SUCCESSFUL, taskMap.get(taskId2).getState());
		assertEquals(CcdpTaskState.SUCCESSFUL, taskMap.get(taskId3).getState());
		assertEquals(CcdpTaskState.SUCCESSFUL, taskMap.get(taskId4).getState());
		assertEquals(CcdpTaskState.SUCCESSFUL, taskMap.get(taskId5).getState());
		assertEquals(CcdpTaskState.SUCCESSFUL, taskMap.get(taskId6).getState());
		CcdpUtils.pause(10);

	}
	/**
	 * Test the task allocation based on the AvgLoadControllerImpl
	 * Test sending one task at different time
	 * if the task's the CPU not equals 100 the engine should not assign more task to a vm
	 * if the mem usage greater or equals taskContrIntf.allocate.avg.load.mem or the 
	 * cpuLoad usage greater or equals askContrIntf.allocate.avg.load.cpu
	 * 
	 */
	@Test (timeout=300000)//test fails if it takes longer than 5 min
	public void TaskAllocationBasedOn_AvgLoadControllerImpl_1() {
		CcdpUtils.setProperty("resourceIntf.ec2.min.number.free.agents", "1");
		CcdpUtils.setProperty("task.allocator.intf.classname","com.axios.ccdp.controllers.AvgLoadControllerImpl");
		CcdpUtils.setProperty("taskContrIntf.allocate.avg.load.cpu","75");
		CcdpUtils.setProperty("taskContrIntf.allocate.avg.load.mem","15");

		ccdpEngine= new CcdpMainApplication(null);
		//waiting for the onEvent function to be called 
		double pauseTime = ccdpEngine.getTimerDelay()/1000 + addSecond;
		CcdpUtils.pause(pauseTime);

		Map<String, List<CcdpVMResource>> resources = ccdpEngine.getResources();
		//making sure there are two VMs running based on the config properties
		assertEquals(1,resources.get("EC2").size());

		waitUntilVMisRunning("EC2");

		List<String> cmd = new ArrayList<String>();
		cmd.add( "/data/ccdp/ccdp-engine/python/ccdp_mod_test.py");
		cmd.add("-a");
		cmd.add("testCpuUsage");
		cmd.add( "-p");
		cmd.add("90");
		String taskId1 = sendTaskRequest("EC2","Test CPU 1","Test1",
				testChannel, 0.0, cmd,null, this.mainChannel);

		waitForTaskStatus("RUNNING", taskId1);
		CcdpUtils.pause(20);
		
	
		String taskId2 = sendTaskRequest("EC2","Test CPU 2","Test1",
				testChannel, 0.0, cmd,null, this.mainChannel);

		waitForTaskStatus("RUNNING", taskId2);
		CcdpUtils.pause(10);

		cmd.remove(4);
		cmd.add("40");
		String taskId3 = sendTaskRequest("EC2","Test CPU 3","Test1",
				testChannel, 0.0, cmd,null, this.mainChannel);

		waitForTaskStatus("RUNNING", taskId3);
		//waiting for the onEvent function to be called
		pauseTime = ccdpEngine.getTimerPeriod()/1000 + addSecond;
		CcdpUtils.pause(pauseTime);

		//because the three tasks sent make the CPU go to 100% the AvgLoad controller
		//should have allocated each task in a different vm 
		assertTrue(resources.get("Test1").size()>=3);
		assertNotEquals(taskMap.get(taskId1).getHostId(),taskMap.get(taskId2).getHostId());
		assertNotEquals(taskMap.get(taskId1).getHostId(),taskMap.get(taskId3).getHostId());
		assertNotEquals(taskMap.get(taskId2).getHostId(),taskMap.get(taskId3).getHostId());

		//waiting until there no request left in the queue

		int request_Left = ccdpEngine.getRequests().size();
		while(request_Left > 0) {
			CcdpUtils.pause(1);
			request_Left = ccdpEngine.getRequests().size();
		}

		assertEquals(CcdpTaskState.SUCCESSFUL, taskMap.get(taskId1).getState());
		assertEquals(CcdpTaskState.SUCCESSFUL, taskMap.get(taskId2).getState());
		assertEquals(CcdpTaskState.SUCCESSFUL, taskMap.get(taskId3).getState());


		CcdpUtils.pause(pauseTime);
		assertEquals(1,resources.get("EC2").size());
	}


	/**
	 * Test the task allocation based on the AvgLoadControllerImpl
	 * Test sending multiple task consecutively when there are multiple VMs available
	 * if the task's the CPU not equals 100 the engine should not assign more task to a vm
	 * if the mem usage greater or equals taskContrIntf.allocate.avg.load.mem or the 
	 * cpuLoad usage greater or equals askContrIntf.allocate.avg.load.cpu
	 * 
	 * task could be assigned to a single VM or distributed between other VMs
	 */

	@Test (timeout=240000)//test fails if it takes longer than 4 min
	public void TaskAllocationBasedOn_AvgLoadControllerImpl_2() {
		CcdpUtils.setProperty("resourceIntf.ec2.min.number.free.agents", "3");
		CcdpUtils.setProperty("task.allocator.intf.classname","com.axios.ccdp.controllers.AvgLoadControllerImpl");
		CcdpUtils.setProperty("taskContrIntf.allocate.avg.load.cpu","75");
		CcdpUtils.setProperty("taskContrIntf.allocate.avg.load.mem","15");

		ccdpEngine= new CcdpMainApplication(null);

		//waiting for the onEvent function to be called 
		double pauseTime = ccdpEngine.getTimerDelay()/1000 + addSecond;
		CcdpUtils.pause(pauseTime);

		Map<String, List<CcdpVMResource>> resources = ccdpEngine.getResources();
		//making sure there are two VMs running based on the config properties
		assertEquals(3,resources.get("EC2").size());


		List<String> cmd = new ArrayList<String>();
		cmd.add( "/data/ccdp/ccdp-engine/python/ccdp_mod_test.py");
		cmd.add("-a");
		cmd.add("testCpuUsage");
		cmd.add( "-p");
		cmd.add("60");
		String taskId1 = sendTaskRequest("EC2","Test CPU 1","EC2",
				testChannel, 0.0, cmd,null, this.mainChannel);

		String taskId2 = sendTaskRequest("EC2","Test CPU 2","EC2",
				testChannel, 0.0, cmd,null, this.mainChannel);

		String taskId3 = sendTaskRequest("EC2","Test CPU 3","EC2",
				testChannel, 0.0, cmd,null, this.mainChannel);

		String taskId4 = sendTaskRequest("EC2","Test CPU 4","EC2",
				testChannel, 0.0, cmd,null, this.mainChannel);

		String taskId5 = sendTaskRequest("EC2","Test CPU 5","EC2",
				testChannel, 0.0, cmd,null, this.mainChannel);

		String taskId6 = sendTaskRequest("EC2","Test CPU 6","EC2",
				testChannel, 0.0, cmd,null, this.mainChannel);

		//waiting until there no request left in the queue
		int request_Left = ccdpEngine.getRequests().size();
		while(request_Left > 0) {
			CcdpUtils.pause(1);
			request_Left = ccdpEngine.getRequests().size();
		}
		assertEquals(CcdpTaskState.SUCCESSFUL, taskMap.get(taskId1).getState());
		assertEquals(CcdpTaskState.SUCCESSFUL, taskMap.get(taskId2).getState());
		assertEquals(CcdpTaskState.SUCCESSFUL, taskMap.get(taskId3).getState());
		assertEquals(CcdpTaskState.SUCCESSFUL, taskMap.get(taskId4).getState());
		assertEquals(CcdpTaskState.SUCCESSFUL, taskMap.get(taskId5).getState());
		assertEquals(CcdpTaskState.SUCCESSFUL, taskMap.get(taskId6).getState());

		//waiting for the onEvent function to be called
		pauseTime = ccdpEngine.getTimerPeriod()/1000 + addSecond;
		CcdpUtils.pause(pauseTime);

	}


	/**
	 * Test the task allocation based using AvgLoadController. And sending task that
	 * require full VM, task that are allocated on first fit.
	 * Tasks that requiere different session or node types
	 */

	@Test (timeout=270000)//test fails if it takes longer than 4.5 min
	public void MoreTaskAllocation() {
		CcdpUtils.setProperty("task.allocator.intf.classname","com.axios.ccdp.controllers.AvgLoadControllerImpl");
		CcdpUtils.setProperty("resourceIntf.default.min.number.free.agents", "1");
		CcdpUtils.setProperty("taskContrIntf.deallocate.avg.load.time","1");//changing deallocation time for 1 min

		ccdpEngine= new CcdpMainApplication(null);
		//waiting for the onEvent function to be called 
		double pauseTime = ccdpEngine.getTimerDelay()/1000 + addSecond;
		CcdpUtils.pause(pauseTime);

		Map<String, List<CcdpVMResource>> resources = ccdpEngine.getResources();
		//making sure there are two VMs running based on the config properties
		assertEquals(1,resources.get("DEFAULT").size());

		waitUntilVMisRunning("DEFAULT");

		List<String> cmd1 = new ArrayList<String>();
		cmd1.add( "/data/ccdp/ccdp-engine/python/ccdp_mod_test.py");
		cmd1.add("-a");
		cmd1.add("testCpuUsage");
		cmd1.add( "-p");
		cmd1.add("40");
		List<String> cmd2 = new ArrayList<String>();
		cmd2.add( "/data/ccdp/ccdp-engine/python/ccdp_mod_test.py");
		cmd2.add("-a");
		cmd2.add("testRandomTime");
		cmd2.add( "-p");
		cmd2.add("min=25,max=35");

		String taskId1 = sendTaskRequest("DEFAULT","Random Test 1","DEFAULT",
				testChannel, 0.0, cmd1,null, this.mainChannel);
		waitForTaskStatus("RUNNING",taskId1);
		CcdpUtils.pause(12);
		
		String taskId2 = sendTaskRequest("DEFAULT","Random Test 2","DEFAULT",
				testChannel, 0.0, cmd1,null, this.mainChannel);
		CcdpUtils.pause(3);

		String taskId3 = sendTaskRequest("EC2","Random Test 3","Test1",
				testChannel, 70.0, cmd2,null, this.mainChannel);
		CcdpUtils.pause(3);

		String taskId4 = sendTaskRequest("EC2","Random Test 4","Test2",
				testChannel, 20.0, cmd2,null, this.mainChannel);
		CcdpUtils.pause(3);

		String taskId5 = sendTaskRequest("NIFI","Random Test 5","Test3",
				testChannel, 0.0, cmd2,null, this.mainChannel);
		CcdpUtils.pause(3);

		String taskId6 = sendTaskRequest("EC2","Random Test 6","Test3",
				testChannel, 100.0, cmd1,null, this.mainChannel);
		CcdpUtils.pause(3);

		//make sure there are only vm running for the session required
		assertEquals(2,resources.get("DEFAULT").size());
		assertEquals(1,resources.get("Test1").size());
		assertEquals(1,resources.get("Test2").size());
		assertEquals(2,resources.get("Test3").size());
		assertEquals(0,resources.get("NIFI").size());
		assertEquals(0,resources.get("EC2").size());

		//make sure that in session Test3 here is one VM for NiFin and one for EC2
		assertNotEquals(resources.get("Test3").get(0).getNodeTypeAsString(),resources.get("Test3").get(1).getNodeTypeAsString() );

		//the vm for EC2 in session test3 
		//have to be single tasked since the task require CPU = 100
		for(CcdpVMResource vm : resources.get("Test3")) {
			if(vm.getNodeTypeAsString().equals("EC2"))
				assertTrue(vm.isSingleTasked());
		}


		//waiting until there no request left in the queue
		int request_Left = ccdpEngine.getRequests().size();
		while(request_Left > 0) {
			CcdpUtils.pause(1);
			request_Left = ccdpEngine.getRequests().size();
		}
		assertNotEquals(taskMap.get(taskId1).getHostId(),taskMap.get(taskId2).getHostId());
		assertNotEquals(taskMap.get(taskId2).getHostId(),taskMap.get(taskId3).getHostId());
		assertNotEquals(taskMap.get(taskId3).getHostId(),taskMap.get(taskId4).getHostId());
		assertNotEquals(taskMap.get(taskId4).getHostId(),taskMap.get(taskId5).getHostId());
		assertNotEquals(taskMap.get(taskId6).getHostId(),taskMap.get(taskId5).getHostId());

		assertEquals(CcdpTaskState.SUCCESSFUL, taskMap.get(taskId1).getState());
		assertEquals(CcdpTaskState.SUCCESSFUL, taskMap.get(taskId2).getState());
		assertEquals(CcdpTaskState.SUCCESSFUL, taskMap.get(taskId3).getState());
		assertEquals(CcdpTaskState.SUCCESSFUL, taskMap.get(taskId4).getState());
		assertEquals(CcdpTaskState.SUCCESSFUL, taskMap.get(taskId5).getState());
		assertEquals(CcdpTaskState.SUCCESSFUL, taskMap.get(taskId6).getState());

		waitUntilVMisRunning("DEFAULT");
		waitUntilVMisRunning("Test1");
		waitUntilVMisRunning("Test2");
		waitUntilVMisRunning("Test3");

		//waiting for the onEvent function to be called
		pauseTime = ccdpEngine.getTimerPeriod()/1000  + addSecond;
		CcdpUtils.pause(pauseTime);

		assertEquals(1,resources.get("DEFAULT").size());
		//because using the average load there will always be VM running for this session
		//unless we wait for the the deallocation period since the last assignment for each VM
		assertTrue(resources.get("Test1").size()<=2);
		assertTrue(resources.get("Test2").size()<=2);
		assertTrue(resources.get("Test3").size()<=2);
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
	 * Helper function used to wait until the VM(s) in a specific session
	 * are in a running state 
	 * 
	 * @param sid session id of the vm we are waiting for updates
	 */
	private void waitUntilVMisRunning(String sid) {
		boolean stateUpdated = false;

		while(!stateUpdated) {
			if(ccdpEngine.getResources().get(sid).size()==0)
				break;
			for(CcdpVMResource vm : ccdpEngine.getResources().get(sid)) {
				if(ResourceStatus.RUNNING.equals(vm.getStatus())) {
					stateUpdated = true;
					break;
				}
				else {
					stateUpdated = false;
					break;
				}
			}
			CcdpUtils.pause(2);
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
				break;
			}
			CcdpUtils.pause(2);
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
