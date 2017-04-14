package com.axios.ccdp.mesos.controllers.aws;

import java.io.IOException;
import java.util.ArrayList;

import com.amazonaws.util.Base64;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.apache.log4j.Logger;

import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.http.SdkHttpMetadata;
import com.amazonaws.services.ec2.AmazonEC2;
import com.amazonaws.services.ec2.AmazonEC2Client;
import com.amazonaws.services.ec2.model.DescribeInstanceStatusRequest;
import com.amazonaws.services.ec2.model.DescribeInstanceStatusResult;
import com.amazonaws.services.ec2.model.DescribeInstancesRequest;
import com.amazonaws.services.ec2.model.DescribeInstancesResult;
import com.amazonaws.services.ec2.model.Instance;
import com.amazonaws.services.ec2.model.InstanceStatus;
import com.amazonaws.services.ec2.model.InstanceStatusDetails;
import com.amazonaws.services.ec2.model.Reservation;
import com.amazonaws.services.ec2.model.RunInstancesRequest;
import com.amazonaws.services.ec2.model.RunInstancesResult;
import com.amazonaws.services.ec2.model.StopInstancesRequest;
import com.amazonaws.services.ec2.model.StopInstancesResult;
import com.amazonaws.services.ec2.model.Tag;
import com.amazonaws.services.ec2.model.TerminateInstancesRequest;
import com.amazonaws.services.ec2.model.TerminateInstancesResult;
import com.axios.ccdp.mesos.connections.intfs.CcdpVMControllerIntf;
import com.axios.ccdp.mesos.factory.AWSCcdpFactoryImpl;
import com.axios.ccdp.mesos.utils.CcdpUtils;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

public class AWSCcdpVMControllerImpl implements CcdpVMControllerIntf
{
  /** The Image ID to use */
  public static final String FLD_IMAGE_ID = "image.id";
  /** The security group resource ID to use */
  public static final String FLD_SECURITY_GRP = "security.group";
  /** The subnet resource id to use */
  public static final String FLD_SUBNET_ID    = "subnet.id";
  /** The type of instance to deploy (default t2.micro) */
  public static final String FLD_INST_TYPE    = "instance.type";
  /** The name of the .pem key file (without the extension) */
  public static final String FLD_KEY_FILE     = "key.file.name";
  /** The User data to use */
  public static final String FLD_USER_DATA = "user.data";
  /** The tags use */
  public static final String FLD_TAGS = "tags";
  /**
   * Stores the command to execute at startup
   */
  public static final String USER_DATA =  "#!/bin/bash\n\n "
      + "/data/ccdp_env.py -a download -i ";
  
  /**
   * Generates debug print statements based on the verbosity level.
   */
  private Logger logger = Logger.getLogger(AWSCcdpVMControllerImpl.class
      .getName());
  
  /**
   * Stores all the data configuration for this object
   */
  private ObjectNode config = null;
  /**
   * Object responsible for authenticating with AWS
   */
  private AmazonEC2 ec2 = null;
  
  /**
   * Creates all the ObjectNode and ArrayNode objects
   */
  private ObjectMapper mapper = new ObjectMapper();
  
  /**
   * Instantiates a new object, but it does not do anything
   */
  public AWSCcdpVMControllerImpl()
  {
    this.logger.debug("Creating new Controller");
  }

