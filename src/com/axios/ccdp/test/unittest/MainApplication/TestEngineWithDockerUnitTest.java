package com.axios.ccdp.test.unittest.MainApplication;


import org.junit.BeforeClass;

import com.axios.ccdp.utils.CcdpUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

public class TestEngineWithDockerUnitTest extends CcdpMainApplicationTests
{

  @BeforeClass
  public static void setUpBeforeClass() throws Exception
  {
    CcdpVMcontroller = "com.axios.ccdp.impl.cloud.docker.DockerVMControllerImpl";
    CcdpVMStorageController = "com.axios.ccdp.impl.cloud.docker.DockerStorageControllerImpl";
    ClassMonitorIntf = "com.axios.ccdp.impl.cloud.docker.DockerResourceMonitorImpl";
    addSecond = 15;
   
    System.out.println("Im in the dockertest");
    ObjectMapper mapper = new ObjectMapper();
    ArrayNode base_cmd = mapper.createArrayNode();
    base_cmd.add("data/ccdp/ccdp_install.py");
    base_cmd.add("-t");
    base_cmd.add("/data/ccdp");
    base_cmd.add("-D");
    base_cmd.add("-n");
    
    //setting image id and command for the vm or container
    ObjectNode def_cfg = 
        CcdpUtils.getResourceCfg(CcdpUtils.DEFAULT_RES_NAME).deepCopy();
    def_cfg.put("image-id", "payero/centos-7:ccdp");
    ArrayNode def_cmd = base_cmd.deepCopy();
    def_cmd.add("DEFAULT");
    def_cfg.set("startup-command", def_cmd);
    CcdpUtils.setResourceCfg(CcdpUtils.DEFAULT_RES_NAME, def_cfg);
    
    ObjectNode ec2_cfg = CcdpUtils.getResourceCfg("EC2").deepCopy();
    ec2_cfg.put("image-id", "payero/centos-7:ccdp");
    ArrayNode ec2_cmd = base_cmd.deepCopy();
    def_cmd.add("EC2");
    ec2_cfg.set("startup-command", ec2_cmd);
    CcdpUtils.setResourceCfg("EC2", ec2_cfg);
    
    ObjectNode nifi_cfg = CcdpUtils.getResourceCfg("NIFI").deepCopy();
    nifi_cfg.put("image-id", "payero/centos-7:ccdp");
    ArrayNode nifi_cmd = base_cmd.deepCopy();
    def_cmd.add("NIFI");
    nifi_cfg.set("startup-command", nifi_cmd);
    CcdpUtils.setResourceCfg("NIFI", nifi_cfg);
    
  }

}
