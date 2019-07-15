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
    final ArrayNode base_cmd = mapper.createArrayNode();
    base_cmd.add("/data/ccdp/ccdp_install.py");
    base_cmd.add("-a");
    base_cmd.add("download");
    base_cmd.add("-d");
    base_cmd.add("s3://ccdp-dist/ccdp-engine12D.tgz");
    base_cmd.add("-w");
    base_cmd.add("-t");
    base_cmd.add("/data/ccdp");
    base_cmd.add("-D");
    base_cmd.add("-n");
    
    //setting image id and command for the vm or container
    ObjectNode def_cfg = 
        CcdpUtils.getResourceCfg(CcdpUtils.DEFAULT_RES_NAME).deepCopy();
    def_cfg.put("image-id", "payero/centos-7:ccdp12");
    ArrayNode def_cmd = base_cmd.deepCopy();
    def_cmd.add("DEFAULT");
    def_cfg.set("startup-command", def_cmd);
    CcdpUtils.setResourceCfg(CcdpUtils.DEFAULT_RES_NAME, def_cfg);
    
    ObjectNode ec2_cfg = CcdpUtils.getResourceCfg("EC2").deepCopy();
    ec2_cfg.put("image-id", "payero/centos-7:ccdp12");
    ArrayNode ec2_cmd = base_cmd.deepCopy();
    ec2_cmd.add("EC2");
    ec2_cfg.set("startup-command", ec2_cmd);
    CcdpUtils.setResourceCfg("EC2", ec2_cfg);
    
    ObjectNode nifi_cfg = CcdpUtils.getResourceCfg("NIFI").deepCopy();
    nifi_cfg.put("image-id", "payero/centos-7:ccdp12");
    ArrayNode nifi_cmd = base_cmd.deepCopy();
    nifi_cmd.add("NIFI");
    nifi_cfg.set("startup-command", nifi_cmd);
    CcdpUtils.setResourceCfg("NIFI", nifi_cfg);
    
    ObjectNode docker_cfg = CcdpUtils.getResourceCfg("DOCKER").deepCopy();
    docker_cfg.put("image-id", "payero/centos-7:ccdp12");
    ArrayNode docker_cmd = base_cmd.deepCopy();
    docker_cmd.add("Docker");
    docker_cfg.set("startup-command", def_cmd);
    CcdpUtils.setResourceCfg("DOCKER", docker_cfg);
  }
}
