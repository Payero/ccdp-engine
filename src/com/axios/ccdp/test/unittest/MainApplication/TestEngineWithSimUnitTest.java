package com.axios.ccdp.test.unittest.MainApplication;

import org.junit.BeforeClass;


public class TestEngineWithSimUnitTest extends CcdpMainApplicationTests
{

  @BeforeClass
  public static void setUpBeforeClass() throws Exception
  {
    CcdpVMcontroller = "com.axios.ccdp.cloud.sim.SimCcdpVMControllerImpl";
    CcdpVMStorageController = "com.axios.ccdp.cloud.sim.SimCcdpStorageControllerImpl";
    WAIT_TIME_LAUNCH_VM = 5;
    WAIT_TIME_SEND_TASK = 10;
  }
  
  
}
