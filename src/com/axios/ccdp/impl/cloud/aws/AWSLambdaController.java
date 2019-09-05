/*
 * @author Scott Bennett
 * 
 * An AWS Lambda Controller Implementation that creates 
 * Lambda requests and send them to a webhook to perform a
 * task
 */
package com.axios.ccdp.impl.cloud.aws;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.log4j.Logger;

import com.amazonaws.ClientConfiguration;
import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3Client;
import com.axios.ccdp.impl.cloud.aws.AWSUtils;
import com.axios.ccdp.impl.controllers.CcdpServerlessControllerAbs;
import com.axios.ccdp.tasking.CcdpTaskRequest;
import com.axios.ccdp.tasking.CcdpTaskRequest.CcdpTaskState;
import com.axios.ccdp.utils.CcdpUtils;
import com.axios.ccdp.utils.ServerlessTaskRunner;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

public class AWSLambdaController extends CcdpServerlessControllerAbs
{    
  /*
   * Object mapper to map strings to json
   */
  ObjectMapper mapper = new ObjectMapper();
  /*
   * Strings for the curl command
   */
  private final String curlCmd = "curl -X POST -d ";
  /**
   * Generates debug print statements based on the verbosity level.
   */
  private Logger logger = Logger.getLogger(AWSLambdaController.class.getName());
  
  /*
   * Beginning string for S3 uploads
   */
  private final String s3_proto = "s3://";
  private final String AWS = "AWS";
  private final String controller_name = "AWS Lambda";

  public AWSLambdaController()
  {
    this.logger.debug("New Lambda Controller Object");
  }

  @Override
  public void runTask(CcdpTaskRequest task)
  {
    this.logger.debug("New task received: \n" + task.toPrettyPrint());
    this.controllerInfo.addTask(task);
    task.setState(CcdpTaskState.STAGING);
    this.connection.sendTaskUpdate(toMain, task);
    
    //Create the command
    String curlData = this.generateCurl(task);
    this.logger.debug("Curl cmd: " + curlCmd + curlData);
    
    // Create a new thread for the lambda runner
    Thread t = new Thread(new ServerlessTaskRunner(curlCmd + curlData, task, this));
    this.logger.debug("Thread configured, starting thread");
    task.setState(CcdpTaskState.RUNNING);
    this.connection.sendTaskUpdate(toMain, task);
    t.start();
  }
  
  @Override
  public void onEvent()
  {
    this.dbClient.storeServerlessInformation(this.controllerInfo);  
  }
  
  /*
   * Generates the curl data parameter of the curl command that will be used in
   * the post request
   * 
   * @param task The task that the request is being created for
   * 
   * @return the data for the post command
   */
  private String generateCurl( CcdpTaskRequest task ) 
  {
    Map<String, String> serverCfg = task.getServerlessCfg();
    List<String> taskArgs = task.getServerArgs();
    
    this.logger.debug("Server Cfg: " + serverCfg.toString());
    this.logger.debug("Task Arguments: " + taskArgs.toString());
    
    // Create a map and add all the needed data fields to the map
    Map<String, String> dataMap = new HashMap<>();
    dataMap.put("arguments", String.join(" ", taskArgs));
    for ( String key : serverCfg.keySet() )
    {
      if ( key.equals(CcdpUtils.S_CFG_PROVIDER) || key.equals(CcdpUtils.S_CFG_GATEWAY) || key.equals(CcdpUtils.S_CFG_REMOTE_FILE) || key.equals(CcdpUtils.S_CFG_LOCAL_FILE) )
        continue;
      dataMap.put(key, serverCfg.get(key));
    }
    
    // Turn the map into a JsonNode, change it to a string an format it for AWS
    JsonNode dataNode = mapper.valueToTree(dataMap);
    String curlData = "\"" + dataNode.toString().replace("\"", "\\\"") + "\" " + serverCfg.get(CcdpUtils.S_CFG_GATEWAY);
    this.logger.debug("The AWS Lambda Request: " + curlData);
    return curlData;
  }

  /*
   * Saves the result of the task to Amazon S3 using credentials
   * 
   * @param result The result of the task in JsonNode format
   * @param location The remote location to store the result
   * @param cont_name The name of the controller that 
   */
  public void remoteSave(JsonNode result, String location)
  {
    this.logger.debug("Uploading AWS Lambda result to S3 at: " + location);
    
    try
    {
   // Remove appended s3 proto if it was there
      if ( location.contains(s3_proto) )
        location = location.substring(s3_proto.length());
      this.logger.debug("location: " + location);
      
      // Get the bucket name and separate it from the folders/fname
      Path tgt_loc = Paths.get(location);
      String bucket = tgt_loc.subpath(0, 1).toString();
      location = location.substring(bucket.length() + 1);
      this.logger.debug("Bucket: " + bucket + ", location: " + location);    
      
      // Get AWS Credentials and build a S3 client
      JsonNode AWSCreds = CcdpUtils.getCredentials().get(AWS);
      if ( AWSCreds == null )
      {
        this.logger.error("AWS Credentials were not found, aborting remote save");
        return;
      }
      AWSCredentials creds = AWSUtils.getAWSCredentials(
          AWSCreds.get(CcdpUtils.CFG_KEY_CREDENTIALS_FILE).asText(), 
          AWSCreds.get(CcdpUtils.CFG_KEY_CREDENTIALS_PROFILE).asText());
      
      // Create the S3 Client and do upload
      AmazonS3 s3 = new AmazonS3Client(creds, new ClientConfiguration());
      
      if ( !s3.doesBucketExist(bucket) )
      {
        this.logger.debug("Bucket \"" + bucket + "\" doesn't exist, creating it");
        s3.createBucket(bucket);
      }
      
      String existingObj = "";
      if (s3.doesObjectExist(bucket, location))
        existingObj = s3.getObjectAsString(bucket, location);
      
      DateTimeFormatter dtf = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");
      LocalDateTime now = LocalDateTime.now();
      s3.putObject(bucket, location, existingObj + "\n" + controller_name + " Result from " + 
          dtf.format(now) +"\n" + result.toString() + "\n");
      
      this.logger.debug("File uploaded to S3."); 
    }
    catch (Exception e)
    {
      this.logger.debug("Exception thrown while uploading to S3");
      e.printStackTrace();
    }
     
  }

  @Override
  public void handleResult(JsonNode result, CcdpTaskRequest task)
  {
    
    String localSaveLoc = task.getServerlessCfg().get(CcdpUtils.S_CFG_LOCAL_FILE);
    String remoteSaveLoc = task.getServerlessCfg().get(CcdpUtils.S_CFG_REMOTE_FILE);
    
    
    if (localSaveLoc != null)
      this.localSave(result, localSaveLoc, controller_name);
    else
      this.logger.debug("Opted out of local storage");
    
    if (remoteSaveLoc != null)
      this.remoteSave(result, remoteSaveLoc);
    else
      this.logger.debug("Opted out of remote storage");
  }
}