  /**
   * Sets the configuration object containing all the related information 
   * regarding credential, EC2 type, etc.  In order to authenticate this object
   * uses the following:
   * 
   *  - Environment Variables:
   *      AWS_ACCESS_KEY_ID and AWS_SECRET_ACCESS_KEY
   *  - Java System Properties: 
   *      aws.accessKeyId and aws.secretKey
   *  - Credential File:
   *      ~/.aws/credentials as the default location and uses 'default' as the
   *      profile name, or credential-file and profile-name in the configuration
   *  - ECS Container Credentials
   *      AWS_CONTAINER_CREDENTIALS_RELATIVE_URI
   * 
   *  At least one of this methods need to be valid otherwise it throws an 
   *  exception
   *  
   *  Required Fields:
   *    security-group:     The security group resource ID to use
   *    subnet-id:          The subnet resource id to use
   *    key-file-name:      The name of the .pem key file 
   *  
   *  If the configuration file is missing one or more of the required fields 
   *  it throws an IllegalArgumentException
   *  
   *  Additional Fields:
   *     credentials-file:  The name of the files with access keys
   *     profile-name:      The profile to use in the given file
   *     instance-type:     The type of instance to deploy (default t2.micro)   
   *      
   * @param config a JSON object containing the required configuration to
   *        manipulate resources in AWS
   * 
   * @throws IllegalArgumentException an IllegalArgumentException is thrown if
   *         it cannot find at least one of the different methods to 
   *         authenticate the user
   */
  @Override
  public void configure( ObjectNode config )
  {
    this.logger.debug("Configuring ResourceController using: " + config);
    // the configuration is required
    if( config == null )
      throw new IllegalArgumentException("The config cannot be null");
    // let's check all the required fields
    String msg = null;
    if(!config.has(FLD_SECURITY_GRP) )
    {
      msg = "The JSON configuration is missing a required field (" 
            + FLD_SECURITY_GRP + ")";
    }
    else if( !config.has(FLD_SUBNET_ID) )
    {
      msg = "The JSON configuration is missing a required field (" 
          + FLD_SUBNET_ID + ")";
    }
    
    
    if( msg != null )
    {
      throw new IllegalArgumentException(msg);
    }
    this.config = config;
    
    AWSCredentials credentials = AWSCcdpFactoryImpl.getAWSCredentials(config);
    
    if( credentials != null )
    {
      this.ec2 = new AmazonEC2Client(credentials);
    }
  }
  
  /**
   * Starts one or more VM instances using the defined Image ID as given by the
   * imageId argument.  The number of instances are determined by the min and 
   * max arguments.  If the tags is not null then they are set and the new 
   * Virtual Machine will contain them.
   * 
   * @param min the minimum number of Virtual Machines to create
   * @param max the maximum number of Virtual Machines to create
   * 
   * @return a list of unique Virtual Machine identifiers
   */
  @Override
  public List<String> startInstances(int min, int max )
  {
    String imgId = this.config.get(FLD_IMAGE_ID).asText();
    HashMap<String, String> map = null;
    
    if(this.config.has(FLD_TAGS))
    {
      JsonNode tags = this.config.get(FLD_TAGS);
      
      try
      {
        if( tags != null )
        {
          map = this.mapper.readValue(tags.asText(),
              new TypeReference<HashMap<String, String>>() {
              });
        }
      }
      catch( IOException e )
      {
        this.logger.error("Message: " + e.getMessage(), e);
      }  
    }
    
    return this.startInstances(imgId, min, max, null);
  }

  /**
   * Starts one or more VM instances using the defined Image ID as given by the
   * imageId argument.  The number of instances are determined by the min and 
   * max arguments.  If the tags is not null then they are set and the new 
   * Virtual Machine will contain them.
   * 
   * @param min the minimum number of Virtual Machines to create
   * @param max the maximum number of Virtual Machines to create
   * 
   * @return a list of unique Virtual Machine identifiers
   */
  @Override
  public List<String> startInstances(int min, int max, String session_id )
  {
    String imgId = this.config.get(FLD_IMAGE_ID).asText();
    HashMap<String, String> map = null;
    
    if(this.config.has(FLD_TAGS))
    {
      JsonNode tags = this.config.get(FLD_TAGS);
      
      try
      {
        if( tags != null )
        {
          map = this.mapper.readValue(tags.asText(),
              new TypeReference<HashMap<String, String>>() {
              });
        }
      }
      catch( IOException e )
      {
        this.logger.error("Message: " + e.getMessage(), e);
      }  
    }
    
    return this.startInstances(imgId, min, max, session_id, map);
  }
  

