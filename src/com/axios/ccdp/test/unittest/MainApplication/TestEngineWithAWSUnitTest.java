package com.axios.ccdp.test.unittest.MainApplication;

import org.junit.BeforeClass;

public class TestEngineWithAWSUnitTest extends CcdpMainApplicationTests
{
  
  @BeforeClass
  public static void setUpBeforeClass() throws Exception
  {
    CcdpVMcontroller = "com.axios.ccdp.cloud.aws.AWSCcdpVMControllerImpl";
    CcdpVMStorageController = "com.axios.ccdp.cloud.aws.AWSCcdpStorageControllerImpl";
    WAIT_TIME_LAUNCH_VM = 80;
    WAIT_TIME_SEND_TASK = 30;
  }

}
