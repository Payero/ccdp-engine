package com.axios.ccdp.controllers.aws;

import java.io.File;
import java.util.ArrayList;

import com.amazonaws.util.Base64;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.apache.log4j.Logger;

import com.amazonaws.ClientConfiguration;
import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.InstanceProfileCredentialsProvider;
import com.amazonaws.auth.EnvironmentVariableCredentialsProvider;
import com.amazonaws.auth.SystemPropertiesCredentialsProvider;
import com.amazonaws.auth.profile.ProfileCredentialsProvider;
import com.amazonaws.auth.profile.ProfilesConfigFile;
import com.amazonaws.http.SdkHttpMetadata;
import com.amazonaws.regions.Region;
import com.amazonaws.regions.RegionUtils;
import com.amazonaws.services.ec2.AmazonEC2;
import com.amazonaws.services.ec2.AmazonEC2Client;
import com.amazonaws.services.ec2.model.CreateTagsRequest;
import com.amazonaws.services.ec2.model.DescribeInstanceStatusRequest;
import com.amazonaws.services.ec2.model.DescribeInstanceStatusResult;
import com.amazonaws.services.ec2.model.DescribeInstancesRequest;
import com.amazonaws.services.ec2.model.DescribeInstancesResult;
import com.amazonaws.services.ec2.model.IamInstanceProfileSpecification;
import com.amazonaws.services.ec2.model.Instance;
import com.amazonaws.services.ec2.model.InstanceStatus;
import com.amazonaws.services.ec2.model.Reservation;
import com.amazonaws.services.ec2.model.RunInstancesRequest;
import com.amazonaws.services.ec2.model.RunInstancesResult;
import com.amazonaws.services.ec2.model.StopInstancesRequest;
import com.amazonaws.services.ec2.model.StopInstancesResult;
import com.amazonaws.services.ec2.model.Tag;
import com.amazonaws.services.ec2.model.TerminateInstancesRequest;
import com.amazonaws.services.ec2.model.TerminateInstancesResult;
import com.axios.ccdp.connections.intfs.CcdpVMControllerIntf;
import com.axios.ccdp.resources.CcdpVMResource;
import com.axios.ccdp.resources.CcdpVMResource.ResourceStatus;
import com.axios.ccdp.utils.CcdpImageInfo;
import com.axios.ccdp.utils.CcdpUtils;
import com.axios.ccdp.utils.CcdpUtils.CcdpNodeType;
import com.fasterxml.jackson.databind.node.ObjectNode;

public class AWSCcdpVMControllerImpl implements CcdpVMControllerIntf
{
  /** Stores the name of the ACCESS KEY Environment Variable **/
  public static final String ACCESS_KEY_ID_ENV_VAR = "AWS_ACCESS_KEY_ID";
  /** Stores the name of the ACCESS SECRET Environment Variable **/
  public static final String ACCESS_SECRET_ENV_VAR = "AWS_SECRET_ACCESS_KEY";
  /** Stores the name of the ACCESS KEY System Property **/
  public static final String ACCESS_KEY_ID_PROPERTY = "aws.accessKeyId";
  /** Stores the name of the ACCESS SECRET System Property **/
  public static final String ACCESS_SECRET_PROPERTY = "aws.secretKey";
  
//  /** The name of the files with access keys */
//  public static final String FLD_CREDS_FILE   = "credentials.file";
//  /** The profile to use in the given file */
//  public static final String FLD_PROFILE_NAME = "profile.name";
//  /** The type of instance to deploy (default t2.micro) */
//  public static final String FLD_INST_TYPE    = "instance.type";
//  /** The command to run under a bash script */
//  public static final String FLD_STARTUP_COMMAND    = "startup.command";
//  /** The region to use, if other than default */
//  public static final String FLD_REGION    = "region";
//  /** The URL of a proxy to use to contact AWS */
//  public static final String FLD_PROXY_URL    = "proxy.url";
//  /** The port number of a proxy to use to contact AWS */
//  public static final String FLD_PROXY_PORT    = "proxy.port";
//  /** The role to assign to newly created instances */
//  public static final String FLD_ROLE_NAME    = "role.name";
  
  /**
   * Stores the command to execute at startup
   */
  public static final String USER_DATA =  "#!/bin/bash\n\n ";
  