  /**
   * Starts one or more VM instances using the defined Image ID as given by the
   * imageId argument.  The number of instances are determined by the min and 
   * max arguments.  If the tags is not null then they are set and the new 
   * Virtual Machine will contain them.
   * 
   * @param imgId the image to use to create new Virtual Machines
   * @param min the minimum number of Virtual Machines to create
   * @param max the maximum number of Virtual Machines to create
   * @param tags optional map containing key-value pairs to set
   * 
   * @return a list of unique Virtual Machine identifiers
   */
  @Override
  public List<String> startInstances(String imgId, int min, int max, 
                                  Map<String, String> tags)
  {
    return this.startInstances(imgId, min, max, null, tags);
  }

  /**
   * Starts one or more VM instances using the defined Image ID as given by the
   * imageId argument.  The number of instances are determined by the min and 
   * max arguments.  If the tags is not null then they are set and the new 
   * Virtual Machine will contain them.
   * 
   * @param imgId the image to use to create new Virtual Machines
   * @param min the minimum number of Virtual Machines to create
   * @param max the maximum number of Virtual Machines to create
   * @param session_id the session id to assign to this resource
   * @param tags optional map containing key-value pairs to set
   * 
   * @return a list of unique Virtual Machine identifiers
   */
  @Override
  public List<String> startInstances(String imgId, int min, int max, 
                                  String session_id, Map<String, String> tags)
  {
    List<String> launched = null;
    
    RunInstancesRequest request = new RunInstancesRequest(imgId, min, max);
    String instType = "t2.micro";
    if( this.config.has(FLD_INST_TYPE) )
      instType = this.config.get(FLD_INST_TYPE).asText();
    
    // Do we need to add session id?
    String user_data = USER_DATA;
    if ( session_id != null )
      user_data += "-s " + session_id;
    
    this.logger.info("Using User Data: " + user_data);
    // encode data on your side using BASE64
    byte[]   bytesEncoded = Base64.encode(user_data.getBytes());
    this.logger.debug("ecncoded value is " + new String(bytesEncoded));
    
    request.withInstanceType(instType)
         .withUserData(new String(bytesEncoded ))
         .withSecurityGroupIds(this.config.get(FLD_SECURITY_GRP).asText())
         .withSubnetId(this.config.get(FLD_SUBNET_ID).asText())
         .withKeyName(this.config.get(FLD_KEY_FILE).asText());
    
    RunInstancesResult result = this.ec2.runInstances(request);
    SdkHttpMetadata shm = result.getSdkHttpMetadata();
    
    int code = shm.getHttpStatusCode();
    if( code == 200 )
    {
      this.logger.info("EC2 start request sent successfully");
      launched = new ArrayList<String>();
      
      Reservation reservation = result.getReservation();
      Iterator<Instance> instances = reservation.getInstances().iterator();
      while( instances.hasNext() )
      {
        Instance inst = instances.next();
        String instId = inst.getInstanceId();
        
        List<Tag> new_tags = new ArrayList<Tag>();
        new_tags.add(new Tag(CcdpUtils.KEY_INSTANCE_ID, instId));
        
        if( tags != null )
        {
          this.logger.info("Setting Tags");
          
          Iterator<String> keys = tags.keySet().iterator();
          while( keys.hasNext() )
          {
            String key = keys.next();
            String val = tags.get(key);
            this.logger.debug("Setting Tag[" + key + "] = " + val);
            new_tags.add(new Tag(key , val));
          }
          
        }
        
        inst.setTags(new_tags);
        launched.add(instId);
      }
    }
    else
    {
      this.logger.error("Could not start instances, error code: " + code);
    }
    
    return launched;
    
  }
  
  
  /**
   * Stops each one of the Virtual Machines whose unique identifier matches the
   * ones given in the argument
   * 
   * @param instIDs a list of unique identifiers used to determine which Virtual
   *        Machine needs to be stopped
   *        
   * @return true if the request was submitted successfully or false otherwise
   */
  @Override
  public boolean stopInstances( List<String> instIDs )
  {
    boolean stopped = false;
    StopInstancesRequest request = new StopInstancesRequest(instIDs);
    StopInstancesResult result = this.ec2.stopInstances(request);
    
    SdkHttpMetadata shm = result.getSdkHttpMetadata();
    
    int code = shm.getHttpStatusCode();
    if( code == 200 )
    {
      this.logger.debug("Stop Request Successful");
      stopped = true;
    }
    else
    {
      this.logger.error("Could not stop instances, error code: " + code);
      stopped = false;
    }
    
    return stopped;
  }
  
