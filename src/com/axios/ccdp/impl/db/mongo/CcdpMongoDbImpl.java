package com.axios.ccdp.impl.db.mongo;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import com.axios.ccdp.resources.CcdpResourceAbs;
import com.axios.ccdp.resources.CcdpServerlessResource;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.bson.Document;
import org.bson.conversions.Bson;

import com.axios.ccdp.intfs.CcdpDatabaseIntf;
import com.axios.ccdp.resources.CcdpVMResource;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
      Bson query = Filters.and(Filters.eq("instance-id", id), Filters.eq("isServerless", false));
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
   * Stores the information from the Resource object into the database.
   * Returns true if was able to store the information or false otherwise
   *
   * @param controller the object with the information to store
   *
   * @return true if was able to store the information or false otherwise
   */
  public boolean storeServerlessInformation(CcdpServerlessResource controller )
  {
    Document doc = Document.parse( controller.toJSON().toString() );
    String type = doc.get("node-type").toString();
    try
    {
      Bson query = Filters.and(Filters.eq("node-type", type), Filters.eq("isServerless", true));
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
        this.statusColl.find(Filters.and( Filters.eq("instance-id", uniqueId), Filters.eq("isServerless", false) ));
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
   * Gets the first object whose node type matches the given controller type.  If 
   * the object is not found it returns null
   * 
   * @param nodeType the object's controller Type
   * 
   * @return returns the object if found or null otherwise
   */
  public CcdpServerlessResource getServerlessInformation(String nodeType)
  {
    FindIterable<Document> docs = 
        this.statusColl.find(Filters.and(Filters.eq("node-type", nodeType),
            Filters.eq("isServerless", true) ));
    try
    {
      return this.getCcdpServerlessResource( docs.first() );
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
        this.statusColl.deleteMany(Filters.and( Filters.eq("instance-id", uniqueId), Filters.eq("isServerless", false) )); 
    return delRes.getDeletedCount();
  }
  
  /**
   * Deletes all the entries whose serverless controller type matches the given one.
   * 
   * @param nodeType the object's controller type
   * 
   * @return returns the  number of entries that were removed 
   */
  public long deleteServerlessInformation(String nodeType)
  {
    DeleteResult delRes = 
        this.statusColl.deleteMany(Filters.and(Filters.eq("node-type", nodeType), Filters.eq("isServerless", true))); 
    return delRes.getDeletedCount();
  }

  /**
   * Gets a list of all the resources stored in the database.
   * 
   * @return a list of the resources stored in the database
   */
  public List<CcdpResourceAbs> getAllInformation()
  {
    List<CcdpResourceAbs> result = new ArrayList<>();
    FindIterable<Document> docs = this.statusColl.find();
    for( Document doc : docs )
    {
      try
      {
        if ( !doc.getBoolean("isServerless", false) )
          result.add( this.getCcdpVMResource(doc) );
        else 
          result.add( this.getCcdpServerlessResource(doc) );
      }
      catch( JsonProcessingException jpe )
      {
        this.logger.error("Could not process VM " + jpe.getMessage() );
        continue;
      }
    }
    return result;
  }
  
  /**
   * Gets a list of all the resources stored in the database.
   * 
   * @return a list of the resources stored in the database
   */
  public List<CcdpVMResource> getAllVMInformation()
  {
    List<CcdpVMResource> result = new ArrayList<>();
    FindIterable<Document> docs = this.statusColl.find( Filters.eq("isServerless", false) );
    for( Document doc : docs )
    {
      try
      {
        result.add( this.getCcdpVMResource(doc) );
      }
      catch( JsonProcessingException jpe )
      {
        this.logger.error("Could not process VM " + jpe.getMessage() );
        continue;
      }
    }
    return result;
  }

  /**
   * Gets a list of all the resources stored in the database of specified type.
   * 
   * @param type the type of node to filter the return list for
   * @return a list of the resources stored in the database of the specified type
   */
  public List<CcdpVMResource> getAllVMInformationOfType( String type )
  {
    List<CcdpVMResource> result = new ArrayList<>();
    FindIterable<Document> docs = this.statusColl.find(
        Filters.and(Filters.eq("node-type", type), Filters.eq("isServerless", false)));
    for( Document doc : docs )
    {
      try
      {
        result.add( this.getCcdpVMResource(doc) );
      }
      catch( JsonProcessingException jpe )
      {
        this.logger.error("Could not process VM " + jpe.getMessage() );
        continue;
      }
    }
    
    return result;
  }
  
  /**
   * Gets a list of all the resources stored in the database of specified type.
   * 
   * @param type the type of node to filter the return list for
   * @return a list of the resources stored in the database of the specified type
   */
  public List<CcdpServerlessResource> getAllServerlessInformationOfType( String type )
  {
    List<CcdpServerlessResource> result = new ArrayList<>();
    FindIterable<Document> docs = this.statusColl.find(
        Filters.and(Filters.eq("node-type", type), Filters.eq("isServerless", true)));
    for( Document doc : docs )
    {
      try
      {
        result.add( this.getCcdpServerlessResource(doc) );
      }
      catch( JsonProcessingException jpe )
      {
        this.logger.error("Could not process VM " + jpe.getMessage() );
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
        this.statusColl.find(Filters.and( Filters.eq("session-id", sid), Filters.eq("isServerless", false) ));
    
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
  
  /**
   * Translates the given Document object into a CcdpServerlessResource.  Basically it
   * removes the _id field and uses ObjectMapper and a HashMap for the 
   * translation.
   * 
   * @param doc the MongoDB Document to translate
   * 
   * @return a CcdpServerlessResource object with all its fields populated
   * 
   * @throws JsonProcessingException a JsonProcessingException is thrown if 
   *         there is a problem during the translation
   */
  private CcdpServerlessResource getCcdpServerlessResource( Document doc ) throws JsonProcessingException
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
    return this.mapper.treeToValue(node, CcdpServerlessResource.class);
    
  }

  /**
   * Gets the total number of VMs stored in the database assigned to a specific
   * session with a specific node type
   * 
   * @param SID the session id to query
   * @param node_type the node_type to query
   * 
   * @return the total number of VMs stored in the database assigned to a
   *         specific session
   */
  @Override
  public List<CcdpVMResource> getAllVMInformationBySessionIdAndNodeType(
      String SID, String node_type)
  {
    this.logger.debug("Finding all VMs for " + SID + " and " + node_type);
    List<CcdpVMResource> result = new ArrayList<>();
    FindIterable<Document> docs = 
        this.statusColl.find( Filters.and(Filters.eq("session-id", SID), Filters.eq("node-type", node_type), Filters.eq("isServerless", false)) );
    
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
}
