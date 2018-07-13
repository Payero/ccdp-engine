package com.axios.ccdp.test.unittest.MainApplication;

import org.junit.BeforeClass;

import com.axios.ccdp.utils.CcdpUtils;


public class TestEngineWithSimUnitTest extends CcdpMainApplicationTests
{

  @BeforeClass
  public static void setUpBeforeClass() throws Exception
  {
    CcdpVMcontroller = "com.axios.ccdp.cloud.sim.SimCcdpVMControllerImpl";
    CcdpVMStorageController = "com.axios.ccdp.cloud.sim.SimCcdpStorageControllerImpl";
    CcdpUtils.setProperty("cpu.increment.by", "60");
    CcdpUtils.setProperty("mem.increment.by", "512");
  }
  
  
}