  /**
   * Terminates each one of the Virtual Machines whose unique identifier matches
   * the ones given in the argument
   * 
   * @param instIDs a list of unique identifiers used to determine which Virtual
   *        Machine needs to be terminated
   *        
   * @return true if the request was submitted successfully or false otherwise
   */
  @Override
  public boolean terminateInstances( List<String> instIDs )
  {
    if( instIDs == null || instIDs.isEmpty() )
    {
      this.logger.info("No instances to terminate");
      return false;
    }
    
    this.logger.info("Terminating Instances");
    boolean terminated = false;
    TerminateInstancesRequest request = new TerminateInstancesRequest(instIDs);
    TerminateInstancesResult result = this.ec2.terminateInstances(request);
    
    SdkHttpMetadata shm = result.getSdkHttpMetadata();
    
    int code = shm.getHttpStatusCode();
    if( code == 200 )
    {
      this.logger.debug("Stop Request Successful");
      terminated = true;
    }
    else
    {
      this.logger.error("Could not stop instances, error code: " + code);
      terminated = false;
    }
    
    return terminated;
  }
  
  /**
   * Gets all the instances status that are currently assigned to the user
   * 
   * @return an object containing details of each of the Virtual Machines 
   *         assigned to the user
   */
  @Override
  public ObjectNode getAllInstanceStatus()
  {
    this.logger.debug("Getting all the Instances Status");
    ObjectNode instancesJson = this.mapper.createObjectNode();
    
    DescribeInstanceStatusRequest descInstReq = 
        new DescribeInstanceStatusRequest()
            .withIncludeAllInstances(true);
    DescribeInstanceStatusResult descInstRes = 
                              this.ec2.describeInstanceStatus(descInstReq);
    
    List<InstanceStatus> state = descInstRes.getInstanceStatuses();
    
    Iterator<InstanceStatus> states = state.iterator();
    this.logger.debug("Found " + state.size() + " instances");
    while( states.hasNext() )
    {
      InstanceStatus stat = states.next();
      
      String instId = stat.getInstanceId();
      String status = stat.getInstanceState().getName();
      ObjectNode obj = this.mapper.createObjectNode();
      
      obj.put("status", status);
      List<InstanceStatusDetails>  dets = stat.getInstanceStatus().getDetails();
      Iterator<InstanceStatusDetails> details = dets.iterator();
      ObjectNode jDets = this.mapper.createObjectNode();
      
      while( details.hasNext() )
      {
        InstanceStatusDetails detail = details.next();
        String name = detail.getName();
        String val = detail.getStatus();
        jDets.put(name, val);
      }
      obj.set("details", jDets);
      this.logger.debug("Adding: " + obj);
      instancesJson.set(instId, obj);
    }
    
    
    DescribeInstancesRequest req = new DescribeInstancesRequest();
    DescribeInstancesResult res = this.ec2.describeInstances(req);
    Iterator<Reservation> reservations = res.getReservations().iterator();
    while( reservations.hasNext() )
    {
      Reservation reserve = reservations.next();
      Iterator<Instance> instances = reserve.getInstances().iterator();
      while( instances.hasNext() )
      {
        Instance instance = instances.next();
        String id = instance.getInstanceId();
        String pubIp = instance.getPublicIpAddress();
        String privIp = instance.getPrivateIpAddress();
        if( instancesJson.has(id) )
        {
          try
          {
            JsonNode node = instancesJson.get(id);
            this.logger.debug("The JSON Node: " + node.toString());
            ObjectNode instJson = node.deepCopy(); 
            
            instJson.put("public-ip", pubIp);
            instJson.put("private-ip", privIp);
            
            ObjectNode jsonTag = this.mapper.createObjectNode();
            Iterator<Tag> tags = instance.getTags().iterator();
            while( tags.hasNext() )
            {
              Tag tag = tags.next();
              String key = tag.getKey();
              String val = tag.getValue();
              jsonTag.put(key, val);
            }
            instJson.set("tags", jsonTag);
          }
          catch( Exception ioe )
          {
            this.logger.error("Message: " + ioe.getMessage(), ioe);
          }
          
        }// instance ID found
      }
    }
    return instancesJson;
  }
  
