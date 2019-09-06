/*
 * @author Scott Bennett, scott.bennett@caci.com
 * 
 * This class add functionality to database incorporation into CCDP. Developer code can
 * be run using this class for mongo implementations.
 */
package com.axios.ccdp.impl.db.mongo;

import java.util.Arrays;

import org.apache.log4j.Logger;
import org.bson.Document;

import com.axios.ccdp.intfs.CcdpDatabaseEventTriggerIntf;
import com.fasterxml.jackson.databind.JsonNode;
import com.mongodb.Block;
import com.mongodb.MongoClientSettings;
import com.mongodb.ServerAddress;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Aggregates;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.changestream.ChangeStreamDocument;
import com.mongodb.client.model.changestream.FullDocument;

public class CcdpMongoEventTriggerImpl implements CcdpDatabaseEventTriggerIntf
{
  
  /*
   * The block for watching Mongo
   * 
   * THIS IS WHERE CODE GOES TO EXECUTE ON DOCUMENT CHANGES
   */
  private Block<ChangeStreamDocument<Document>> printBlock = new Block<>() {
    @Override
    public void apply(final ChangeStreamDocument<Document> changeStreamDocument) {
      logger.debug(changeStreamDocument.getFullDocument());
    }
  };
  
  /**
   * Generates debug print statements based on the verbosity level.
   */
  private Logger logger = Logger
      .getLogger(CcdpMongoEventTriggerImpl.class.getName());
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
   * Stores the name of the collection to store status
   */
  private String dbColl = "ResourceStatus";
  /**
   * Stores the database object to access
   */
  private MongoDatabase database = null;
  /**
   * Stores the collection object with all the records
   */
  private MongoCollection<Document> statusColl = null;
  /**
   * Generates JSON objects 
   */
  //private ObjectMapper mapper = new ObjectMapper();

  public CcdpMongoEventTriggerImpl() 
  {
    this.logger.debug("New Mongo Event Trigger");
  }

  @Override
  public void run()
  {
    statusColl.watch(Arrays.asList(Aggregates.match(Filters.in
        ("operationType", Arrays.asList("insert", "update", "replace")))))
        .fullDocument(FullDocument.UPDATE_LOOKUP).forEach(printBlock);
  }

  @Override
  public void configure(JsonNode config)
  {
    if( config.has("db-host") )
      this.dbHost = config.get("db-host").asText();
    if( config.has("db-port") )
      this.dbPort = config.get("db-port").asInt();
    if( config.has("db-name") )
      this.dbName = config.get("db-name").asText();
    if( config.has("db-resources-table") )
      this.dbColl = config.get("db-resources-table").asText();
    
    this.connect();
  }
  
  /*
   * Actually connect to the database
   * 
   * @return true if connection was successful, false otherwise
   */
  private boolean connect()
  {
    String url = String.format("mongodb://%s:%d", this.dbHost, this.dbPort);
    this.logger.info("Connecting to: " + url);
    ServerAddress sa = new ServerAddress(this.dbHost, this.dbPort);
    this.client = MongoClients.create(
        MongoClientSettings.builder()
        .applyToClusterSettings(builder ->
                builder.hosts(Arrays.asList(sa)))
        .build());
    this.database = this.client.getDatabase(this.dbName);
    this.statusColl = this.database.getCollection(this.dbColl);
    
    return true;
  }
}
