package com.axios.ccdp.cloud.docker;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.util.ArrayList;

import com.amazonaws.util.Base64;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.StringTokenizer;

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
import com.axios.ccdp.connections.intfs.SystemResourceMonitorIntf;
import com.axios.ccdp.resources.CcdpImageInfo;
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
import com.spotify.docker.client.messages.HostConfig;

public class DockerVMControllerImpl implements CcdpVMControllerIntf
{
  /** Stores the name of the ACCESS KEY Environment Variable **/
  public static final String ACCESS_KEY_ID_ENV_VAR = "AWS_ACCESS_KEY_ID";
  /** Stores the name of the ACCESS SECRET Environment Variable **/
  public static final String ACCESS_SECRET_ENV_VAR = "AWS_SECRET_ACCESS_KEY";
  /** Stores the name of the ACCESS KEY System Property **/
  public static final String ACCESS_KEY_ID_PROPERTY = "aws.accessKeyId";
  /** Stores the name of the ACCESS SECRET System Property **/
  public static final String ACCESS_SECRET_PROPERTY = "aws.secretKey";
  
  /**
   * Generates debug print statements based on the verbosity level.
   */
  private static Logger logger = Logger.getLogger(DockerVMControllerImpl.class
      .getName());
  
  /**
   * Stores all the data configuration for this object
   */
  private ObjectNode config = null;
  
  /**
   * The object used to start and stop Docker Containers
   */
  private DockerClient docker = null;
  /**
   * Whether or not we are using the file system for the distribution file
   */
  private boolean use_fs = true;
  
  /**
   * Instantiates a new object, but it does not do anything
   */
  public DockerVMControllerImpl()
  {
    logger.debug("Creating new Docker Controller");
  }

