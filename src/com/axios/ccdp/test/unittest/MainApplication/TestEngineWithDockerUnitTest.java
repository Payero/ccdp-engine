package com.axios.ccdp.test.unittest.MainApplication;

import static org.junit.Assert.*;

import org.junit.BeforeClass;
import org.junit.Test;

import com.axios.ccdp.utils.CcdpUtils;

public class TestEngineWithDockerUnitTest extends CcdpMainApplicationTests
{

  @BeforeClass
  public static void setUpBeforeClass() throws Exception
  {
    CcdpVMcontroller = "com.axios.ccdp.cloud.docker.DockerVMControllerImpl";
    CcdpVMStorageController = "com.axios.ccdp.cloud.docker.DockerStorageControllerImpl";
    ClassMonitorIntf = "com.axios.ccdp.cloud.docker.DockerResourceMonitorImpl";
    addSecond = 15;
   
    System.out.println("Im in the dockertest");
    //setting image id and command for the vm or container
    CcdpUtils.setProperty("resourceIntf.default.image.id", "payero/centos-7:ccdp");
    CcdpUtils.setProperty("resourceIntf.default.startup.command", "/data/ccdp/ccdp_install.py -t /data/ccdp -D -n DEFAULT");
    CcdpUtils.setProperty("resourceIntf.ec2.image.id", "payero/centos-7:ccdp");
    CcdpUtils.setProperty("resourceIntf.ec2.startup.command", "/data/ccdp/ccdp_install.py -t /data/ccdp -D -n EC2");
    CcdpUtils.setProperty("resourceIntf.nifi.image.id", "payero/centos-7:ccdp");
    CcdpUtils.setProperty("resourceIntf.nifi.startup.command", "/data/ccdp/ccdp_install.py -t /data/ccdp -D -n NIFI");
  }

}
