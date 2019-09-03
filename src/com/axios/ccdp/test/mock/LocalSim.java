/*
 * @author Scott Bennett
 * 
 * A simple controller that echos the input arguments. 
 */

package com.axios.ccdp.test.mock;

import java.util.List;

import org.apache.log4j.Logger;

import com.axios.ccdp.impl.controllers.CcdpServerlessControllerAbs;
import com.axios.ccdp.tasking.CcdpTaskRequest;
import com.axios.ccdp.tasking.CcdpTaskRequest.CcdpTaskState;
import com.axios.ccdp.utils.CcdpUtils;
import com.axios.ccdp.utils.ServerlessTaskRunner;
import com.fasterxml.jackson.databind.JsonNode;


public class LocalSim extends CcdpServerlessControllerAbs
{
  /**
   * Generates debug print statements based on the verbosity level.
   */
  private Logger logger = Logger.getLogger(LocalSim.class.getName());
  
  private final String controller_name = "LocalSim";

  public LocalSim()
  {
    this.logger.debug("New Local Sim created");
  }
  
  @Override
  public void runTask( CcdpTaskRequest task )
  {
    this.logger.debug("New task received: \n" + task.toPrettyPrint());
    this.controllerInfo.addTask(task);
    task.setState(CcdpTaskState.STAGING);
    this.connection.sendTaskUpdate(toMain, task);
    
    List<String> taskArgs = task.getServerArgs();
    
    // Create a new thread and configure the runner
    Thread t = new Thread(new ServerlessTaskRunner(
        "echo " + String.join(" ", taskArgs), task, this));
    
    this.logger.debug("Thread configured, starting thread");
    task.setState(CcdpTaskState.RUNNING);
    this.connection.sendTaskUpdate(toMain, task);
    t.start();
  }
  
  @Override
  public void onEvent() 
  {
    this.dbClient.storeServerlessInformation(this.controllerInfo);
  }

  @Override
  public void handleResult(JsonNode result, CcdpTaskRequest task)
  {
    String localSaveLoc = task.getServerlessCfg().get(CcdpUtils.S_CFG_LOCAL_FILE);

    if (localSaveLoc != null)
      this.localSave(result, localSaveLoc, controller_name);
    else
      this.logger.debug("Opted out of local storage");
  }
}

