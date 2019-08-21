package com.axios.ccdp.impl.controllers;

/*
 * @author Scott Bennett, scott.bennett@caci.com
 * 
 * An abstract class to respresent a high level approach to Serverless Controller 
 * implementations. All serverless controllers will extend this class.
 */
import org.apache.log4j.Logger;

import com.axios.ccdp.factory.CcdpObjectFactory;
import com.axios.ccdp.intfs.CcdpConnectionIntf;
import com.axios.ccdp.intfs.CcdpDatabaseIntf;
import com.axios.ccdp.tasking.CcdpTaskRequest;
import com.axios.ccdp.utils.CcdpUtils;
import com.fasterxml.jackson.databind.JsonNode;

public abstract class CcdpServerlessControllerAbs
{
  /*
   * A connection interface for sending task updates to
   * the CCDP Engine
   */
  private CcdpConnectionIntf connection = null;
  /*
   * The main application's connection channel
   */
  private String toMain = null;
  /*
   * A client to connect to the database
   */
  private CcdpDatabaseIntf dbClient = null;
  /**
   * Generates debug print statements based on the verbosity level.
   */
  private Logger logger = Logger.getLogger(CcdpServerlessControllerAbs.class
      .getName());
  
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
  }
    
  /*
   * Runs the task on the task runner after configuring a runnable thread
   * 
   * @param task A CcdpTaskRequest object containing task information
   */
  public abstract void runTask(CcdpTaskRequest task);
}
