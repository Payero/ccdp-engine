package com.axios.ccdp.utils;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Stream;

import org.apache.log4j.Logger;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ObjectNode;


public class CcdpConfigParser
{
  public static final String KEY_LOGGING = "logging";
  public static final String KEY_ENGINE = "engine";
  public static final String KEY_NODE_TYPES = "node-types";
  public static final String KEY_INTF_IMPLS = "interface-impls";
  public static final String KEY_CONNECTION_INTF = "connection";
  public static final String KEY_TASK_ALLOCATOR = "task-allocator";
  public static final String KEY_RESOURCE_MGR = "resource-manager";
  public static final String KEY_STORAGE = "storage";
  public static final String KEY_RESOURCE_MON = "resource-monitor";
  public static final String KEY_DATABASE = "database";
//  public static final String KEY_TASKING = "tasking";
  public static final String KEY_RES_PROV = "resource-provisioning";
  public static final String KEY_RESOURCES = "resources";
  
  public static final String KEY_VM_CONTROLLER = "resource-controller";
  public static final String KEY_VM_RESOURCE_MONITOR = "resource-monitor";
  public static final String KEY_VM_RESOURCE_UNITS = "resource-units";
  public static final String KEY_DOCKER_URL = "docker-url";
  
  /** The JSON key used to store the user's session id **/
  public static final String KEY_SESSION_ID = "session-id";
  /** The JSON key used to store the objects class name **/
  public static final String CFG_KEY_CLASSNAME = "classname";
  /** The JSON key used to store the resource's instance id **/
  public static final String KEY_INSTANCE_ID = "instance-id";
  

  /**  The name of the default resource type  */
  public static final String DEFAULT_IMG_NAME = "DEFAULT";
  /**  The name of the AWS EC2 resource type  */
  public static final String EC2_IMG_NAME = "EC2";
 
  
  /**
   * Generates debug print statements based on the verbosity level.
   */
  private Logger logger = Logger.getLogger(CcdpConfigParser.class.getName());
  
  private ObjectNode config = null;
  private ObjectMapper mapper = new ObjectMapper();
  
  private String filename = null;
  
  @SuppressWarnings("unused")
  private CcdpConfigParser()
  {
    
  }
  
  /**
   * Instantiates a new object using the the given filename.  If the filename
   * is invalid or it cannot be read, it throws a RuntimeException
   * 
   * @param filename the name of the file with all the configuration parameters
   * @throws RuntimeException a RuntimeException is thrown if the filename is 
   *         invalid or it cannot be read
   * @throws FileNotFoundException a FileNotFoundException is thrown if the 
   *         parser cannot generate an input stream using the given filename 
   */
  public CcdpConfigParser( String filename ) throws FileNotFoundException
  {
    this.logger.debug("Parsing JSON file " + filename);
    this.setFilename(filename);
  }
  
  /**
   * Instantiates a new object using the the given stream.  If the stream
   * is invalid or it cannot be read, it throws a RuntimeException
   * 
   * @param stream the byte stream with all the configuration parameters
   * @throws RuntimeException If the filename is invalid or it cannot be read, 
   *         it throws a RuntimeException
   */
  public CcdpConfigParser( InputStream stream )
  {
    this.loadConfig(stream);
  }
  
  /**
   * Instantiates a new object using the the given stream.  If the stream
   * is invalid or it cannot be read, it throws a RuntimeException
   * 
   * @param stream the byte stream with all the configuration parameters
   * @throws RuntimeException If the filename is invalid or it cannot be read, 
   *         it throws a RuntimeException
   */
  public void loadConfig( InputStream stream )
  {
    try
    {
      StringBuilder textBuilder = new StringBuilder();
      InputStreamReader reader = 
          new InputStreamReader(stream, StandardCharsets.UTF_8);
      Stream<String> strStream = new BufferedReader( reader ).lines();
      strStream.forEach( 
            s -> textBuilder.append(CcdpUtils.expandVars(s)).append("\n")
         );
      this.config = this.mapper.readTree(textBuilder.toString()).deepCopy();
    }
    catch( Exception e)
    {
      this.logger.error("Could not parse the InputStream data");
      e.printStackTrace();
      throw new RuntimeException("Could not parse the configuration file");
    }
  }
  
