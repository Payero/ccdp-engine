package com.axios.ccdp.factory;

import java.io.File;

import org.apache.log4j.Logger;

import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.EnvironmentVariableCredentialsProvider;
import com.amazonaws.auth.SystemPropertiesCredentialsProvider;
import com.amazonaws.auth.profile.ProfileCredentialsProvider;
import com.amazonaws.auth.profile.ProfilesConfigFile;
import com.axios.ccdp.connections.amq.AMQCcdpTaskingImpl;
import com.axios.ccdp.connections.intfs.CcdpObjectFactoryAbs;
import com.axios.ccdp.connections.intfs.CcdpStorageControllerIntf;
import com.axios.ccdp.connections.intfs.CcdpTaskingControllerIntf;
import com.axios.ccdp.connections.intfs.CcdpTaskingIntf;
import com.axios.ccdp.connections.intfs.CcdpVMControllerIntf;
import com.axios.ccdp.controllers.aws.AWSCcdpStorageControllerImpl;
import com.axios.ccdp.controllers.aws.AWSCcdpTaskingControllerImpl;
import com.axios.ccdp.controllers.aws.AWSCcdpVMControllerImpl;
import com.axios.ccdp.newgen.AmqCcdpConnectionImpl;
import com.axios.ccdp.newgen.CcdpConnectionIntf;
import com.fasterxml.jackson.databind.node.ObjectNode;

public class AWSCcdpFactoryImpl extends CcdpObjectFactoryAbs
{
  /** Stores the name of the ACCESS KEY Environment Variable **/
  public static final String ACCESS_KEY_ID_ENV_VAR = "AWS_ACCESS_KEY_ID";
  /** Stores the name of the ACCESS SECRET Environment Variable **/
  public static final String ACCESS_SECRET_ENV_VAR = "AWS_SECRET_ACCESS_KEY";
  /** Stores the name of the ACCESS KEY System Property **/
  public static final String ACCESS_KEY_ID_PROPERTY = "aws.accessKeyId";
  /** Stores the name of the ACCESS SECRET System Property **/
  public static final String ACCESS_SECRET_PROPERTY = "aws.secretKey";
  
  /** The name of the files with access keys */
  public static final String FLD_CREDS_FILE   = "credentials-file";
  /** The profile to use in the given file */
  public static final String FLD_PROFILE_NAME = "profile-name";
  
   /**
   * Generates debug print statements based on the verbosity level.
   */
  private static Logger logger = 
            Logger.getLogger(AWSCcdpFactoryImpl.class.getName());
  /**
   * Limiting access to this constructor is intentional in order to enforce the
   * use of a single factory object
   */
  public AWSCcdpFactoryImpl()
  {

  }

  /**
   * Gets the object that is used communicate among elements in the sustem.  
   * The same interface is also used to send messages back to a specific 
   * destination
   * 
   * @param config a JSON Object containing required configuration parameters
   * @return an actual implementation of the object that allows the main 
   *         application to send and receive tasking events
   */
  @Override
  public CcdpConnectionIntf getCcdpConnectionInterface(ObjectNode config)
  {
    CcdpConnectionIntf tasking = new AmqCcdpConnectionImpl();
    tasking.configure(config);
    return tasking;
  }
  
  
  /**
   * Gets the object that is used to task the scheduler.  The same interface
   * is also used to send messages back to a specific destination
   * 
   * @return an actual implementation of the object that allows the scheduler
   *         to send and receive tasking events
   */
  @Override
  public CcdpTaskingIntf getCcdpTaskingInterface(ObjectNode config)
  {
    CcdpTaskingIntf tasking = new AMQCcdpTaskingImpl();
    tasking.configure(config);
    return tasking;
  }

  /**
   * Gets the object responsible for controlling the resources.  For instance, 
   * it starts and stops VMs, ask for status, etc.
   * 
   * @param config a JSON Object containing required configuration parameters
   * 
   * @return an actual implementation of the object that allows the scheduler
   *         to manipulate the resources
   */
  @Override
  public CcdpVMControllerIntf getCcdpResourceController(ObjectNode config)
  {
    CcdpVMControllerIntf aws = new AWSCcdpVMControllerImpl();
    aws.configure(config);
    return aws;
  }
  
  /**
   * Gets the object responsible for controlling the storage of objects.  For
   * instance, it creates and deletes directories and files in a file system
   * implementation.  It can also retrieve the contents of a file or an object
   * stored in a S3 bucket
   * 
   * @param config a JSON Object containing required configuration parameters
   * @return an actual implementation of the object that allows the scheduler
   *         to manipulate the storage resources
   */
  @Override
  public CcdpStorageControllerIntf getCcdpStorageControllerIntf(ObjectNode config)
  {
    CcdpStorageControllerIntf aws = new AWSCcdpStorageControllerImpl();
    aws.configure(config);
    return null;
  }
  
  /**
   * Gets the object responsible for tasking the resources.  For instance, 
   * it will start a task based on a session-id, capacity, etc
   * 
   * @param config a JSON Object containing required configuration parameters
   * 
   * @return an actual implementation of the object that allows the scheduler
   *         to manipulate the tasking
   */
  @Override
  public CcdpTaskingControllerIntf getCcdpTaskingController(ObjectNode config)
  {
    CcdpTaskingControllerIntf aws = new AWSCcdpTaskingControllerImpl();
    aws.configure(config);
    return aws;
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
   * @return an authentication object using the given parameters
   * 
   * @throws IllegalArgumentException an IllegalArgumentException is thrown if
   *         it cannot find at least one of the different methods to 
   *         authenticate the user
   */
  public static AWSCredentials getAWSCredentials( ObjectNode config )
  {
    logger.debug("Configuring ResourceController using: " + config);
    // the configuration is required
    if( config == null )
      throw new IllegalArgumentException("The config cannot be null");
    
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
    else if( config.has(FLD_CREDS_FILE) &&  
             config.has(FLD_PROFILE_NAME))
    {
      logger.info("Setting Credentials using JSON Configuration");
      String fname = config.asText(FLD_CREDS_FILE);
      String profile = config.asText(FLD_PROFILE_NAME);
      credentials = AWSCcdpFactoryImpl.getCredentials(fname, profile);
    }
    else if( System.getenv("HOME") != null )
    {
      logger.info("Setting Credentials using default");
      String fname = System.getenv("HOME") + "/.aws/credentials";
      String profile = "default";
      credentials = AWSCcdpFactoryImpl.getCredentials(fname, profile);
    }
    else
    {
      String txt = "Was not able to find any of the different ways to "
          + "authenticate.  At least one method needs to be available";
      throw new IllegalArgumentException(txt);
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
      String txt = "The profiles file (" + fname + ") is invalid.  Please set"
          + " the credentials-file field properly";
      throw new IllegalArgumentException(txt);
    }
    
    return creds;
  }
  
}
