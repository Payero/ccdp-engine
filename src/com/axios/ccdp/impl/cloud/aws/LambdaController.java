package com.axios.ccdp.impl.cloud.aws;



import org.apache.log4j.Logger;

import com.axios.ccdp.impl.controllers.CcdpServerlessControllerAbs;
import com.axios.ccdp.tasking.CcdpTaskRequest;

public class LambdaController extends CcdpServerlessControllerAbs
{  
  
  /**
   * Generates debug print statements based on the verbosity level.
   */
  private Logger logger = Logger.getLogger(LambdaController.class.getName());

  public LambdaController()
  {
    this.logger.debug("New Lambda Controller Object");
  }

  @Override
  public void runTask(CcdpTaskRequest task)
  {
    this.logger.debug("New task received: \n" + task.toPrettyPrint());
    
    // Create a new thread for the lambda runner
    Thread t = new Thread(new LambdaTaskRunner(task, this));
    this.logger.debug("Thread configured, starting thread");
    t.start();
  }
}
