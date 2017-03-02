package com.axios.ccdp.mesos.test;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.StringJoiner;

import org.apache.log4j.Logger;

import com.axios.ccdp.mesos.fmwk.CcdpJob;
import com.axios.ccdp.mesos.tasking.CcdpTaskRequest;
import com.axios.ccdp.mesos.tasking.CcdpThreadRequest;
import com.axios.ccdp.mesos.utils.CcdpUtils;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;


public class CCDPTest
{

  /**
   * Generates debug print statements based on the verbosity level.
   */
  private Logger logger = Logger.getLogger(CCDPTest.class.getName());

  
  public CCDPTest() throws Exception
  {
    this.logger.debug("Running CCDP Test");
    String fname = "/home/oeg/dev/oeg/CCDP/data/job.json";
    File file = new File(fname);
    if( file.isFile() )
    {
      byte[] data = Files.readAllBytes( Paths.get( fname ) );
      JsonNode node = new ObjectMapper().readTree( data );
      if( node.has("jobs") )
      {
        ArrayNode jobs_node = (ArrayNode)node.get("jobs");
        for( JsonNode job : jobs_node )
        {
          this.logger.debug("Adding Job: " + job.toString());
          ArrayNode args = (ArrayNode)job.get("command");
          List<String> list = new ArrayList<String>();
          for( JsonNode arg : args )
          {
            String cmd = arg.asText();
            list.add(cmd);
            this.logger.debug("\tThe Argument " + cmd);
          }
          StringJoiner joiner = new StringJoiner(" ");
          list.forEach(joiner::add);
          
          String command = joiner.toString();
          this.logger.debug("The generated command " + command);
          
          
          CcdpJob ccdp_job = CcdpJob.fromJSON(job);
          this.logger.debug("CPU: " + ccdp_job.getCpus());
          this.logger.debug("MEM: " + ccdp_job.getMemory());
          this.logger.debug("ID: " + ccdp_job.getId());
          this.logger.debug("Command: " + ccdp_job.getCommand());
        }
      }
    }
  }  
  

  
  public static void main(String[] args) throws Exception
  {
    CcdpUtils.loadProperties(System.getProperty(CcdpUtils.CFG_KEY_CFG_FILE));
    CcdpUtils.configLogger();
    
    new CCDPTest();
  }
}


