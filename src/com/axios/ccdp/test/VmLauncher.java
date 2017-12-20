package com.axios.ccdp.test;


import java.util.List;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import org.apache.log4j.Logger;

import com.amazonaws.ClientConfiguration;
import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.http.SdkHttpMetadata;
import com.amazonaws.regions.Region;
import com.amazonaws.regions.RegionUtils;
import com.amazonaws.services.ec2.AmazonEC2;
import com.amazonaws.services.ec2.AmazonEC2Client;
import com.amazonaws.services.ec2.model.CreateTagsRequest;
import com.amazonaws.services.ec2.model.IamInstanceProfileSpecification;
import com.amazonaws.services.ec2.model.Instance;
import com.amazonaws.services.ec2.model.Reservation;
import com.amazonaws.services.ec2.model.RunInstancesRequest;
import com.amazonaws.services.ec2.model.RunInstancesResult;
import com.amazonaws.services.ec2.model.Tag;
import com.amazonaws.util.Base64;
import com.axios.ccdp.cloud.aws.AWSCcdpVMControllerImpl;
import com.axios.ccdp.resources.CcdpImageInfo;
import com.axios.ccdp.utils.CcdpUtils;
import com.axios.ccdp.utils.CcdpUtils.CcdpNodeType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

public class VmLauncher 
{
  /**
   * Generates all the JSON Objects
   */
  private ObjectMapper mapper = new ObjectMapper();
  
  /**
   * Generates debug print statements based on the verbosity level.
   */
  private Logger logger = Logger.getLogger(VmLauncher.class.getName());
  