  /**
   * Returns information about all instances matching the set of filters given
   * by the filter JSON object.  In other words, if the instance contains a tag
   * matching ALL the names and values of the given in the filter then is 
   * flagged as a valid result.
   * 
   * The result is a JSON Object whose key is the Virtual Machine identifier and
   * the value is detailed information of the VM.
   * 
   * @param filter a JSON object containing the criteria to filter the Virtual
   *        Machines
   *        
   * @return A JSON Object containing all the Virtual Machines matching the 
   *         criteria
   */
  public ObjectNode getStatusFilteredByTags( ObjectNode filter )
  {
    this.logger.debug("Getting Filtered Status using: " + filter);
    ObjectNode all = this.getAllInstanceStatus();
    ObjectNode some = this.mapper.createObjectNode();
    
    this.logger.debug("All Instances: " + all);
    Iterator<String> all_keys = all.fieldNames();
    
    while( all_keys.hasNext() )
    {
      String id = all_keys.next();
      this.logger.debug("Looking at ID " + id);
      
      JsonNode inst = all.get(id);
      
      if( inst.has("tags") )
      {
        JsonNode tags = inst.get("tags");
        Iterator<String> filter_keys = filter.fieldNames();
        boolean found = true;
        while( filter_keys.hasNext() )
        {
          String key = filter_keys.next();
          Object val = filter.get(key);
          this.logger.debug("Evaluating Filter[" + key + "] = " + val );
          if( !tags.has(key) || !tags.get(key).equals(val) )
          {
            this.logger.info("Instance " + id + " does not have matching tag " + key);
            found = false;
            break;
          }
        }// end of filter keys while loop
        
        // if all the keys and values matched, then add it to the final result
        if( found )
        {
          this.logger.info("Adding Instance to list");
          some.set(id, inst);
        }
      }// it has tags to compare
    }// All instances checked
    
    return some;
  }
  
  
  
  /**
   * Returns information about the instance matching the unique id given as 
   * argument.  
   * 
   * The result is a JSON Object whose key is the Virtual Machine identifier and
   * the value is detailed information of the VM.
   * 
   * @param uuid the unique identifier used to select the appropriate resource
   *        
   * @return A JSON Object containing all the information about the VM
   */
  public ObjectNode getStatusFilteredById( String uuid )
  {
    this.logger.debug("Getting Filtered Status for: " + uuid);
    ObjectNode all = this.getAllInstanceStatus();
    
    this.logger.debug("All Instances: " + all);
    Iterator<String> all_keys = all.fieldNames();
    
    while( all_keys.hasNext() )
    {
      String id = all_keys.next();
      this.logger.debug("Looking at ID " + id);
      
      // found it
      if(id.equals(uuid))
        return all.get(id).deepCopy();
      
    }// All instances checked
    
    return null;
  }
  
}
