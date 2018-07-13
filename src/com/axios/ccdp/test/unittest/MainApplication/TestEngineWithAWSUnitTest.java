package com.axios.ccdp.test.unittest.MainApplication;

import org.junit.BeforeClass;

public class TestEngineWithAWSUnitTest extends CcdpMainApplicationTests
{
  
  @BeforeClass
  public static void setUpBeforeClass() throws Exception
  {
    CcdpVMcontroller = "com.axios.ccdp.cloud.aws.AWSCcdpVMControllerImpl";
    CcdpVMStorageController = "com.axios.ccdp.cloud.aws.AWSCcdpStorageControllerImpl";
   
   
  }

}
