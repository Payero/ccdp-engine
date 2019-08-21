/*
 * Scott Bennett, scott.bennett@caci.com
 * This class runs acts as an "Agent" for lambda tasks, spawnning when
 * called to run tasks, and despawning when it is no longer needed
 */

package com.axios.ccdp.impl.cloud.aws;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.log4j.Logger;

import com.axios.ccdp.factory.CcdpObjectFactory;
import com.axios.ccdp.impl.controllers.CcdpServerlessControllerAbs;
import com.axios.ccdp.intfs.CcdpConnectionIntf;
import com.axios.ccdp.intfs.CcdpMessageConsumerIntf;
import com.axios.ccdp.messages.AssignSessionMessage;
import com.axios.ccdp.messages.CcdpMessage;
import com.axios.ccdp.messages.KillTaskMessage;
import com.axios.ccdp.messages.RunTaskMessage;
import com.axios.ccdp.messages.ShutdownMessage;
import com.axios.ccdp.messages.ThreadRequestMessage;
import com.axios.ccdp.messages.CcdpMessage.CcdpMessageType;
import com.axios.ccdp.tasking.CcdpTaskRequest;
import com.axios.ccdp.utils.CcdpUtils;
import com.fasterxml.jackson.databind.JsonNode;

public class LambdaTaskRunner implements Runnable
{

  /*
   * The AWS Controller object that created this thread
   */
  private CcdpServerlessControllerAbs controller = null;
  /**
   * Generates debug print statements based on the verbosity level.
   */
  private Logger logger = Logger.getLogger(LambdaTaskRunner.class.getName());
  /*
   * Object for holding the server configuration
   */
  Map<String, String> serverCfg = new HashMap<String, String>();
  
  /*
   * List for holding all the arguments of the task
   */
  List<String> taskArgs = new ArrayList<>();
  /*
   * Index to add the command to, just making sure it isn't hard coded
   */
  final int cmd_index = 2;
  
  String[] exec_command = {"/bin/bash", "-c", ""};
  String command = "curl -X POST -d ";
  String post_command = "\"{";
  String result = "";
  String errors = "";

  public LambdaTaskRunner(CcdpTaskRequest task, CcdpServerlessControllerAbs task_controller)
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
      BufferedReader ereader =  
          new BufferedReader(new InputStreamReader(proc.getErrorStream()));
  
      // Get the output result of the Lambda Function
      String line = null;
      while((line = reader.readLine()) != null) {
          result = result + line + "\n";
      }
      
      // Get the error output of the Lambda Function
      String eline = null;
      while((eline = ereader.readLine()) != null) {
          errors = errors + eline + "\n";
      }
  
      // Wait for the process to finish execution
      proc.waitFor();
    }
    catch ( Exception e)
    {
      this.logger.debug("Exception in posting curl");
      e.printStackTrace();
    }
    
    logger.info("Result of the Lambda Function: " + result);
    logger.debug("Errors from Lambda Execution: \n" + errors);
    logger.debug("Done with Lambda Function");
    
  }

}
