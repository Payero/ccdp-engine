package com.axios.ccdp.impl.cloud.aws;

import java.io.File;

import org.apache.log4j.Logger;

import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.EnvironmentVariableCredentialsProvider;
import com.amazonaws.auth.InstanceProfileCredentialsProvider;
import com.amazonaws.auth.SystemPropertiesCredentialsProvider;
import com.amazonaws.auth.profile.ProfileCredentialsProvider;
import com.amazonaws.auth.profile.ProfilesConfigFile;
import com.axios.ccdp.utils.CcdpUtils;

public class AWSUtils
{

  /**
   * Generates debug print statements based on the verbosity level.
   */
  private static Logger logger = Logger.getLogger(AWSUtils.class.getName());

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
    
    //System.out.println("credsFile, profile: " + credsFile + ", " + profile);
    
    AWSCredentials credentials = null;
    
    // Attempting all different ways 
    if( System.getenv(CcdpUtils.ACCESS_KEY_ID_ENV_VAR) != null && 
        System.getenv(CcdpUtils.ACCESS_SECRET_ENV_VAR) != null )
    {
      logger.info("Setting Credentials using Environment Variables");
      credentials = 
          new EnvironmentVariableCredentialsProvider().getCredentials();
    }
    else if( System.getProperty(CcdpUtils.ACCESS_KEY_ID_PROPERTY) != null &&
             System.getProperty(CcdpUtils.ACCESS_SECRET_PROPERTY) != null)
    {
      logger.info("Setting Credentials using System Properties");
      credentials = 
          new SystemPropertiesCredentialsProvider().getCredentials();
    }
    else if( credsFile != null )
    {
      logger.info("Setting Credentials using JSON Configuration");
      credentials = AWSUtils.getCredentials(credsFile, profile);
    }
    else if( System.getenv("HOME") != null )
    {
      logger.info("Setting Credentials using default");
      String fname = System.getenv("HOME") + "/.aws/credentials";
      File file = new File(fname);
      if( file.isFile() )
      {
        credentials = AWSUtils.getCredentials(fname, profile);
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
      logger.debug(cfgFile.toString() + ", " + profile);
      creds = 
          new ProfileCredentialsProvider( cfgFile, profile ).getCredentials();
      logger.debug(creds.toString());
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

