package com.axios.ccdp.test;


import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
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
import com.spotify.docker.client.DockerClient.ListContainersParam;
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
import com.spotify.docker.client.messages.NetworkConfig;
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
    String url = DockerResourceMonitorImpl.DEFAULT_DOCKER_HOST;
    DockerClient docker = new DefaultDockerClient(url);
    docker.stopContainer("061918e23a25", 1);

    
//    List<String> cmd_args = new ArrayList<>();
//    
//    String cmd_line = "/data/ccdp/ccdp_install.py -a download -t /data/ccdp -D -d /data/ccdp/dist.tgz";
//    String[] items = cmd_line.split(" ");
//    String dist_file = null;
//    for( int i = 0; i < items.length; i++ )
//    {
//      String arg = items[i].trim();
//      if( arg.equals("-d") && i < items.length - 1)
//      {
//        dist_file = items[i + 1];
//        i += 1;
//      }
//      else
//      {
//        cmd_args.add(arg);
//      }
//    }
//    this.logger.debug("The Distribution file " + dist_file);
//    this.logger.debug("The Args " + cmd_args.toString() );
//    boolean test = true;
//    if( true )
//      return;
//    
//    String url = DockerResourceMonitorImpl.DEFAULT_DOCKER_HOST;
//    DockerClient docker = new DefaultDockerClient(url);
//    
//    List<String> envs = new ArrayList<>();
//    envs.add("DOCKER_HOST=172.17.0.1:2375");
//    
//    
//    List<String> cmd = new ArrayList<>();
//    boolean is_aws = false;
//    if( is_aws )
//    {
//      envs.add("AWS_DEFAULT_REGION=us-east-1");
//      envs.add("AWS_SECRET_ACCESS_KEY=sP4V52RAc0zdq/FAY4yqbJPeQFahSyRHantOSjDf");
//      envs.add("AWS_ACCESS_KEY_ID=AKIAILDTHAKOE7G3SFGA");
//      
//      cmd.add("/data/ccdp/ccdp_install.py"); 
//      cmd.add("-a");
//      cmd.add("download");
//      cmd.add("-d");
//      cmd.add("s3://ccdp-settings/ccdp-engine.tgz");
//      cmd.add("-t");
//      cmd.add("/data/ccdp");
//      cmd.add("-D");
//      cmd.add("-s");
//      cmd.add("NIFI");
//      
//    }
//    else
//    {
//      cmd.add("/data/ccdp/ccdp_install.py"); 
//      cmd.add("-t");
//      cmd.add("/data/ccdp");
//      cmd.add("-D");
//    }
//    
//    //docker run -it --net=host  --rm
////    Volume vol = Volume.builder().name("/data/ccdp").build();
//    HostConfig hostCfg = HostConfig.builder()
//        .networkMode("host")
//        .build();
////        .binds(Bind.from(vol)
////                   .to("/data/ccdp")
////                   .build())
////        .build();
//    
//    
//    ContainerConfig cfg = ContainerConfig.builder()
//        .env(envs)
//        
//        .hostConfig(hostCfg)
//        .image("payero/centos-7:ccdp")
//        .entrypoint(cmd)
//        .build();
//    ContainerCreation cc = docker.createContainer(cfg);
//    this.logger.debug("Created Container " + cc.id() );
//    if( !is_aws )
//    {
//      FileInputStream fis = new FileInputStream("/home/oeg/workspace/ccdp-engine/dist/ccdp-engine.tgz");
//      docker.copyToContainer(fis, cc.id(), "/data/ccdp/");
//    }
//    
//    CcdpUtils.pause(5);
//    this.logger.debug("Starting Container");
//    docker.startContainer(cc.id() );
//    this.logger.debug("Done starting container");
//    docker.close();
    
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

  
  public static void main( String[] args ) throws Exception
  {
    String cfg_file = System.getProperty("ccdp.config.file");
    CcdpUtils.loadProperties(cfg_file);
    CcdpUtils.configLogger();
    
    new CCDPTest();
  }

}



