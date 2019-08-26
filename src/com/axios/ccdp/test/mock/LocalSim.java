/*
 * @author Scott Bennett
 * 
 * A simple controller to send tasks to a Docker container to run commands. 
 */

package com.axios.ccdp.test.mock;

import org.apache.log4j.Logger;

import com.axios.ccdp.impl.controllers.CcdpServerlessControllerAbs;
import com.axios.ccdp.tasking.CcdpTaskRequest;
import com.axios.ccdp.tasking.CcdpTaskRequest.CcdpTaskState;


public class LocalSim extends CcdpServerlessControllerAbs
{
  /**
   * Generates debug print statements based on the verbosity level.
   */
  private Logger logger = Logger.getLogger(LocalSim.class.getName());

  public LocalSim()
  {
    this.logger.debug("New Docker Serverless Sim created");
  }
  
  @Override
  public void runTask( CcdpTaskRequest task )
  {
    this.logger.debug("New task received: \n" + task.toPrettyPrint());
    this.controllerInfo.addTask(task);
    task.setState(CcdpTaskState.STAGING);
    this.connection.sendTaskUpdate(toMain, task);
    // Create a new thread for the lambda runner
    Thread t = new Thread(new LocalSimTaskRunner(task, this));
    this.logger.debug("Thread configured, starting thread");
    task.setState(CcdpTaskState.RUNNING);
    this.connection.sendTaskUpdate(toMain, task);
    t.start();
  }

  @Override
  public void onEvent()
  {
    //this.logger.debug("Storing Local Sim Heartbeat");
    this.dbClient.storeServerlessInformation(this.controllerInfo);
  }

  @Override
  public void completeTask(CcdpTaskRequest task)
  {
    this.logger.debug("Thread Completed");
    this.controllerInfo.removeTask(task);
    this.logger.debug( "Task " + task.getTaskId() + " has status " + task.getState().toString() );
    this.connection.sendTaskUpdate(toMain, task);
  }
  
}
