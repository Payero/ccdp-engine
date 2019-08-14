package com.axios.ccdp.impl.cloud.aws;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.log4j.Logger;

import com.axios.ccdp.tasking.CcdpTaskRequest;

public class LambdaController
{

  Map<String, String> serverCfg = new HashMap<String, String>();
  List<String> taskArgs = new ArrayList<>();
  
  String[] exec_command = {"/bin/bash", "-c", ""};
  String command = "curl -X POST -d ";
  String post_command = "\"{";
  String result = "";
  String errors = "";
  /**
   * Generates debug print statements based on the verbosity level.
   */
  private Logger logger = Logger.getLogger(LambdaController.class.getName());

  public LambdaController(CcdpTaskRequest LambdaTask)
  {
    serverCfg = LambdaTask.getServerlessCfg();
    taskArgs = LambdaTask.getServerArgs();
    
    this.logger.debug("Server Cfg: " + serverCfg.toString());
    this.logger.debug("Task Arguments: " + taskArgs.toString());
    
    post_command = post_command + "\\\"arguments\\\": \\\"" + String.join(", ", taskArgs) + "\\\"";
    for ( String key : serverCfg.keySet() )
    {
      if ( key.equals("provider") || key.equals("URL-Gateway") )
        continue;
      post_command = post_command + ",\\\"" + key + "\\\": \\\"" + serverCfg.get(key) + "\\\"";
    }
    post_command = post_command + "}\" " + serverCfg.get("URL-Gateway");
    this.logger.debug("The curl cmd: " + command + post_command);
    exec_command[2] = command + post_command;
    
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