  /**
   * Gets the current configuration used by the parser
   * 
   * @return the current configuration used by the parser
   */
  public JsonNode getConfiguration()
  {
    return this.config;
  }
  
  /**
   * Sets the JSON file name containing all the configuration parameters. 
   * If the filename is invalid or it cannot be read, it throws a 
   * RuntimeException
   * 
   * @param fname the name of the file to store
   * 
   * @throws RuntimeException If the filename is invalid or it cannot be read, 
   *         it throws a RuntimeException
   */
  public void setFilename( String fname )
  {
    File file = new File(fname);
    if( !file.exists() )
      throw new IllegalArgumentException("The file " + fname + " is invalid");
    try
    {
      this.filename = fname;
      this.loadConfig( new FileInputStream( fname ) );
    }
    catch( Exception e)
    {
      this.logger.error("Could not parse the configuration file " + fname);
      e.printStackTrace();
      throw new RuntimeException("Could not parse the configuration file");
    }
  }
  
  /**
   * 
   * Gets the name of the file used to load the configurations
   * 
   * @return the name of the file used to load the configurations
   */
  public String getFilename()
  {
    return this.filename;
  }

  /**
   * Gets all the configuration parameters used by the logging object
   * 
   * @return an object containing all the different configuration parameters
   */
  public JsonNode getLoggingCfg()
  {
    return this.config.get( CcdpConfigParser.KEY_LOGGING );
  }
  /**
   * Sets all the configuration parameters used by the logging object
   * 
   * @param node an object containing all the different configuration parameters
   */
  public void setLoggingCfg(JsonNode node)
  {
    this.config.set( CcdpConfigParser.KEY_LOGGING, node );
  }
  
  /**
   * Gets all the configuration parameters used by the engine object
   * 
   * @return an object containing all the different configuration parameters
   */
  public JsonNode getEngineCfg()
  {
    return this.config.get( CcdpConfigParser.KEY_ENGINE );
  }
  
  /**
   * Sets all the configuration parameters used by the engine object
   * 
   * @param node an object containing all the different configuration parameters
   */
  public void setEngineCfg(JsonNode node)
  {
    this.config.set( CcdpConfigParser.KEY_ENGINE, node );
  }
  
  /**
   * Gets all the node types under the resource provisioning tag
   * 
   * @return a list containing all the node types under the resource 
   *         provisioning tag
   */
  public List<String> getNodeTypes()
  {
    List<String> nodeTypes = new ArrayList<>();
    JsonNode nodes = this.getResourcesCfg();
    if( nodes != null && nodes.isContainerNode() )
    {
      Iterator<String> names = nodes.fieldNames();
      while( names.hasNext() )
        nodeTypes.add( names.next() );
    }
    
    return nodeTypes;
  }
  
  /**
   * Gets all the configuration parameters used by the connection object
   * 
   * @return an object containing all the different configuration parameters
   */
  public JsonNode getConnnectionIntfCfg()
  {
    JsonNode impls = this.config.get( CcdpConfigParser.KEY_INTF_IMPLS );
    return impls.get( CcdpConfigParser.KEY_CONNECTION_INTF );
  }

  /**
   * Sets all the configuration parameters used by the engine object
   * 
   * @param node an object containing all the different configuration parameters
   */
  public void setConnnectionIntfCfg(JsonNode node)
  {
    this.setImplementedCfg( CcdpConfigParser.KEY_CONNECTION_INTF, node);
  }

  /**
   * Gets all the configuration parameters used by the task allocator object
   * 
   * @return an object containing all the different configuration parameters
   */
  public JsonNode getTaskAllocatorIntfCfg()
  {
    JsonNode impls = this.config.get( CcdpConfigParser.KEY_INTF_IMPLS );
    return impls.get( CcdpConfigParser.KEY_TASK_ALLOCATOR );
  }
  