  public VmLauncher(String filename)
  {
    this.logger.debug("Using File " + filename);
    this.mapper.enable(SerializationFeature.INDENT_OUTPUT);
    
    try
    {
      CcdpImageInfo image = new CcdpImageInfo();
      
      File file = new File(filename);
      if( file != null && file.isFile() )
      {
        byte[] data = Files.readAllBytes(Paths.get( file.getAbsolutePath()));
        JsonNode node = this.mapper.readTree( data );
        
        image.setImageId(node.get("image-id").asText());
        image.setSecGrp(node.get("security-group").asText());
        image.setSubnet(node.get("subnet").asText());
        
        if( node.has("key-file") )
        {
          String txt = node.get("key-file").asText();
          if( !txt.equals("null") && txt.length() != 0 )
            image.setKeyFile(txt);
        }
        if( node.has("region") )
        {
          String txt = node.get("region").asText();
          if( !txt.equals("null") && txt.length() != 0 )
            image.setRegion(txt);
        }
        
        if( node.has("instance-type") )
        {
          String txt = node.get("instance-type").asText();
          if( !txt.equals("null") && txt.length() != 0 )
            image.setInstanceType(txt);
        }
        if( node.has("role") )
        {
          String txt = node.get("role").asText();
          if( !txt.equals("null") && txt.length() != 0 )
            image.setRoleName(txt);
        }
        if( node.has("proxy-url") )
        {
          String txt = node.get("proxy-url").asText();
          if( !txt.equals("null") && txt.length() != 0 )
            image.setProxyUrl(txt);
        }
        if( node.has("proxy-port") )
        {
          int txt = node.get("proxy-port").asInt();
          if( txt > 0 )
            image.setProxyPort(txt);
        }
        if( node.has("user-data") )
        {
          String txt = node.get("user-data").asText();
          if( !txt.equals("null") && txt.length() != 0 )
            image.setStartupCommand(txt);
        }
        if( node.has("session-id") )
        {
          String txt = node.get("session-id").asText();
          if( !txt.equals("null") && txt.length() != 0 )
            image.setSessionId(txt);
        }
        
        if( node.has("tags") )
        {
          Map<String, String> map = new HashMap<>();
          JsonNode tags = node.get("tags");
          Iterator<String> keys = tags.fieldNames();
          while( keys.hasNext() )
          {
            String key = keys.next();
            map.put(key, tags.get(key).asText());
          }
          image.setTags(map);
        }
      }
      else
      {
        image.setImageId("ami-f83a1d83");
        image.setInstanceType("t2.micro");
        image.setSecGrp("sg-54410d2f");
        image.setSubnet("subnet-d7008b8f");
        image.setKeyFile("aws_serv_server_key");
        image.setRoleName("");
        image.setStartupCommand("/data/ccdp/ccdp_install.py -a download -d s3://ccdp-settings/ccdp-engine.tgz -w -t /data/ccdp");
        image.setSessionId("Service-Node");
        
        Map<String, String> tags = new HashMap<>();
        tags.put("session-id", "");
        tags.put( "Name", "Host-Agent");
        image.setTags(tags);
        
      }
      
      this.launchVM(image);
      
    }
    catch( Exception e )
    {
      this.logger.error("Message: " + e.getMessage(), e);
    }
    
  }
  
  
  private void launchVM(CcdpImageInfo imgCfg) throws Exception
  {
    this.logger.debug("Launching a new VM using " + imgCfg.toPrettyPrint() );
    String imageId = imgCfg.getImageId();
    String secGrp = imgCfg.getSecGrp();
    String subNet = imgCfg.getSubnet();
    String keyFile = imgCfg.getKeyFile();
    
    String instType = "t2.micro";
    
    String field = imgCfg.getInstanceType();
    
    if( field != null )
    {
      this.logger.debug("Adding Instance Type " + field);
      instType = field;
    }
    
    String user_data = "";
    field = imgCfg.getStartupCommand();
    if( field != null )
    {
      user_data = "#!/bin/bash\n\n" + field;
      this.logger.debug("Adding User Data " + field);
    }
    
    Map<String, String> tags = imgCfg.getTags();
    
    RunInstancesRequest request = new RunInstancesRequest(imageId, 1, 1);
    
    request.withInstanceType(instType)
           .withUserData(new String( Base64.encode(user_data.getBytes()) ))
           .withSecurityGroupIds(secGrp)
           .withSubnetId(subNet)
           .withKeyName(keyFile);
    
    String role = imgCfg.getRoleName();
    if( role != null )
    {
      this.logger.debug("Adding Role " + role);
      IamInstanceProfileSpecification iips = 
          new IamInstanceProfileSpecification();
      iips.setName(role);
      request.setIamInstanceProfile(iips);
    }
    AWSCredentials credentials = AWSCcdpVMControllerImpl.getAWSCredentials();
    
    ClientConfiguration cc = new ClientConfiguration();
    
    String url = imgCfg.getProxyUrl();
    if( url != null )
    {
      this.logger.debug("Adding a Proxy " + url);
      cc.setProxyHost(url);
    }
    
    int port = imgCfg.getProxyPort();
    if( port > 0 )
    {
      this.logger.debug("Adding a Proxy Port " + port);
      cc.setProxyPort(port);
    }
    
    AmazonEC2 ec2 = null;
    if( credentials != null )
    {
      ec2 = new AmazonEC2Client(credentials, cc);
    }
    else
    {
      ec2 = new AmazonEC2Client(cc);
    }
    
    String region = imgCfg.getRegion();
    if( region != null )
    {
      this.logger.debug("Setting Region " + region);
      Region reg = RegionUtils.getRegion(region);
      ec2.setRegion(reg);
    }
    
    RunInstancesResult result = ec2.runInstances(request);
    SdkHttpMetadata shm = result.getSdkHttpMetadata();
    int code = shm.getHttpStatusCode();
    this.logger.debug("The Request Status Code: " + code);
    
    if( code == 200 )
    {
      this.logger.info("Request sent successfully");
      
      Reservation reservation = result.getReservation();
      Iterator<Instance> instances = reservation.getInstances().iterator();
      while( instances.hasNext() )
      {
        Instance inst = instances.next();
        String instId = inst.getInstanceId();
        String pubIp = inst.getPublicIpAddress();
        String privIp = inst.getPrivateIpAddress();
        
        String txt = String.format("Instance %s has a Public IP %s and Private IP %s", instId, pubIp, privIp);
        this.logger.info(txt);
        
        CreateTagsRequest tagsReq = new CreateTagsRequest();
        List<Tag> new_tags = new ArrayList<>();
        new_tags.add(new Tag("instance-id", instId));
        Iterator<String> keys = tags.keySet().iterator();
        while( keys.hasNext() )
        {
          String key = keys.next();
          String val = tags.get(key);
          this.logger.debug("Adding Tag[" + key + "] = " + val);
          new_tags.add(new Tag(key, val));
        }
        
        tagsReq.withResources(instId)
               .withTags(new_tags);
        ec2.createTags(tagsReq);
      }
    }
    else
    {
      this.logger.error("Could not create EC2 Instance, code " + code);
    }
    
  }
  
  
  public static void main( String[] args ) throws Exception
  {
    String cfg_file = System.getProperty("ccdp.config.file");
    CcdpUtils.loadProperties(cfg_file);
    CcdpUtils.configLogger();
    String fname = null;
    
    if( args.length == 1 )
      fname = args[0];
    else if( args.length == 2 && args[0].equals("-f") )
      fname = args[1];
    else
      System.err.println("Just provide the filename if wanted");
    
    new VmLauncher(fname);
  }

}



