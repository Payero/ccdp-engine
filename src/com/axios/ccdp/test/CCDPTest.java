package com.axios.ccdp.test;


import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.math.RoundingMode;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.URL;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.apache.log4j.Logger;

import com.axios.ccdp.cloud.docker.DockerResourceMonitorImpl;
import com.axios.ccdp.connections.intfs.SystemResourceMonitorIntf;
import com.axios.ccdp.factory.CcdpObjectFactory;
import com.axios.ccdp.fmwk.CcdpMainApplication;
import com.axios.ccdp.resources.CcdpVMResource;
import com.axios.ccdp.resources.CcdpVMResource.ResourceStatus;
import com.axios.ccdp.utils.CcdpUtils;
import com.axios.ccdp.utils.CcdpUtils.CcdpNodeType;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.spotify.docker.client.DefaultDockerClient;
import com.spotify.docker.client.DockerClient;
import com.spotify.docker.client.messages.Container;
import com.spotify.docker.client.messages.ContainerConfig;
import com.spotify.docker.client.messages.ContainerCreation;
import com.spotify.docker.client.messages.ContainerInfo;
import com.spotify.docker.client.messages.ContainerStats;
import com.spotify.docker.client.messages.CpuStats;
import com.spotify.docker.client.messages.CpuStats.CpuUsage;
import com.spotify.docker.client.messages.HostConfig;
import com.spotify.docker.client.messages.HostConfig.Bind;
import com.spotify.docker.client.messages.MemoryStats;
import com.spotify.docker.client.messages.MemoryStats.Stats;
import com.spotify.docker.client.messages.Volume;



public class CCDPTest 
{
  /**
   * Generates debug print statements based on the verbosity level.
   */
  private Logger logger = Logger.getLogger(CCDPTest.class.getName());
  
  private CcdpVMResource vmInfo;
  private SystemResourceMonitorIntf monitor;
  
  public CCDPTest()
  {
    this.logger.debug("Running CCDP Test");
    try
    {
      this.runTest();
    }
    catch( Exception e )
    {
      this.logger.error("Message: " + e.getMessage(), e);
    }
    
  }
  
  
  private void runTest() throws Exception
  {
    this.logger.debug("Running the Test");
    CcdpObjectFactory factory = CcdpObjectFactory.newInstance();
    ObjectNode res_mon_node = 
        CcdpUtils.getJsonKeysByFilter(CcdpUtils.CFG_KEY_RES_MON);
    
    this.monitor = factory.getResourceMonitorInterface(res_mon_node);
    
    String hostId = this.monitor.getUniqueHostId();
    String hostname = null;
    
    try
    {
      hostname = CcdpUtils.retrieveEC2Info("public-ipv4");
    }
    catch( Exception e )
    {
      this.logger.warn("Could not retrieve hostname from EC2");
      try
      {
        InetAddress addr = CcdpUtils.getLocalHostAddress();
        hostname = addr.getHostAddress();
      }
      catch(UnknownHostException uhe)
      {
        this.logger.warn("Could not get the IP address");
      }
    }
    CcdpNodeType type = CcdpNodeType.EC2;
    
    this.logger.info("Using Host Id: " + hostId + " and type " + type.name());
    this.vmInfo = new CcdpVMResource(hostId);
    this.vmInfo.setHostname(hostname);
    this.vmInfo.setNodeType(type);
    
//    this.me.setAssignedSession("available");
    this.vmInfo.setStatus(ResourceStatus.RUNNING);
    this.updateResourceInfo();
    
    this.vmInfo.setCPU(this.monitor.getTotalNumberCpuCores());
    this.vmInfo.setTotalMemory(this.monitor.getTotalPhysicalMemorySize());
    this.vmInfo.setDisk(this.monitor.getTotalDiskSpace());
    
    
    
    this.logger.debug("Got the Monitor");
    int max = 10;
    for(int i = 0; i < max; i++ )
    {
      this.updateResourceInfo();
      CcdpUtils.pause(2);
      this.logger.debug(this.vmInfo.toJSON().toString() );
      
    }
    CcdpUtils.pause(1);
    
    monitor.close();
    
//    List<String> envs = new ArrayList<>();
//    envs.add("DOCKER_HOST=172.17.0.1:2375");
//    
//    List<String> cmd = new ArrayList<>();
//    cmd.add("/data/ccdp/python/ccdp_mod_test.py");
//    cmd.add("-a");
//    cmd.add("testCpuUsage");
//    cmd.add("-p");
//    cmd.add("60");
//    
//    //docker run -it --net=host  --rm
//    Volume vol = Volume.builder().name("/data/ccdp").build();
//    HostConfig hostCfg = HostConfig.builder()
//        .binds(Bind.from(vol)
//                   .to("/data/ccdp")
//                   .build())
//        .build();
//    
//    ContainerConfig cfg = ContainerConfig.builder()
//        .env(envs)
//        .hostConfig(hostCfg)
//        .image("payero/centos-7:ccdp")
//        .entrypoint(cmd)
//        .build();
//    ContainerCreation cc = this.docker.createContainer(cfg);
//    this.logger.debug("Created Container " + cc.id() );
//    this.docker.startContainer(cc.id() );
//    CcdpUtils.pause(5);
//    this.logger.debug("\n\nStopping the Container\n\n");
//    this.docker.stopContainer(cc.id(), 1);
    
//    
//    List<Container> containers = docker.listContainers();
//    this.logger.debug("Got " + containers.size() + " containers");
//    String cid = null;
//    for( Container c : containers )
//    {
//      cid = c.id();
//    }
//    
//    this.logger.debug("Container ID " + cid );
//    ContainerInfo info = docker.inspectContainer(cid);
//    this.testStats(docker, cid);
//    
//    docker.close();
////    this.logger.debug("The data " + this.sendData() );
//    
//    this.docker.close();
  }  
  
  public void updateResourceInfo()
  {
    this.vmInfo.setMemLoad( this.monitor.getUsedPhysicalMemorySize() );
    this.vmInfo.setTotalMemory(this.monitor.getTotalPhysicalMemorySize());
    this.vmInfo.setFreeMemory(this.monitor.getFreePhysicalMemorySize());
    this.vmInfo.setCPU(this.monitor.getTotalNumberCpuCores());
    this.vmInfo.setCPULoad(this.monitor.getSystemCpuLoad());
    this.vmInfo.setDisk(this.monitor.getTotalDiskSpace());
    this.vmInfo.setFreeDiskSpace(this.monitor.getFreeDiskSpace());
    
  }
  
  public static void main( String[] args ) throws Exception
  {
    String cfg_file = System.getProperty("ccdp.config.file");
    CcdpUtils.loadProperties(cfg_file);
    CcdpUtils.configLogger();
    
    new CCDPTest();
  }

}



