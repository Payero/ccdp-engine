package com.axios.ccdp.mesos.test.unittest;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Iterator;
import java.util.List;

import org.apache.log4j.Logger;
import org.junit.Test;

import com.axios.ccdp.mesos.tasking.CcdpThreadRequest;
import com.axios.ccdp.mesos.utils.CcdpUtils;

public class TaskingGeneratorUnitTest
{

  /**
   * Generates debug print statements based on the verbosity level.
   */
  private Logger logger = Logger.getLogger(TaskingGeneratorUnitTest.class
      .getName());

  public TaskingGeneratorUnitTest()
  {
    this.logger.debug("Testing Tasking Generator");
    
  }
  
  @Test
  public void testUseTask() 
  {
    this.logger.debug("Running a test using Tasks");
    String path = System.getenv("CCDP_HOME");
    if( path == null )
      path = System.getProperty("ccdp.home");
    String fname = path + "/data/multiple_tasks.json";
    this.logger.debug("The File " + fname);
  
    try
    {
    
      File file = new File(fname);
      if( file.isFile() )
      {
        this.logger.debug("Found The file");
        byte[] bytes = Files.readAllBytes( Paths.get( fname ) );
        String data = new String( bytes, "UTF-8");
        List<CcdpThreadRequest> requests = CcdpUtils.toCcdpThreadRequest( data );
        this.logger.debug("Found " + requests.size() + " requests");
        Iterator<CcdpThreadRequest> reqs = requests.iterator();
        while(reqs.hasNext())
        {
          CcdpThreadRequest req = reqs.next();
          this.logger.debug("Request: " + req.toString());
        }
      }
      else
      {
        this.logger.error("Could not find file " + file.getName());
      }
    }
    catch(Exception e)
    {
      this.logger.error("Message: " + e.getMessage(), e);
      e.printStackTrace();
    }
  }
}
