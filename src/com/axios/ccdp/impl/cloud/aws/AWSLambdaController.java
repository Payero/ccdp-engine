/*
 * @author Scott Bennett
 * 
 * An AWS Lambda Controller Implementation that creates 
 * Lambda requests and send them to a webhook to perform a
 * task
 */
package com.axios.ccdp.impl.cloud.aws;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.log4j.Logger;

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
      if ( key.equals(CcdpUtils.S_CFG_PROVIDER) || key.equals(CcdpUtils.S_CFG_GATEWAY) )
        continue;
      dataMap.put(key, serverCfg.get(key));
    }
    
    // Turn the map into a JsonNode, change it to a string an format it for AWS
    JsonNode dataNode = mapper.valueToTree(dataMap);
    String curlData = "\"" + dataNode.toString().replace("\"", "\\\"") + "\" " + serverCfg.get(CcdpUtils.S_CFG_GATEWAY);
    this.logger.debug("The AWS Lambda Request: " + curlData);
    return curlData;
  }
}
