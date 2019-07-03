package com.axios.ccdp.test;

import java.io.*;
import java.util.ArrayList;
import java.util.List;

import org.apache.log4j.Logger;

import com.axios.ccdp.resources.CcdpImageInfo;
import com.axios.ccdp.utils.CcdpUtils;
import com.spotify.docker.client.DefaultDockerClient;
import com.spotify.docker.client.DockerClient;
import com.spotify.docker.client.messages.ContainerConfig;
import com.spotify.docker.client.messages.ContainerCreation;
import com.spotify.docker.client.messages.HostConfig;
import com.spotify.docker.client.messages.Image;
import com.spotify.docker.client.shaded.com.google.common.collect.ImmutableMap;
import com.spotify.docker.client.shaded.com.google.common.collect.UnmodifiableIterator;


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
      String url = "http://172.17.0.1:2375";
      DockerClient client = new DefaultDockerClient(url);
      this.logger.debug("Connected");
      List<String> envs = new ArrayList<>();
      logger.info("Connecting to docker enging at: " + url);
      
      envs.add("DOCKER_HOST=" + url );
      envs.add("CCDP_HOME=/data/ccdp/ccdp-engine");
      envs.add("AWS_ACCESS_KEY_ID=" + System.getenv("AWS_ACCESS_KEY_ID"));
      envs.add("AWS_SECRET_ACCESS_KEY=" + System.getenv("AWS_SECRET_ACCESS_KEY"));
      
      // Parsing the command to start the docker container
      // It should look like:
      //    AWS: /data/ccdp/ccdp_install.py -a download -d s3://ccdp-settings/ccdp-engine.tgz -t /data/ccdp -D -n DOCKER
      //    FS:  /data/ccdp/ccdp_install.py -t /data/ccdp -D -n DOCKER
      //
      CcdpImageInfo imgCfg = CcdpUtils.getImageInfo("DOCKER");
      List<String> cmd = new ArrayList<>();
      cmd.add("/data/ccdp/ccdp_install.py");
      //new 
      cmd.add("-a");
      cmd.add("download");
      cmd.add("-d");
      cmd.add("s3://ccdp-dist/ccdp-engine.tgz");
      cmd.add("-w");
      cmd.add("-t");
      cmd.add("/data/ccdp");
      cmd.add("-D");
      //cmd.add("-n");
      //cmd.add("DOCKER");
//      String cmd_line = imgCfg.getStartupCommand();
//      StringTokenizer st = new StringTokenizer(cmd_line,  " ");
//      while( st.hasMoreTokens() )
//        cmd.add(st.nextToken());

      HostConfig hostCfg = HostConfig.builder()
          .networkMode("host")
          .build();
      
      ContainerConfig cfg = ContainerConfig.builder()
          .env(envs)
          .hostConfig(hostCfg)
          .image(imgCfg.getImageId())
          .entrypoint(cmd)
          .build();
      
      System.out.println(cfg.toString());
      
      ContainerCreation cc = client.createContainer(cfg);
      String cid = cc.id();
      // Translating from Container id to a hostId
      String hostId = cid.substring(0,  12);
      logger.debug("Created Container " + hostId );
      
      client.startContainer( cc.id() );
      client.close();
      
      
    }
    
    public void imageInfo(DockerClient docker) throws Exception
    {
      this.logger.debug("Testing the Docker Client");
      List<Image> quxImages =  docker.listImages();
      for( Image img : quxImages )
      {
        try
        {
          this.logger.debug("Getting Image " + img.toString());
          ImmutableMap<String, String> map = img.labels();
          UnmodifiableIterator<String> keys = map.keySet().iterator();
          while( keys.hasNext() )
          {
            String key = keys.next();
            String val = map.get(key);
            this.logger.debug("Label[" + key + "] = " + val);
          }
        }
        catch(Exception e)
        {
          this.logger.info("Got an exception while working with image");
          continue;
        }
        
      }
      
      
    }

  public static void main( String[] args ) throws Exception
  {
    String cfg_file = System.getProperty("ccdp.config.file");
    
    //System.out.println(cfg_file); ///projects/users/srbenne/workspace/engine/config/ccdp-config.json AS EXPECTED
    
    // Uses the cfg file to configure all CcdpUtils for use in the next service
    CcdpUtils.loadProperties(cfg_file);
    CcdpUtils.configLogger();
    
    new CCDPTest();
  }

}



