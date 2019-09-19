/*
 * @author Scott Bennett, scott.bennett@caci.com
 * 
 * This class is a generic task runner used in CCDP serverless tasking.
 * It takes in a string on commands and uses Java's process builder to get
 * a command and runs it. It then returns the command to the controller who called it
 */

package com.axios.ccdp.utils;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Map;

import org.apache.log4j.Logger;

import com.axios.ccdp.impl.controllers.CcdpServerlessControllerAbs;
import com.axios.ccdp.tasking.CcdpTaskRequest;
import com.axios.ccdp.tasking.CcdpTaskRequest.CcdpTaskState;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

public class ServerlessTaskRunner implements Runnable
{
  /*
   * Object mapper to map strings to json
   */
  ObjectMapper mapper = new ObjectMapper();
  /*
   * A string array for the command to run, the int index where the incoming command
   * goes and a string for the result.
   */
  private String[] cmd = {"/bin/bash", "-c", ""};
  private final int runCmdIndex = 2;
  private String result = "";
  /*
   * The controller that created this runnable
   */
  private CcdpServerlessControllerAbs controller = null;
  private CcdpTaskRequest task = null;
  /**
   * Generates debug print statements based on the verbosity level.
   */
  private Logger logger = Logger.getLogger(ServerlessTaskRunner.class.getName());

  public ServerlessTaskRunner(String runCmd, CcdpTaskRequest task, CcdpServerlessControllerAbs controller)
  {
    this.logger.debug("New TaskRunner Runnable created");
    this.cmd[runCmdIndex] = runCmd;
    this.controller = controller;
    this.task = task;
  }

  @Override
  public void run()
  {
    Map <String, String> resultMap = new HashMap<>();
    try
    {
      // Build the process with the command and start it
      ProcessBuilder pb = new ProcessBuilder(cmd);
      Process proc = pb.start();
  
      // Read the output and errors
      BufferedReader reader =  
            new BufferedReader(new InputStreamReader(proc.getInputStream()));
  
      // Get the output result of the Lambda Function
      String line = null;
      while((line = reader.readLine()) != null) 
          result = result + line;
      
  
      // Wait for the process to finish execution
      proc.waitFor();
      resultMap.put("result", result);
    }
    catch ( Exception e)
    {
      task.setState(CcdpTaskState.FAILED);
      resultMap.put("status", CcdpTaskState.FAILED.name());
      resultMap.put("stacktrace", e.toString());
      this.logger.debug("Exception thrown from process building");
      e.printStackTrace();
    }
    
    logger.debug("Result of TaskRunner: " + result);
    if ( !resultMap.containsKey("status") )
      resultMap.put("status", CcdpTaskState.SUCCESSFUL.name());
    task.setState(CcdpTaskState.SUCCESSFUL);
    JsonNode resultJson = mapper.valueToTree(resultMap);
    this.controller.completeTask(this.task, resultJson);
  }
}
