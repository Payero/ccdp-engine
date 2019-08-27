/*
 * Scott Bennett, scott.bennett@caci.com
 * This class runs acts as an "Agent" for lambda tasks, spawnning when
 * called to run tasks, and despawning when it is no longer needed
 */

package com.axios.ccdp.impl.cloud.aws;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.InputStreamReader;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.log4j.Logger;

import com.axios.ccdp.impl.controllers.CcdpServerlessControllerAbs;
import com.axios.ccdp.tasking.CcdpTaskRequest;
import com.axios.ccdp.tasking.CcdpTaskRequest.CcdpTaskState;
import com.axios.ccdp.utils.CcdpUtils;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

public class AWSLambdaTaskRunner implements Runnable
{

  /*
   * The AWS Controller object that created this thread
   */
  private CcdpServerlessControllerAbs controller = null;
  /**
   * Generates debug print statements based on the verbosity level.
   */
  private Logger logger = Logger.getLogger(AWSLambdaTaskRunner.class.getName());
  /*
   * Object for holding the server configuration
   */
  Map<String, String> serverCfg = new HashMap<String, String>();
  
  /*
   * List for holding all the arguments of the task
   */
  List<String> taskArgs = new ArrayList<>();
  /*
   * Object mapper to map strings to json
   */
  ObjectMapper mapper = new ObjectMapper();
  /*
   * Index to add the command to, just making sure it isn't hard coded
   */
  final int cmd_index = 2;
  
  String[] exec_command = {"/bin/bash", "-c", ""};
  String command = "curl -X POST -d ";
  String post_command = "\"{";
  String result = "";
  String localFileLocation;
  
  /*
   * Task used in generating this runnable
   */
  private CcdpTaskRequest running_task = null;

  /*
   * A new Lambda Task Runner is created and configured using the task request passed
   * to the constructor
   * 
   * @param task A CcdpTaskRequest object containing information about the serverless task
   * 
   * @param task_controller The controller that created this Runner instance.
   */
  public AWSLambdaTaskRunner(CcdpTaskRequest task, CcdpServerlessControllerAbs task_controller)
  {
    this.logger.debug("New AWS Lambda Runnable constructed.");
    
    this.controller = task_controller;
    serverCfg = task.getServerlessCfg();
    taskArgs = task.getServerArgs();
    
    this.logger.debug("Server Cfg: " + serverCfg.toString());
    this.logger.debug("Task Arguments: " + taskArgs.toString());
    
    post_command = post_command + "\\\"arguments\\\": \\\"" + String.join(", ", taskArgs) + "\\\"";
    for ( String key : serverCfg.keySet() )
    {
      if ( key.equals(CcdpUtils.S_CFG_PROVIDER) || key.equals(CcdpUtils.S_CFG_GATEWAY) )
        continue;
      post_command = post_command + ",\\\"" + key + "\\\": \\\"" + serverCfg.get(key) + "\\\"";
    }
    post_command = post_command + "}\" " + serverCfg.get(CcdpUtils.S_CFG_GATEWAY);
    this.logger.debug("The curl cmd: " + command + post_command);
    exec_command[cmd_index] = command + post_command;
    
    localFileLocation = serverCfg.get(CcdpUtils.S_CFG_LOCAL_FILE);
    
    running_task = task;
  }
  
  /*
   * (non-Javadoc)
   * @see java.lang.Runnable#run()
   * 
   * Runs the thread that executes the AWS Lambda Task
   */
  @Override
  public void run()
  {    
    try
    {
      // Build the process with the command and start it
      ProcessBuilder pb = new ProcessBuilder(exec_command);
      Process proc = pb.start();
  
      // Read the output and errors
      BufferedReader reader =  
            new BufferedReader(new InputStreamReader(proc.getInputStream()));
  
      // Get the output result of the Lambda Function
      String line = null;
      while((line = reader.readLine()) != null) 
          result = result + line + "\n";
      
  
      // Wait for the process to finish execution
      proc.waitFor();
    }
    catch ( Exception e)
    {
      this.logger.debug("Exception in posting curl");
      e.printStackTrace();
    }
    
    logger.debug("Result of the Lambda Function: " + result);
    logger.debug("Done with Lambda Function");
    
    // Try to convert the result to Json
    try 
    {
      JsonNode resultJson = this.mapper.readTree(result);
      // Check if the task failed by return fields
      if (resultJson.has("stackTrace") && resultJson.has("errorType"))
      {
        this.logger.debug("The task failed, setting failed");
        running_task.setState(CcdpTaskState.FAILED);
        this.controller.completeTask(running_task);
      }
    }
    catch ( Exception e )
    {
      this.logger.debug("Error translating string to Json, failing task");
      e.printStackTrace();
      running_task.setState(CcdpTaskState.FAILED);
      this.controller.completeTask(running_task);
    }
    
    // Save file locally
    if ( localFileLocation != null && !localFileLocation.equals("") )
    {
      this.logger.debug("Store file locally");       
      try
      {
        DateTimeFormatter dtf = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");
        LocalDateTime now = LocalDateTime.now();
        BufferedWriter out = new BufferedWriter( 
            new FileWriter(localFileLocation, true)); 
        out.write("\nLambda Result from " + dtf.format(now) +"\n" + result); 
        out.close();
      }
      catch ( Exception e )
      {
        logger.error("Exception caught while writing to output file");
        e.printStackTrace();
      }
    }
    else
      this.logger.debug("Opted out of local storage");
    
    running_task.setState(CcdpTaskState.SUCCESSFUL);
    controller.completeTask(running_task);
  }

}
