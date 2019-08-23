package com.axios.ccdp.impl.cloud.aws;

import org.apache.log4j.Logger;

import com.axios.ccdp.impl.controllers.CcdpServerlessControllerAbs;
import com.axios.ccdp.tasking.CcdpTaskRequest;
import com.axios.ccdp.tasking.CcdpTaskRequest.CcdpTaskState;

public class AWSLambdaController extends CcdpServerlessControllerAbs
{  
  
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
    task.setState(CcdpTaskState.STAGING);
    this.connection.sendTaskUpdate(toMain, task);
    // Create a new thread for the lambda runner
    Thread t = new Thread(new AWSLambdaTaskRunner(task, this));
    this.logger.debug("Thread configured, starting thread");
    task.setState(CcdpTaskState.RUNNING);
    this.connection.sendTaskUpdate(toMain, task);
    t.start();
  }
  
  @Override
  public void completeTask(CcdpTaskRequest task)
  {
    this.logger.debug("Thread Completed");
    this.logger.debug( "Task " + task.getTaskId() + " has status " + task.getState().toString() );
    this.connection.sendTaskUpdate(toMain, task);
  }
}
