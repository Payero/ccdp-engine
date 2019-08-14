package com.axios.ccdp.test;

import java.io.*;

import org.apache.log4j.Logger;

import com.axios.ccdp.utils.CcdpUtils;



public class CCDPTest 
{
  /**
   * Generates debug print statements based on the verbosity level.
   */
  private Logger logger = Logger.getLogger(CCDPTest.class.getName());
  
  
  public CCDPTest() throws Exception
  {
    String[] cmd = {"/bin/bash", "-c", "curl -X POST -d \"{\\\"arguments\\\": \\\"1000000\\\",\\\"bkt_name\\\": \\\"ccdp-tasks\\\",\\\"keep_files\\\": \\\"False\\\",\\\"mod_name\\\": \\\"simple_pi\\\",\\\"verb_level\\\": \\\"debug\\\",\\\"res_file\\\": \\\"pi_out\\\",\\\"zip_file\\\": \\\"simple_pi.zip\\\"}\" https://cx62aa0x70.execute-api.us-east-1.amazonaws.com/prod/TaskRunnerManager"};
    System.out.println(cmd[0] + " " + cmd[1] + " " + cmd[2]);
    //Process proc = Runtime.getRuntime().exec(cmd);
    ProcessBuilder pb = new ProcessBuilder(cmd);
    Process proc = pb.start();
    // Read the output
    BufferedReader reader =  
          new BufferedReader(new InputStreamReader(proc.getInputStream()));
    BufferedReader ereader =  
        new BufferedReader(new InputStreamReader(proc.getErrorStream()));

    String line = null;
    System.out.println("\n\nOutput: ");
    while((line = reader.readLine()) != null) {
        System.out.print(line + "\n");
    }
    
    String eline = null;
    System.out.println("\n\nErrors: ");
    while((eline = ereader.readLine()) != null) {
        System.out.print(eline + "\n");
    }

    proc.waitFor();
    
    logger.debug("Done");
  }
  

  public static void main( String[] args ) throws Exception
  {
    String cfg_file = System.getProperty("ccdp.config.file");
        
    // Uses the cfg file to configure all CcdpUtils for use in the next service
    System.out.println("Before loadProperties");
    CcdpUtils.loadProperties(cfg_file);
    System.out.println("After Load properties");
    CcdpUtils.configLogger();
    
    new CCDPTest();
  }

}



