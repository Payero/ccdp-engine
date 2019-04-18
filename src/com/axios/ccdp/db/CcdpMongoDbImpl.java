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

import com.axios.ccdp.connections.intfs.CcdpDatabaseIntf;
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


public class CcdpMongoDbImpl implements CcdpDatabaseIntf
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
  /**
   * Generates JSON objects 
   */
  private ObjectMapper mapper = new ObjectMapper();

  /**
   * Default constructor, it does not perform any action
   */
  public CcdpMongoDbImpl()
  {

  }


  /**
   * Gets all the settings required to connect from the given parameter
   * 
   * @param config the JSON representation of the DB connection arguments
   */
  public void configure(ObjectNode config)
  {
    if( config.has("db.host") )
      this.dbHost = config.get("db.host").asText();
    if( config.has("db.port") )
      this.dbPort = config.get("db.port").asInt();
    if( config.has("db.name") )
      this.dbName = config.get("db.name").asText();
    if( config.has("status.collection") )
      this.dbColl = config.get("resources.table").asText();
    
  }

  /**
   * Connects to the database using the settings passed to the configure() 
   * method.  Returns true if was able to connect or false otherwise
   * 
   * @return true if was able to connect or false otherwise
   */
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
    this.statusColl = this.database.getCollection(this.dbColl);
    
    return true;
  }

  /**
   * Stores the information from the Resource object into the database.
   * Returns true if was able to store the information or false otherwise
   * 
   * @param vm the object with the information to store
   * 
   * @return true if was able to store the information or false otherwise
   */
  public boolean storeVMInformation(CcdpVMResource vm )
  {
    Document doc = Document.parse( vm.toJSON().toString() );
    String id = doc.get("instance-id").toString();
    try
    {
      Bson query = Filters.eq("instance-id", id);
      Document myFirst = this.statusColl.find(query).first();
      if( myFirst == null )
      {
        this.statusColl.insertOne(doc);      
      }
      else
      {
        this.statusColl.findOneAndReplace(query, doc);
      }
      
    }
    catch( Exception e )
    {
      this.logger.error("ERROR " + e.getMessage());
      e.printStackTrace();
    }
    
    return true;
    
  }

  /**
   * Gets the first object whose instance-id matches the given uniqueId.  If 
   * the object is not found it returns null
   * 
   * @param uniqueId the object's unique identifier
   * 
   * @return returns the object if found or null otherwise
   */
  public CcdpVMResource getVMInformation(String uniqueId)
  {
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
  
  /**
   * Deletes all the entries whose instance-id matches the given one.
   * 
   * @param uniqueId the object's unique identifier
   * 
   * @return returns the  number of entries that were removed 
   */
  public long deleteVMInformation(String uniqueId)
  {
    DeleteResult delRes = 
        this.statusColl.deleteMany(Filters.eq("instance-id", uniqueId)); 
    return delRes.getDeletedCount();
  }

  /**
   * Gets a list of all the resources stored in the database.
   * 
   * @return a list of the resources stored in the database
   */
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

  /**
   * Gets a list of all the resources stored in the database whose session id
   * matches the given one 'sid'
   * 
   * @param sid the session-id to query the database
   * 
   * @return a list of the resources stored in the database whose session id
   *         matches the given one 'sid'
   */
  public List<CcdpVMResource> getAllVMInformationBySessionId(String sid)
  {
    
    this.logger.debug("Finding all VMs for " + sid);
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

  /**
   * Gets the total number of VMs stored in the database
   * 
   * @return the total number of VMs stored in the database
   */
  public int getTotalVMInformationCount()
  {
    return this.getAllVMInformation().size();
  }

  /**
   * Gets the total number of VMs stored in the database assigned to a specific
   * session
   * 
   * @return the total number of VMs stored in the database assigned to a
   *         specific session
   */
  public int getVMInformationCount(String sid)
  {
    return this.getAllVMInformationBySessionId( sid ).size();
  }
  /**
   * Disconnects the client from the database
   */
  public void disconnect()
  {
    this.client.close();
  }
  
  /**
   * Translates the given Document object into a CcdpVMResource.  Basically it
   * removes the _id field and uses ObjectMapper and a HashMap for the 
   * translation.
   * 
   * @param doc the MongoDB Document to translate
   * 
   * @return a CcdpVMResource object with all its fields populated
   * 
   * @throws JsonProcessingException a JsonProcessingException is thrown if 
   *         there is a problem during the translation
   */
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