  /**
   * Generates debug print statements based on the verbosity level.
   */
  private static Logger logger = Logger.getLogger(AWSCcdpVMControllerImpl.class
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
   * Instantiates a new object, but it does not do anything
   */
  public AWSCcdpVMControllerImpl()
  {
    logger.debug("Creating new Controller");
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
    logger.debug("Configuring ResourceController using: " + config);
    // the configuration is required
    if( config == null )
      throw new IllegalArgumentException("The config cannot be null");
    
    this.config = config;
    
    // need to make sure the default configuration is set properly
    CcdpImageInfo def = 
        CcdpUtils.getImageInfo(CcdpNodeType.DEFAULT);
    if( def.getImageId() == null || 
        def.getSecGrp() == null || 
        def.getSubnet() == null )
    {
      String msg = "One of the required fields for the default VM configuraion "
          + "is missing.  Please make sure the system is configured propertly.";
      
      throw new IllegalArgumentException(msg);
    }
    
    /**
     * Need to provide basic credentials as well as a way to set the proxy if
     * required.
     */
    String fname = def.getCredentialsFile();
    String profile = def.getProfileName();
    AWSCredentials credentials = 
        AWSCcdpVMControllerImpl.getAWSCredentials(fname, profile);
    
    // get the proxy port if it was set
    ClientConfiguration cc = new ClientConfiguration();
    int port = def.getProxyPort();
    if( port > 0 )
    {
      logger.info("Setting Proxy Port: " + port);
      cc.setProxyPort(port);
    }
    
    // get the proxy url if it was provided
    String proxy = def.getProxyUrl();
    if( proxy != null )
    {
      logger.info("Setting Proxy: " + proxy);
      cc.setProxyHost(proxy);
    }
    
    // Create a new EC2 client using available credentials and configuration
    if( credentials != null )
    {
      this.ec2 = new AmazonEC2Client(credentials, cc);
    }
    else
    {
      this.ec2 = new AmazonEC2Client(cc);
    }
    
    // set the region if available
    String region = def.getRegion();
    if( region != null )
    {
      logger.info("Setting the Region to " + region);
      Region reg = RegionUtils.getRegion(region);
      this.ec2.setRegion( reg );
    }
  }
  
  /**
   * Starts one or more VM instances using the defined Image ID as given by the
   * imageId argument.  The number of instances are determined by the min and 
   * max arguments.  If the tags is not null then they are set and the new 
   * Virtual Machine will contain them.
   * 
   * @param imgCfg the image configuration containing all the parameters 
   *        required to start an instance
   * 
   * 
   * @return a list of unique Virtual Machine identifiers
   */
  @Override
  public List<String> startInstances( CcdpImageInfo imgCfg )
  {
    List<String> launched = null;
    
    String imgId = imgCfg.getImageId();
    int min = imgCfg.getMinReq();
    int max = imgCfg.getMaxReq();
    if( min == 0 )
      min = 1;
   
    if( max == 0 )
      max = 1;

    String session_id = imgCfg.getSessionId();
    String type = imgCfg.getNodeTypeAsString();
    
    logger.info("Starting VM of type " + type + " for session " + session_id ) ;
    
    Map<String, String> tags = imgCfg.getTags();
    
    //RunInstancesRequest request = new RunInstancesRequest(imgId, min, max);
    RunInstancesRequest request = new RunInstancesRequest(imgId, 1, 1);
    String instType = imgCfg.getInstanceType();
    
    // Do we need to add session id?
    String user_data = USER_DATA + imgCfg.getStartupCommand();
    
    if ( session_id != null )
      user_data += " -s " + session_id;
    
    logger.info("Using User Data: " + user_data);
    // encode data on your side using BASE64
    byte[]   bytesEncoded = Base64.encode(user_data.getBytes());
    logger.trace("encoded value is " + new String(bytesEncoded));
    
    request.withInstanceType(instType)
         .withUserData(new String(bytesEncoded ))
         .withSecurityGroupIds(imgCfg.getSecGrp())
         .withSubnetId(imgCfg.getSubnet())
         .withKeyName(imgCfg.getKeyFile());
    
    String role = imgCfg.getRoleName(); 
    if( role != null )
    {
      logger.debug("Adding Role to the request: " + role);
      IamInstanceProfileSpecification iam = 
                  new IamInstanceProfileSpecification();
      iam.setName(role);
      request.setIamInstanceProfile(iam);
    }
    
    RunInstancesResult result = this.ec2.runInstances(request);
    SdkHttpMetadata shm = result.getSdkHttpMetadata();
    
    int code = shm.getHttpStatusCode();
    if( code == 200 )
    {
      logger.info("EC2 start request sent successfully");
      launched = new ArrayList<String>();
      
      Reservation reservation = result.getReservation();
      Iterator<Instance> instances = reservation.getInstances().iterator();
      while( instances.hasNext() )
      {
        Instance inst = instances.next();
        String instId = inst.getInstanceId();
        logger.debug("Adding Tags to " + instId);
        
        // Tags are added by creating a CreateTagRequest
        CreateTagsRequest tagsReq = new CreateTagsRequest();
        
        List<Tag> new_tags = new ArrayList<Tag>();
        new_tags.add(new Tag(CcdpUtils.KEY_INSTANCE_ID, instId));
        
        if( tags != null )
        {
          logger.info("Setting Tags");
          
          Iterator<String> keys = tags.keySet().iterator();
          while( keys.hasNext() )
          {
            String key = keys.next();
            String val = tags.get(key);
            logger.debug("Setting Tag[" + key + "] = " + val);
            new_tags.add(new Tag(key , val));
          }
        }
        // associate the tags with a resource and create them
        tagsReq.withResources(instId)
               .withTags(new_tags);
        this.ec2.createTags(tagsReq);
        
        // Add the instance id to the list of launched
        launched.add(instId);
      }
    }
    else
    {
      logger.error("Could not start instances, error code: " + code);
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
      logger.debug("Stop Request Successful");
      stopped = true;
    }
    else
    {
      logger.error("Could not stop instances, error code: " + code);
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
      logger.info("No instances to terminate");
      return false;
    }
    
    logger.info("Terminating Instances");
    boolean terminated = false;
    TerminateInstancesRequest request = new TerminateInstancesRequest(instIDs);
    TerminateInstancesResult result = this.ec2.terminateInstances(request);
    
    SdkHttpMetadata shm = result.getSdkHttpMetadata();
    
    int code = shm.getHttpStatusCode();
    if( code == 200 )
    {
      logger.debug("Stop Request Successful");
      terminated = true;
    }
    else
    {
      logger.error("Could not stop instances, error code: " + code);
      terminated = false;
    }
    
    return terminated;
  }
  
  /**
   * Gets all the instances status that are currently 'available' on different
   * states
   * 
   * @return a list with all the instances status that are currently 
   *         'available' on different state
   */
  @Override
  public List<CcdpVMResource> getAllInstanceStatus()
  {
    logger.debug("Getting all the Instances Status");
    Map<String, CcdpVMResource> resources = new HashMap<>();
    
    DescribeInstanceStatusRequest descInstReq = 
        new DescribeInstanceStatusRequest()
            .withIncludeAllInstances(true);
    DescribeInstanceStatusResult descInstRes = 
                              this.ec2.describeInstanceStatus(descInstReq);
    
    List<InstanceStatus> state = descInstRes.getInstanceStatuses();
    
    Iterator<InstanceStatus> states = state.iterator();
    logger.debug("Found " + state.size() + " instances");
    while( states.hasNext() )
    {
      InstanceStatus stat = states.next();
      
      String instId = stat.getInstanceId();
      System.out.println("INSTANCE ID: " + instId);
      CcdpVMResource res = new CcdpVMResource(instId);
      
      String status = stat.getInstanceState().getName();
      switch( status )
      {
      case "pending":
        res.setStatus(ResourceStatus.INITIALIZING);
        System.out.println("STATUS SET TO NITIALIZING");
        break;
      case "running":
        res.setStatus(ResourceStatus.RUNNING);
        break;
      case "shutting-down":
        res.setStatus(ResourceStatus.SHUTTING_DOWN);
        break;
      case "terminated":
        res.setStatus(ResourceStatus.TERMINATED);
        break;
      case "stopping":
      case "stopped":
        res.setStatus(ResourceStatus.STOPPED);
        break;
        
      }  
      resources.put(instId, res);
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
        if( resources.containsKey(id) )
        {
          try
          {
            CcdpVMResource vm = resources.get(id);
            vm.setHostname(pubIp);
            
            Iterator<Tag> tags = instance.getTags().iterator();
            Map<String, String> map = new HashMap<>();
            while( tags.hasNext() )
            {
              Tag tag = tags.next();
              String key = tag.getKey();
              String val = tag.getValue();
              map.put(key, val);
            }
            vm.setTags(map);
          }
          catch( Exception ioe )
          {
            logger.error("Message: " + ioe.getMessage(), ioe);
          }
          
        }// instance ID found
      }
    }
    
    return new ArrayList<CcdpVMResource>( resources.values() );
  }
  
  
  /**
   * Gets the current instance state of the resource with the given id
   * 
   * @return the status of the resource
   */
  @Override
  public ResourceStatus getInstanceState(String id)
  {
    DescribeInstanceStatusRequest descInstReq = 
        new DescribeInstanceStatusRequest()
          .withInstanceIds(id);
    DescribeInstanceStatusResult descInstRes = 
                              this.ec2.describeInstanceStatus(descInstReq);
    
    List<InstanceStatus> state = descInstRes.getInstanceStatuses();
    //Default as launched
    ResourceStatus updatedstat = ResourceStatus.LAUNCHED;
    
    Iterator<InstanceStatus> states = state.iterator();
    String status = new String();
    while( states.hasNext() )
    {
      InstanceStatus stat = states.next();
      
      String instId = stat.getInstanceId();
      CcdpVMResource res = new CcdpVMResource(instId);
      
      status = stat.getInstanceState().getName();
      switch( status )
      {
      case "pending":
        updatedstat = ResourceStatus.INITIALIZING;
        break;
      case "running":
        updatedstat = ResourceStatus.RUNNING;
        break;
      case "shutting-down":
        updatedstat = ResourceStatus.SHUTTING_DOWN;
        break;
      case "terminated":
        updatedstat = ResourceStatus.TERMINATED;
        break;
      case "stopping":
      case "stopped":
        updatedstat = ResourceStatus.STOPPED;
        break;
      }  
    }
    return updatedstat;
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
  public List<CcdpVMResource> getStatusFilteredByTags( ObjectNode filter )
  {
    logger.debug("Getting Filtered Status using: " + filter);
    List<CcdpVMResource> all = this.getAllInstanceStatus();
    List<CcdpVMResource> some = new ArrayList<>();
    
    logger.debug("All Instances: " + all);
    
    for(CcdpVMResource inst : all )
    {
      String id = inst.getInstanceId();
      logger.debug("Looking at ID " + id);
      Map<String, String> tags = inst.getTags();
      
      if( tags != null  )
      {
        Iterator<String> filter_keys = filter.fieldNames();
        boolean found = true;
        while( filter_keys.hasNext() )
        {
          String key = filter_keys.next();
          Object val = filter.get(key);
          logger.debug("Evaluating Filter[" + key + "] = " + val );
          if( !tags.containsKey(key) || !tags.get(key).equals(val) )
          {
            logger.info("Instance " + id + " does not have matching tag " + key);
            found = false;
            break;
          }
        }// end of filter keys while loop
        
        // if all the keys and values matched, then add it to the final result
        if( found )
        {
          logger.info("Adding Instance to list");
          some.add(inst);
        }
      }// it has tags to compare
    }// All instances checked
    
    return some;
  }
  
  
  /**
   * Returns information about the instance matching the unique id given as 
   * argument.  If the object is not found it returns null 
   * 
   * 
   * @param uuid the unique identifier used to select the appropriate resource
   *        
   * @return the resource whose unique identifier matches the given argument
   */
  public CcdpVMResource getStatusFilteredById( String uuid )
  {
    logger.debug("Getting Filtered Status for: " + uuid);
    List<CcdpVMResource> all = this.getAllInstanceStatus();
    
    logger.debug("All Instances: " + all);
    
    for( CcdpVMResource res : all )
    {
      String id = res.getInstanceId();
      logger.debug("Looking at ID " + id);
      
      // found it
      if(id.equals(uuid))
        return res;
      
    }// All instances checked
    
    return null;
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
   *  It uses the default image information to retrieve the credentials 
   *  filename and profile name
   *  
   *  At least one of this methods need to be valid otherwise it throws an 
   *  exception
   *      
   * @return an authentication object using the given parameters
   * 
   * @throws IllegalArgumentException an IllegalArgumentException is thrown if
   *         it cannot find at least one of the different methods to 
   *         authenticate the user
   */
  public static AWSCredentials getAWSCredentials()
  {
    CcdpImageInfo img = CcdpUtils.getImageInfo(CcdpNodeType.DEFAULT);
    String fname = img.getCredentialsFile();
    String profile = img.getProfileName();
    
    return AWSCcdpVMControllerImpl.getAWSCredentials(fname, profile);
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
   * @param credsFile the name of the credentials file to use
   * @param profile the name of the profile to load from the credentials file
   * 
   * @return an authentication object using the given parameters
   * 
   * @throws IllegalArgumentException an IllegalArgumentException is thrown if
   *         it cannot find at least one of the different methods to 
   *         authenticate the user
   */
  public static AWSCredentials getAWSCredentials( String credsFile, String profile )
  {
    if( profile == null )
      profile = "default";
    
    AWSCredentials credentials = null;
    
    // Attempting all different ways 
    if( System.getenv(ACCESS_KEY_ID_ENV_VAR) != null && 
        System.getenv(ACCESS_SECRET_ENV_VAR) != null )
    {
      logger.info("Setting Credentials using Environment Variables");
      credentials = 
          new EnvironmentVariableCredentialsProvider().getCredentials();
    }
    else if( System.getProperty(ACCESS_KEY_ID_PROPERTY) != null &&
             System.getProperty(ACCESS_SECRET_PROPERTY) != null)
    {
      logger.info("Setting Credentials using System Properties");
      credentials = 
          new SystemPropertiesCredentialsProvider().getCredentials();
    }
    else if( credsFile != null )
    {
      logger.info("Setting Credentials using JSON Configuration");
      credentials = AWSCcdpVMControllerImpl.getCredentials(credsFile, profile);
    }
    else if( System.getenv("HOME") != null )
    {
      logger.info("Setting Credentials using default");
      String fname = System.getenv("HOME") + "/.aws/credentials";
      File file = new File(fname);
      if( file.isFile() )
      {
        credentials = AWSCcdpVMControllerImpl.getCredentials(fname, profile);
      }
      else
      {
        logger.info(".aws/credentials file not found using Instance Profile");
        InstanceProfileCredentialsProvider prov = 
            InstanceProfileCredentialsProvider.getInstance();
        credentials = prov.getCredentials();
        if( credentials == null )
        {
         String txt = "Was not able to find any of the different ways to "
             + "authenticate.  At least one method needs to be available";
         throw new IllegalArgumentException(txt);
        } 
      }
    }
    else
    {
      logger.info("Using Instance Profle Credentials Provider");
      InstanceProfileCredentialsProvider prov = 
                   InstanceProfileCredentialsProvider.getInstance();
      credentials = prov.getCredentials();
      if( credentials == null )
      {
        String txt = "Was not able to find any of the different ways to "
            + "authenticate.  At least one method needs to be available";
        throw new IllegalArgumentException(txt);
      }
    }
    
    return credentials;
  }
  
  /**
   * Gets the credentials from an AWS credentials file given by the fname 
   * argument.  The profile arguments refers to the profile name inside the file
   * to be used for authentication
   * 
   * @param fname the full path of the file containing the required fields
   * @param profile the name of the profile to use
   * 
   * @return an AWSCredentials object if properly set or null otherwise
   * @throws IllegalArgumentException an IllegalArgumentException is thrown if
   *         there is a problem during the authentication process
   */
  private static AWSCredentials getCredentials(String fname, String profile)
  {
    AWSCredentials creds = null;
    File file = new File(fname);
    if( file.isFile() )
    {
      ProfilesConfigFile cfgFile = new ProfilesConfigFile(file);
      creds = 
          new ProfileCredentialsProvider( cfgFile, profile ).getCredentials();
    }
    else
    {
      InstanceProfileCredentialsProvider prov =
                   InstanceProfileCredentialsProvider.getInstance();
      creds = prov.getCredentials();
      if( creds == null )
      {
        String txt = "The profiles file (" + fname + ") is invalid.  Please set"
          + " the credentials-file field properly";
        throw new IllegalArgumentException(txt);
      }
    }
    
    return creds;
  }
}
