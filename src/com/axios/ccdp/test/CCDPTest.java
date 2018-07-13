package com.axios.ccdp.test;

import java.io.FileInputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.StringTokenizer;

import org.apache.log4j.Logger;

import com.axios.ccdp.utils.CcdpUtils;
import com.spotify.docker.client.DefaultDockerClient;
import com.spotify.docker.client.DockerClient;
import com.spotify.docker.client.DockerClient.ListContainersParam;
import com.spotify.docker.client.messages.Container;
import com.spotify.docker.client.messages.ContainerConfig;
import com.spotify.docker.client.messages.ContainerCreation;
import com.spotify.docker.client.messages.ContainerInfo;
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
    ListContainersParam params = ListContainersParam.allContainers();
    params = ListContainersParam.filter("status", "exited");
    params = ListContainersParam.filter("ancestor", "centos");
    
    List<Container> ids = docker.listContainers(params);
    for( Container c : ids )
    {
      String id = c.id();
      this.logger.debug("The Container id " + id);
//      docker.removeContainer(id);
    }
    docker.close();
    
  }
  
  public static void main( String[] args ) throws Exception
  {
    String cfg_file = System.getProperty("ccdp.config.file");
    CcdpUtils.loadProperties(cfg_file);
    CcdpUtils.configLogger();
    
    new CCDPTest();
  }

}



