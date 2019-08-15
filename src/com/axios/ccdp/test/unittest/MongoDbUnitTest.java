package com.axios.ccdp.test.unittest;

import static org.junit.Assert.assertTrue;

import org.apache.log4j.Logger;
import org.junit.Before;
import org.junit.After;
import org.junit.BeforeClass;
import org.junit.Test;

import com.axios.ccdp.impl.db.mongo.CcdpMongoDbImpl;
import com.axios.ccdp.utils.CcdpUtils;
import com.fasterxml.jackson.databind.JsonNode;

public class MongoDbUnitTest
{

  /**
   * Generates debug print statements based on the verbosity level.
   */
  private Logger logger = Logger.getLogger(MongoDbUnitTest.class.getName());

  CcdpMongoDbImpl db = null;
  
  @BeforeClass
  public static void initialize() 
  {
    TestHelperUnitTest.initialize();
  }
  
  @Before
  public void MongoTestSetup()
  {
    this.logger.info("Setting up MongoDB connection using ccdp-config.json");
    JsonNode db_node = CcdpUtils.getDatabaseIntfCfg();
    db = new CcdpMongoDbImpl();
    db.configure(db_node);
    
    this.logger.debug("Ready to connnect");
  }
  
  @Test
  public void testConnect()
  {
    boolean connected = false;
    try
    {
      connected = db.connect();
    }
    catch ( Exception e)
    {
      this.logger.error("Connection Failed", e);
    }
    
    assertTrue("Connection failed", connected);
  }
  
  // ADD test methods for accessing data and adding data
  
  @After
  public void MongoTestTeardown()
  {
    if ( db != null)
    {
      try
      {
        db.disconnect();
      }
      catch ( Exception e)
      {
        this.logger.error("Unable to disconnect", e);
      }
    }
  }
}
