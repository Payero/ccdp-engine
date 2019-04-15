package com.axios.ccdp.utils;

import java.util.List;

import org.apache.log4j.Logger;

import com.axios.ccdp.connections.intfs.CcdpDatabaseIntf;
import com.axios.ccdp.resources.CcdpVMResource;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mongodb.client.MongoClient;

public class CcdpMongoDbImpl implements CcdpDatabaseIntf
{
  /**
   * Generates debug print statements based on the verbosity level.
   */
  private Logger logger = Logger.getLogger(CcdpMongoDbImpl.class.getName());
  /**
   * Connects and interacts with the database
   */
  private MongoClient client = null;
  /**
   * Stores the hostname/IP address of the DB server
   */
  private String dbHost = "localhost";
  /**
   * Stores the port number of the DB server
   */
  private int dbPort = 27017;
  /**
   * Stores the name of the database to use
   */
  private String dbName = "CCDP";
  
  /**
   * Instantiates a new object and set all the default values
   */
  public CcdpMongoDbImpl()
  {
    
  }
  
  @Override
  public boolean storeVMInformation(CcdpVMResource vm)
  {
    // TODO Auto-generated method stub
    return false;
  }

  @Override
  public void configure(ObjectNode config)
  {
    if( config.has("db.host") )
      this.dbHost = config.get("db.host").asText();
    if( config.has("db.port") )
      this.dbPort = config.get("db.port").asInt();
    if( config.has("db.name") )
      this.dbName = config.get("db.name").asText();
  }

  @Override
  public boolean connect()
  {
    // TODO Auto-generated method stub
    return false;
  }

  @Override
  public boolean deleteVMInformation(String uniqueId)
  {
    // TODO Auto-generated method stub
    return false;
  }

  @Override
  public CcdpVMResource getVMInformation(String uniqueId)
  {
    // TODO Auto-generated method stub
    return null;
  }

  @Override
  public List<CcdpVMResource> getAllVMInformation()
  {
    // TODO Auto-generated method stub
    return null;
  }

  @Override
  public List<CcdpVMResource> getAllVMInformationBySessionId(String sid)
  {
    // TODO Auto-generated method stub
    return null;
  }

  @Override
  public boolean disconnect()
  {
    // TODO Auto-generated method stub
    return false;
  }

}
