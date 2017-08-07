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
import com.axios.ccdp.controllers.aws.AWSCcdpVMControllerImpl;
import com.axios.ccdp.utils.CcdpImageInfo;
import com.axios.ccdp.utils.CcdpUtils;
import com.axios.ccdp.utils.CcdpUtils.CcdpNodeType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ObjectNode;

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
  
  private JsonNode config = null;
  
  public VmLauncher(String filename)
  {
    this.logger.debug("Using File " + filename);
    this.mapper.enable(SerializationFeature.INDENT_OUTPUT);
    
    try
    {
      File file = new File(filename);
      if( file != null && file.isFile() )
      {
        byte[] data = Files.readAllBytes(Paths.get( file.getAbsolutePath()));
        this.config = this.mapper.readTree( data );
        this.launchVM();
      }
      else
      {
        ObjectNode node = this.mapper.createObjectNode();

        node.put("image-id", "ami-f83a1d83");
        node.put("instance-type", "t2.micro");
        node.put("sec-group", "sg-54410d2f");
        node.put("subnet", "subnet-d7008b8f");
        node.put("key-file", "aws_serv_server_key");
        node.put("role", "");
        node.put("user-data", "/data/ccdp/ccdp_install.py -a download -d s3://ccdp-settings/ccdp-engine.tgz -w -t /data/ccdp");
        ObjectNode tags = this.mapper.createObjectNode();
        tags.put("session-id", "Service-Node");
        tags.put( "Name", "Host-Agent");
        node.set("tags", tags);
        
        this.config = this.mapper.readTree(node.toString());
        
        this.launchVM();
      }
      
    }
    catch( Exception e )
    {
      this.logger.error("Message: " + e.getMessage(), e);
    }
    
  }
  
  
  private void launchVM() throws Exception
  {
    this.logger.debug("Launching a new VM using " + 
                      this.mapper.writeValueAsString(this.config) );
    String imgId = this.config.get("image-id").asText();
    String secGrp = this.config.get("security-group").asText();
    String subNet = this.config.get("subnet").asText();
    String keyFile = this.config.get("key-file").asText();
    
    String instType = "t2.micro";
    String field = this.getField("instance-type");
    
    if( field != null )
    {
      this.logger.debug("Adding Instance Type " + field);
      instType = field;
    }
    
    String user_data = "";
    field = this.getField("user-data");
    if( field != null )
    {
      user_data = "#!/bin/bash\n\n" + field;
      this.logger.debug("Adding User Data " + field);
    }
    
    Map<String, String> tags = new HashMap<>();
    if( this.config.has("tags") )
    {
      this.logger.debug("Adding Tags");
      JsonNode json = this.config.get("tags");
      Iterator<String> fields = json.fieldNames();
      while( fields.hasNext() )
      {
        String name = fields.next();
        tags.put(name, json.get(name).asText());
      }
    }
    
    RunInstancesRequest request = new RunInstancesRequest(imgId, 1, 1);
    
    request.withInstanceType(instType)
           .withUserData(user_data)
           .withSecurityGroupIds(secGrp)
           .withSubnetId(subNet)
           .withKeyName(keyFile);
    
    String role = this.getField("role");
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
    
    String url = this.getField("proxy-url");
    if( url != null )
    {
      this.logger.debug("Adding a Proxy " + url);
      cc.setProxyHost(url);
    }
    String port_str = this.getField("proxy-port");
    if( port_str != null )
    {
      int port = Integer.parseInt(port_str);
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
    
    String region = this.getField("region");
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
  
  /**
   * If the value of the given field exists and is not set to null or is empty
   * it returns the value of that field otherwise it returns null
   *  
   * @param name the name of the field whose value is required
   * @return the value of the field or null if either is not set, is set to 
   *         null, or is set to an empty string.
   */
  private String getField( String name )
  {
    if( this.config.has(name) )
    {
      String field = this.config.get(name).asText();
      if( field.equals("null") || field.length() == 0 )
        return null;
      else
        return field;
    }
    return null;
  }
  
  
  public static void main( String[] args ) throws Exception
  {
    String cfg_file = System.getProperty("ccdp.config.file");
    CcdpUtils.loadProperties(cfg_file);
    CcdpUtils.configLogger();
    String fname = null;
    if( args.length > 0 )
    {
      if( args[0].equals("-f") && args.length >= 2 )
        fname = args[1];
      else
        fname = args[0];
    }
      
    
    new VmLauncher(fname);
  }

}