  /**
   * Sets the configuration object containing all the related information.  It 
   * uses the following fields
   * 
   *  Determines what are the units when reporting back
   *      res.mon.intf.units=KB|MB|GB
   *  How to reach the docker engine
   *      res.mon.intf.docker.url=http://172.17.0.1:2375
   *  Where is a file containing the docker id in the same line as 'docker'
   *      res.mon.intf.cgroup.file=/proc/self/cgroup
   *  The AWS Settings if to allow the docker container to access the S3 bucket
   *  where the install file is located
   *      res.mon.intf.aws.access.id=
   *      res.mon.intf.aws.secret.key=
   *      res.mon.intf.aws.default.region=
   *      
   *  If there is no desire to use AWS, then the distribution file is used to
   *  install the code in the container.
   *  
   *  Determines the location of the distribution file
   *      res.mon.intf.dist.file
   *       
   * @param config a JSON object containing the required configuration to
   *        manipulate resources in a Docker environment
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
    
    if( config.has("dist.file") )
    {
      String filename = config.get("dist.file").asText();
      logger.info("Getting Distribution file from: " + filename );
      File file = new File(filename);
      if( !file.isFile() || !file.canRead() )
      {
        String err = "The distribution file " + filename + " is invalid";
        throw new IllegalArgumentException(err);
      }
      this.use_fs = true;
    }
    else
    {
      if( config.has("aws.access.id") && config.get("aws.access.id") != null && 
          config.has("aws.secret.key") && config.get("aws.secret.key") != null )
      {
        logger.info("Getting Distribution file from AWS S3 bucket");
        this.use_fs = false;
        if( !config.has("aws.region") || config.get("aws.region") == null )
          config.put("aws.region",  "us-east-1");
            
      }
      else
      {
        throw new IllegalArgumentException("Need distribution file");
      }
    }
    String url = CcdpUtils.getConfigValue("res.mon.intf.docker.url");
    if( url == null )
    {
      logger.warn("Docker URL was not defined using default");
      url = DockerResourceMonitorImpl.DEFAULT_DOCKER_HOST;
      this.config.put("docker.url", url);
    }
    this.docker = new DefaultDockerClient(url);
    
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
    List<String> launched = new ArrayList<>();;
  
    try
    {
      int min = imgCfg.getMinReq();
      int max = imgCfg.getMaxReq();
      if( min == 0 )
        min = 1;
     
      if( max == 0 )
        max = 1;
  
      // if the session id is not assigned, then use the node type
      String session_id = imgCfg.getSessionId();
      if( session_id == null )
        imgCfg.setSessionId(imgCfg.getNodeTypeAsString());
      
      String type = imgCfg.getNodeTypeAsString();
      
      logger.info("Starting VM of type " + type + " for session " + session_id ) ;
      // Setting all the environment variables
      List<String> envs = new ArrayList<>();
      
      String url = CcdpUtils.getConfigValue("res.mon.intf.docker.url");
      logger.info("Connecting to docker enging at: " + url);
      
      envs.add("DOCKER_HOST=" + url );
      envs.add("CCDP_HOME=/data/ccdp/ccdp-engine");
      // Add AWS specific ones if we are using AWS S3 Bucket
      if( !this.use_fs )
      {
        envs.add("AWS_DEFAULT_REGION=" + this.config.get("aws.region").asText());
        envs.add("AWS_SECRET_ACCESS_KEY=" + this.config.get("aws.secret.key").asText());
        envs.add("AWS_ACCESS_KEY_ID=" + this.config.get("aws.access.id").asText());
      }
      
      // Parsing the command to start the docker container
      // It should look like:
      //    AWS: /data/ccdp/ccdp_install.py -a download -d s3://ccdp-settings/ccdp-engine.tgz -t /data/ccdp -D -n DOCKER
      //    FS:  /data/ccdp/ccdp_install.py -t /data/ccdp -D -n DOCKER
      //
      
      List<String> cmd = new ArrayList<>();
      String cmd_line = imgCfg.getStartupCommand();
      StringTokenizer st = new StringTokenizer(cmd_line,  " ");
      while( st.hasMoreTokens() )
        cmd.add(st.nextToken());
  
      HostConfig hostCfg = HostConfig.builder()
          .networkMode("host")
          .build();
      // Now starting as many containers as asked
      //for(int i = 0; i < max; i++ )
      //{
        ContainerConfig cfg = ContainerConfig.builder()
            .env(envs)
            .hostConfig(hostCfg)
            .image(imgCfg.getImageId())
            .entrypoint(cmd)
            .build();
        ContainerCreation cc = this.docker.createContainer(cfg);
        String cid = cc.id();
        // Translating from Container id to a hostId
        String hostId = cid.substring(0,  12);
        
        logger.debug("Created Container " + hostId );
        launched.add(hostId);
        
        // we are copying the file to the container here
        if( this.use_fs )
        {
          String filename = config.get("dist.file").asText();
          FileInputStream fis = new FileInputStream(filename);
          this.docker.copyToContainer(fis, cid, "/data/ccdp");
        }
        
        this.docker.startContainer( cid );
      //}
      
      logger.info("May want to add tags as well???");
      //Map<String, String> tags = imgCfg.getTags();
    }
    catch( Exception e )
    {
      logger.error("Message: " + e.getMessage(), e);
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
    try
    {
      for( String cid : instIDs )
      {
        logger.info("Stopping " + cid);
        this.docker.stopContainer(cid, 1);
      }
      stopped = true;
    }
    catch( Exception e )
    {
      logger.error("Message: " + e.getMessage(), e);
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
    boolean removed = false;
    try
    {
      for( String cid : instIDs )
      {
        logger.info("Removing " + cid);
        // making sure it does not fail because is still running
        try
        {
          this.docker.stopContainer(cid, 1);
        }
        catch( Exception e )
        {
          
        }
        this.docker.removeContainer(cid);
      }
      removed = true;
    }
    catch( Exception e )
    {
      logger.error("Message: " + e.getMessage(), e);
    }
    return removed;
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

    try
    {
      List<Container> containers = 
            this.docker.listContainers(ListContainersParam.allContainers());
      
      for( Container container : containers )
      {
        String cid = container.id().substring(0,  12);
        
        String state = container.state();
        logger.debug("Setting the state " + state + " to " + cid);
        CcdpVMResource res = new CcdpVMResource(cid);
        
        switch( state )
        {
          case "created":
            res.setStatus(ResourceStatus.INITIALIZING);
            break;
          case "running":
            res.setStatus(ResourceStatus.RUNNING);
            break;
          case "exited":
            res.setStatus(ResourceStatus.STOPPED);
            break;
          default:
            logger.warn("Got unknown state " + state );
          
        }  
        resources.put(cid, res);
      }
    }
    catch (Exception e )
    {
      logger.error("Message: " + e.getMessage(), e);
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
    ResourceStatus status = null;
    
    try
    {
      List<Container> containers = 
            this.docker.listContainers(ListContainersParam.allContainers());
      
      for( Container container : containers )
      {
        String cid = container.id().substring(0,  12);
        if( cid != null && cid.equals(id) )
        {
          logger.trace("Found Container");
          String state = container.state();
          switch( state )
          {
            case "created":
              status = ResourceStatus.INITIALIZING;
              break;
            case "running":
              status = ResourceStatus.RUNNING;
              break;
            case "exited":
              status = ResourceStatus.STOPPED;
              break;
            default:
              logger.warn("Got unknown state " + state );
          }
          break;
        }// end of the comparisson
      }// end of for loop
    }
    catch (Exception e )
    {
      logger.error("Message: " + e.getMessage(), e);
    }
    
    return status;
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
    this.logger.warn("Not currently implemented as does not have tags yet");
    return this.getAllInstanceStatus();
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
    logger.warn("Not implemented yet as I am not even ser we use it");
    
    return null;
  }

}
