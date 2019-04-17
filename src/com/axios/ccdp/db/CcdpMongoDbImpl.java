package com.axios.ccdp.db;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.bson.Document;
import org.bson.conversions.Bson;

import com.axios.ccdp.resources.CcdpVMResource;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mongodb.MongoClientSettings;
import com.mongodb.ServerAddress;
import com.mongodb.client.FindIterable;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Filters;
import com.mongodb.client.result.DeleteResult;


public class CcdpMongoDbImpl
{
  static {
    Logger mongoLogger = Logger.getLogger( "org.mongodb.driver" );
    mongoLogger.setLevel(Level.WARN);
  }
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

  private ObjectMapper mapper = new ObjectMapper();
  
  public CcdpMongoDbImpl()
  {

  }



  public void configure(ObjectNode config)
  {
    if( config.has("db.host") )
      this.dbHost = config.get("db.host").asText();
    if( config.has("db.port") )
      this.dbPort = config.get("db.port").asInt();
    if( config.has("db.name") )
      this.dbName = config.get("db.name").asText();
    if( config.has("status.collection") )
      this.dbColl = config.get("status.collection").asText();
    
  }

  public boolean connect()
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
    
//    try {
//      this.database.createCollection(this.dbColl);
//    }
//    catch( Exception e )
//    {
//      // the collection already exists so ignore the error message
//    }
    this.statusColl = this.database.getCollection(this.dbColl);
    
    return true;
  }

  public boolean storeVMInformation(CcdpVMResource vm )
  {
    Document doc = Document.parse( vm.toJSON().toString() );
    
    if( doc == null )
      System.err.println("Crap!!");
    else
      this.logger.debug("The Document " + doc.toString() );
    String id = doc.get("instance-id").toString();
    
    this.logger.info("Storing Data for " + id );
    try
  {
    Bson query = Filters.eq("instance-id", id);
    this.logger.debug("Finding '" + id + "'");
    Document myFirst = this.statusColl.find(query).first();
    if( myFirst == null )
    {
      this.logger.debug("Did not find it, inserting it");
      this.statusColl.insertOne(doc);      
    }
    else
    {
      this.logger.debug("Updating current entry");
      doc.append("last-updated", Long.valueOf(Instant.now().toEpochMilli()) );
      this.statusColl.findOneAndReplace(query, doc);
    }
    
  }
  catch( Exception e )
  {
    this.logger.error("ERROR " + e.getMessage());
    e.printStackTrace();
  }
    
    
    
    return true;
    
//    boolean result = false;
//    String id = vm.getInstanceId();
//    
//    try
//    {
//      BasicDBObject dbObj = new BasicDBObject();
//      
//      ObjectNode node = vm.toJSON();
//      HashMap<String, Object> map = 
//          new ObjectMapper().readValue(node.traverse(), HashMap.class);
//      dbObj.putAll(map);
//      Document doc = new Document(map);
//      
//      try
//      {
//        Bson query = Filters.eq("instance-id", id);
//        this.logger.debug("Finding '" + id + "'");
//        this.statusColl.findOneAndUpdate(query, dbObj);
//      }
//      catch( Exception e )
//      {
//        this.logger.error("ERROR " + e.getMessage());
//        e.printStackTrace();
//        this.logger.info("Could not find it, inserting " + id);
//        //this.statusColl.insertOne(doc);
//      }
//      result = true;
//    }
//    catch(Exception e )
//    {
//      this.logger.error("Got an exception while inserting the status " + 
//                         e.getMessage() );
//      result = false;
//    }
//    
//    return result;
  }

  public CcdpVMResource getVMInformation(String uniqueId)
  {
    this.logger.info("Finding VM " + uniqueId);
    
    FindIterable<Document> docs = 
        this.statusColl.find(Filters.eq("instance-id", uniqueId) );
    
    try
    {
      return this.getCcdpVMResource( docs.first() );
    }
    catch( Exception e )
    {
      this.logger.error("Got an error " + e.getMessage());
      e.printStackTrace();
      return null;
    }
    
  }
  
  public boolean deleteVMInformation(String uniqueId)
  {
    DeleteResult delRes = this.statusColl.deleteMany(Filters.eq("instance-id", uniqueId)); 
    this.logger.debug("Removed " + delRes.getDeletedCount() + " entries");
    return true;
  }



  public List<CcdpVMResource> getAllVMInformation()
  {
    List<CcdpVMResource> result = new ArrayList<>();
    FindIterable<Document> docs = this.statusColl.find();
    for( Document doc : docs )
    {
      try
      {
        result.add( this.getCcdpVMResource(doc) );
      }
      catch( JsonProcessingException jpe )
      {
        this.logger.error("Could not process VM");
        continue;
      }
    }
    return result;
  }

  public List<CcdpVMResource> getAllVMInformationBySessionId(String sid)
  {
    
    this.logger.info("Finding all VMs for " + sid);
    List<CcdpVMResource> result = new ArrayList<>();
    FindIterable<Document> docs = 
        this.statusColl.find(Filters.eq("session-id", sid) );
    
    for( Document doc : docs )
    {
      try
      {
        result.add( this.getCcdpVMResource(doc) );
      }
      catch( JsonProcessingException jpe )
      {
        this.logger.error("Could not process VM");
        continue;
      }
    }
    return result;
  }

  public boolean disconnect()
  {
    this.client.close();
    return true;
  }
  
  
  private CcdpVMResource getCcdpVMResource( Document doc ) throws JsonProcessingException
  {
    if( doc == null )
      return null;
    
    Map<String, Object> map = new HashMap<>();
    doc.remove("_id");
    Iterator<String> keys = doc.keySet().iterator();
    while( keys.hasNext() )
    {
      String key = keys.next();
      map.put(key,  doc.get(key) );
    }
    JsonNode node = this.mapper.valueToTree(map);
    return this.mapper.treeToValue(node, CcdpVMResource.class);
    
  }
}