  /**
   * Sets all the configuration parameters used by the task allocator object
   * 
   * @param node an object containing all the different configuration parameters
   */
  public void setTaskAllocatorIntfCfg(JsonNode node)
  {
    this.setImplementedCfg( CcdpConfigParser.KEY_TASK_ALLOCATOR, node);
  }
  
  /**
   * Gets all the configuration parameters used by the resource manager object
   * 
   * @return an object containing all the different configuration parameters
   */
  public JsonNode getResourceManagerIntfCfg()
  {
    JsonNode impls = this.config.get( CcdpConfigParser.KEY_INTF_IMPLS );
    return impls.get( CcdpConfigParser.KEY_RESOURCE_MGR );
  }
  
  /**
   * Sets all the configuration parameters used by the task resource manager 
   * object
   * 
   * @param node an object containing all the different configuration parameters
   */
  public void setResourceManagerIntfCfg(JsonNode node)
  {
    this.setImplementedCfg( CcdpConfigParser.KEY_RESOURCE_MGR, node);
  }
  
  /**
   * Gets all the configuration parameters used by the storage object
   * 
   * @return an object containing all the different configuration parameters
   */  
  public JsonNode getStorageIntfCfg()
  {
    JsonNode impls = this.config.get( CcdpConfigParser.KEY_INTF_IMPLS );
    return impls.get( CcdpConfigParser.KEY_STORAGE );
  }

  /**
   * Sets all the configuration parameters used by the task storage object
   * 
   * @param node an object containing all the different configuration parameters
   */
  public void setStorageIntfCfg(JsonNode node)
  {
    this.setImplementedCfg( CcdpConfigParser.KEY_STORAGE, node);
  }
  
  /**
   * Gets all the configuration parameters used by the resource monitor object
   * 
   * @return an object containing all the different configuration parameters
   */
  public JsonNode getResourceMonitorIntfCfg()
  {
    JsonNode impls = this.config.get( CcdpConfigParser.KEY_INTF_IMPLS );
    return impls.get( CcdpConfigParser.KEY_RESOURCE_MON );
  }
  
  /**
   * Sets all the configuration parameters used by the task resource monitor 
   * object
   * 
   * @param node an object containing all the different configuration parameters
   */
  public void setResourceMonitorIntfCfg(JsonNode node)
  {
    this.setImplementedCfg( CcdpConfigParser.KEY_RESOURCE_MON, node);
  }
  
  /**
   * Gets all the configuration parameters used by the database object
   * 
   * @return an object containing all the different configuration parameters
   */
  public JsonNode getDatabaseIntfCfg()
  {
    JsonNode impls = this.config.get( CcdpConfigParser.KEY_INTF_IMPLS );
    return impls.get( CcdpConfigParser.KEY_DATABASE );
  }
  
  /**
   * Sets all the configuration parameters used by the database object
   * 
   * @param node an object containing all the different configuration parameters
   */
  public void setDatabaseIntfCfg(JsonNode node )
  {
    this.setImplementedCfg( CcdpConfigParser.KEY_DATABASE, node);
  }
  
  /**
   * Gets all the resources configured under the resource provisioning task
   * 
   * @return a map like object with all the different resources
   */
  public JsonNode getResourcesCfg()
  {
    JsonNode prov = this.config.get( CcdpConfigParser.KEY_RES_PROV );
    return prov.get( CcdpConfigParser.KEY_RESOURCES );
  }

  /**
   * Sets all the resources configured under the resource provisioning task
   * 
   * @param node a map like object with all the different resources
   */
  public void setResourcesCfg(JsonNode node)
  {
    ObjectNode provRes = 
        this.config.get( CcdpConfigParser.KEY_RES_PROV ).deepCopy();
    provRes.set(CcdpConfigParser.KEY_RESOURCES, node);
    this.config.set( CcdpConfigParser.KEY_RES_PROV, provRes );
  }
  
