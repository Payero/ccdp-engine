/*
 * @author Scott Bennett, scott.bennett@caci.com
 * 
 * An abstract class to respresent a high level approach to Serverless Controller 
 * implementations. All serverless controllers will extend this class.
 */

package com.axios.ccdp.impl.controllers;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.log4j.Logger;

import com.axios.ccdp.resources.CcdpServerlessResource;
import com.axios.ccdp.factory.CcdpObjectFactory;
import com.axios.ccdp.intfs.CcdpConnectionIntf;
import com.axios.ccdp.intfs.CcdpDatabaseIntf;
import com.axios.ccdp.tasking.CcdpTaskRequest;
import com.axios.ccdp.utils.CcdpUtils;
import com.axios.ccdp.utils.TaskEventIntf;
import com.axios.ccdp.utils.ThreadedTimerTask;
import com.fasterxml.jackson.databind.JsonNode;

public abstract class CcdpServerlessControllerAbs implements TaskEventIntf
{
  /*
   * A connection interface for sending task updates to
   * the CCDP Engine
   */
  protected CcdpConnectionIntf connection = null;
  /*
   * The main application's connection channel
   */
  protected String toMain = null;
  /*
   * A client to connect to the database
   */
  protected CcdpDatabaseIntf dbClient = null;
  /*
   * A class to store the controller information to do data processing
   */
  protected CcdpServerlessResource controllerInfo;
  /*
   * A map to used to map task IDs to runnables
   */
  protected Map<String, Runnable> IDtoThreadMap = new ConcurrentHashMap<>();
   
  /**
   * Generates debug print statements based on the verbosity level.
   */
  private Logger logger = Logger.getLogger(CcdpServerlessControllerAbs.class
      .getName());
  /**
   * Invokes a method periodically to send heartbeats back to the Mesos Master
   */
  @SuppressWarnings("unused")
  private ThreadedTimerTask timer = null;
  
  /*
   * Whether heartbeats are skipped or not
   */
  private boolean skip_hb = false;
  
  public CcdpServerlessControllerAbs()
  {
    this.logger.debug("New CcdpServerlessControllerAbs created");
  }
  
  /*
   * This method configures the abstract class and prepares it for use. The 
   * method itself is NOT abstract.
   * 
   * @param config the JsonNode config to use to configure the class
   */
  public void configure(JsonNode config) 
  {
    this.logger.debug("Configuring new CcdpServerlessControllerAbs");
    CcdpObjectFactory factory = CcdpObjectFactory.newInstance();

    JsonNode task_msg_node = CcdpUtils.getConnnectionIntfCfg();
    JsonNode db_node = CcdpUtils.getDatabaseIntfCfg();
    
    // Configure the connection for sending updates to the main engine
    this.connection = factory.getCcdpConnectionInterface(task_msg_node);
    this.connection.configure(task_msg_node);
    this.toMain = task_msg_node.get(CcdpUtils.CFG_KEY_MAIN_CHANNEL).asText(); 
    this.connection.registerProducer(this.toMain);
    
    // Configure the connection to the database for updating resources
    this.dbClient = factory.getCcdpDatabaseIntf( db_node );
    this.dbClient.configure(db_node);
    this.dbClient.connect();

    this.logger.debug("New controller info with type: " + config.get("name").asText());
    this.controllerInfo = new CcdpServerlessResource(config.get("name").asText());
    this.dbClient.storeServerlessInformation(this.controllerInfo);
    
    JsonNode eng = CcdpUtils.getEngineCfg();
    long cycle = 5000;
    try
    {
      cycle = eng.get(CcdpUtils.CFG_KEY_CHECK_CYCLE).asInt() * 1000;
    }
    catch( Exception e )
    {
      this.logger.warn("The heartbeat frequency was not set using 5 seconds");
    }
    
    this.skip_hb = eng.get(CcdpUtils.CFG_KEY_SKIP_HEARTBEATS).asBoolean();
    if( !skip_hb )
    { 
      this.timer = new ThreadedTimerTask(this, 2*cycle, cycle);
    }
    else
    {
      this.logger.warn("Skipping Hearbeats");
      //this.connection.sendHeartbeat(this.toMain, this.vmInfo);
      this.dbClient.storeServerlessInformation(this.controllerInfo);
    }
  }
  
  @Override
  public void onEvent()
  {
	this.controllerInfo.setLastUpdatedTime(System.currentTimeMillis());
    this.dbClient.storeServerlessInformation(this.controllerInfo);  
  }
    
  /*
   * Runs the task on the task runner after configuring a runnable thread
   * 
   * @param task A CcdpTaskRequest object containing task information
   */
  public abstract void runTask(CcdpTaskRequest task);
  
  /*
   * Handles the result of the serverless task, allowing developer-implemented method
   * 
   * @param result a JsonNode with the result of the task
   * @param task The CcdpTaskRequest the was completed
   */
  public abstract void handleResult(JsonNode result, CcdpTaskRequest task);
  
  /*
   * Called when the thread completes, returns to controller to send updates on status
   * 
   * @param task A CcdpTaskRequest object containing task information
   */
  public void completeTask(CcdpTaskRequest task, JsonNode result)
  {
    this.logger.debug("Task " + task.getTaskId() + " completed");
    this.controllerInfo.removeTask(task);
    this.logger.debug( "Task " + task.getTaskId() + " has status " + task.getState().toString() );
    this.connection.sendTaskUpdate(toMain, task);
    
    this.handleResult(result, task);
  }
  
  /*
   * Processes the result and saves the result if dictated by the task request
   * 
   * @param result the string result of the lambda task
   * @param localSaveLocation the local file location to save the result of the request
   * @param cont_name The name of the controller that completed the task
   */
  protected void localSave(JsonNode result, String localSaveLocation, String cont_name)
  {
    this.logger.debug("Store file locally");       
    try
    {
      DateTimeFormatter dtf = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");
      LocalDateTime now = LocalDateTime.now();
      BufferedWriter out = new BufferedWriter( 
          new FileWriter(localSaveLocation, true)); 
      out.write("\n" + cont_name + " Result from " + 
          dtf.format(now) +"\n" + result.toString() + "\n"); 
      out.close();
    }
    catch ( Exception e )
    {
      logger.error("Exception caught while writing to output file");
      e.printStackTrace();
    }
  }
}
