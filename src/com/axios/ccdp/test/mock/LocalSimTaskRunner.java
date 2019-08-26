package com.axios.ccdp.test.mock;

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

public class LocalSimTaskRunner implements Runnable
{

  private CcdpTaskRequest running_task = null;
  private CcdpServerlessControllerAbs controller = null;
  Map<String, String> serverCfg = new HashMap<String, String>(); 
  List<String> taskArgs = new ArrayList<>();

  /*
   * Object mapper to map strings to json
   */
  ObjectMapper mapper = new ObjectMapper();
  /**
   * Generates debug print statements based on the verbosity level.
   */
  private Logger logger = Logger.getLogger(LocalSimTaskRunner.class.getName());
  
  final int cmd_index = 2;
  String[] exec_command = {"/bin/bash", "-c", ""};
  String result = "";
  String localFileLocation;

  public LocalSimTaskRunner(CcdpTaskRequest task, CcdpServerlessControllerAbs task_controller)
  {
    this.logger.debug("New LocalSimTaskRunner created");
    this.running_task = task;
    this.controller = task_controller;
    exec_command[cmd_index] = "echo \"{\\\"result\\\": \\\"" + String.join(" ", task.getServerArgs()) + "\\\"}\"";
    
    serverCfg = task.getServerlessCfg();
    localFileLocation = serverCfg.get(CcdpUtils.S_CFG_LOCAL_FILE);
  }

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
      this.logger.debug("Exception in running cmd");
      e.printStackTrace();
    }
    
    logger.debug("Result of the LocalSim Function: " + result);
    logger.debug("Done with LocalSim Function");
    
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