  /**
   * Gets the Resource configuration of a specific type based on the resName
   * argument
   * 
   * @param resName the name of the resource of interest
   * @return a map like representation of the resource configuration
   */
  public JsonNode getResourceCfg( String resName )
  {
    JsonNode resources = this.getResourcesCfg();
    return resources.get( resName );
  }
  
  /**
   * Sets the configuration for a single resource object.  Once configured the
   * new object is stored in the resources object
   * 
   * @param resName the name of the resource to store 
   * @param node a map like object the configuration for a single resource 
   *        object
   */
  public void setResourceCfg( String resName, JsonNode node )
  {
    ObjectNode resources = this.getResourcesCfg().deepCopy();
    resources.set(resName, node);
    this.setResourcesCfg(resources);
  }

  /**
   * Gets a single configuration object if found otherwise it returns null
   * 
   * @param key the name of the configuration parameter to find
   * 
   * @return a single configuration object if found otherwise it returns null
   */
  public JsonNode getConfigValue( String key )
  {
    Iterator<String> names = this.config.fieldNames();
    while( names.hasNext() )
    {
      String name = names.next();
      JsonNode value = this.findElement(this.config.get(name), key);
      if( value != null )
        return value;
    }
    return null;
  }
  
  /**
   * Iterates recursively through all the  nodes in the current configuration  
   * parameters searching for the given key.  Returns the object stored under 
   * the key name or null if it was not found.
   * 
   * @param node the object to search for the given key
   * @param key the name of the configuration parameter to find
   * 
   * @return the object stored under the key name or null if it was not found.
   */
  private JsonNode findElement(JsonNode node, String key ) 
  {
    Iterator<String> names = node.fieldNames();
    while( names.hasNext() )
    {
      String name = names.next();
      JsonNode value = node.get(name);
      if( name.equals(key) )
      {
        return value;
      }
      else
      {
        if( value.isContainerNode() )
        {
          value = this.findElement(value, key);
          if( value != null )
            return value;
        }
      }
    }
    
    return null;
  }
  
  /**
   * Returns a String object representing the JSON configuration object used by
   * this object 
   * 
   * @return a JSON representation of the configuration used by this object
   */
  public String toString()
  {
    return this.config.toString();
  }
  
  /**
   * Returns the JSON representation of the configuration used by the parser. 
   * The String is expanded for ease to read and follow
   *  
   * @return an expanded JSON representation of the configuration file
   */
  public String toPrettyPrint()
  {
    String str = null;
    
    try
    {
      this.mapper.enable(SerializationFeature.INDENT_OUTPUT);
      str = this.mapper.writeValueAsString(this.config);
    }
    catch( Exception e )
    {
      throw new RuntimeException("Could not write Json " + e.getMessage() );
    }
    
    return str;
  }

  

  /**
   * Stores a new configuration for a given implemented class as specified 
   * by the key argument
   * 
   * @param key the implemented class name to replace
   * @param node the new configuration to store
   */
  private void setImplementedCfg( String key, JsonNode node )
  {
    ObjectNode impls = 
        this.config.get( CcdpConfigParser.KEY_INTF_IMPLS ).deepCopy();
    impls.set( key, node);
    this.config.set( CcdpConfigParser.KEY_INTF_IMPLS, impls );
  }
  
  /**
   * Runs the show 
   * 
   * @param args command line arguments
   * 
   * @throws Exception throws an exception if something unexpected happens
   */
  public static void main(String[] args) throws Exception
  {
    String cfg_file = System.getProperty("ccdp.config.file");
    CcdpUtils.loadProperties(cfg_file);
    CcdpUtils.configLogger();
    
    CcdpConfigParser parser = new CcdpConfigParser( args[0] );
    System.err.println("Configuration Used: \n" + parser.toPrettyPrint() );
  }
}
