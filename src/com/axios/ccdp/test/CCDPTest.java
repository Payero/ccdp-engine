package com.axios.ccdp.test;

import java.io.FileInputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.StringTokenizer;

import org.apache.log4j.Logger;

import com.axios.ccdp.utils.CcdpUtils;
import com.spotify.docker.client.DefaultDockerClient;
import com.spotify.docker.client.DockerClient;
import com.spotify.docker.client.messages.ContainerConfig;
import com.spotify.docker.client.messages.ContainerCreation;
import com.spotify.docker.client.messages.HostConfig;


public class CCDPTest 
{
  /**
   * Generates debug print statements based on the verbosity level.
   */
  private Logger logger = Logger.getLogger(CCDPTest.class.getName());
  
  
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
    String url = CcdpUtils.getConfigValue("res.mon.intf.docker.url");
    this.logger.debug("The URL " + url);
    DockerClient docker = new DefaultDockerClient(url);
    
    List<String> envs = new ArrayList<>();
    envs.add("DOCKER_HOST=" + url );
    envs.add("CCDP_HOME=/data/ccdp/ccdp-engine");
    
    String cmdLine = "/data/ccdp/ccdp_install.py -t /data/ccdp -D -n DOCKER";
    cmdLine = "watch -n 5 ls";
    List<String> cmd = new ArrayList<>();
    StringTokenizer st = new StringTokenizer(cmdLine,  " ");
    while( st.hasMoreTokens() )
      cmd.add(st.nextToken());
    
    HostConfig hostCfg = HostConfig.builder()
        .networkMode("host")
        .build();
    
    ContainerConfig cfg = ContainerConfig.builder()
        .env(envs)
        .hostConfig(hostCfg)
        .image("payero/centos-7:ccdp")
//        .entrypoint(cmd)
        .cmd(cmd)
        .build();
    ContainerCreation cc = docker.createContainer(cfg);
    String cid = cc.id();
    // Translating from Container id to a hostId
    String hostId = cid.substring(0,  12);
    this.logger.debug("Container ID " + hostId);
    String filename = CcdpUtils.getConfigValue("resourceIntf.dist.file");
    this.logger.debug("Using File " + filename);
    FileInputStream fis = new FileInputStream(filename);
    //docker.copyToContainer(fis, cid, "/data/ccdp");
    docker.startContainer( cid );
    
  }
  
  public static void main( String[] args ) throws Exception
  {
    String cfg_file = System.getProperty("ccdp.config.file");
    CcdpUtils.loadProperties(cfg_file);
    CcdpUtils.configLogger();
    
    new CCDPTest();
  }

}



