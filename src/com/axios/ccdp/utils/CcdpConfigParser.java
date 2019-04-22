package com.axios.ccdp.utils;

import java.io.File;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.apache.log4j.Logger;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
  public static final String KEY_TASKING = "tasking";
  public static final String KEY_RES_PROV = "resource-provisioning";
  public static final String KEY_RESOURCES = "resources";
  
  
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
  
  private JsonNode config = null;
  private ObjectMapper mapper = new ObjectMapper();
  
  @SuppressWarnings("unused")
  private CcdpConfigParser()
  {
    
  }
  
  public CcdpConfigParser( String filename )
  {
    this.logger.debug("Parsing JSON file " + filename);
    this.setFilename(filename);
  }
  
  public CcdpConfigParser( InputStream stream )
  {
    try
    {
      this.config = this.mapper.readTree(stream);
    }
    catch( Exception e)
    {
      this.logger.error("Could not parse the InputStream data");
      e.printStackTrace();
    }
    
  }
  
  public void setFilename( String fname )
  {
    File file = new File(fname);
    if( !file.exists() )
      throw new IllegalArgumentException("The file " + fname + " is invalid");
    try
    {
      this.config = this.mapper.readTree(file);
    }
    catch( Exception e)
    {
      this.logger.error("Could not parse the configuration file " + fname);
      e.printStackTrace();
    }
  }

  public JsonNode getLoggingCfg()
  {
    return this.config.get( CcdpConfigParser.KEY_LOGGING );
  }
  
  public JsonNode getEngineCfg()
  {
    return this.config.get( CcdpConfigParser.KEY_ENGINE );
  }
  
  public List<String> getNodeTypes()
  {
    List<String> nodeTypes = new ArrayList<>();
    JsonNode eng = this.getEngineCfg();
    JsonNode nodes = eng.get( CcdpConfigParser.KEY_NODE_TYPES );
    if( nodes != null && nodes.isArray() )
    {
      for( JsonNode type : nodes )
        nodeTypes.add(type.asText());
    }
    
    return nodeTypes;
  }
  
  public JsonNode getConnnectionIntfCfg()
  {
    JsonNode impls = this.config.get( CcdpConfigParser.KEY_INTF_IMPLS );
    return impls.get( CcdpConfigParser.KEY_CONNECTION_INTF );
  }
  
  public JsonNode getTaskAllocatorIntfCfg()
  {
    JsonNode impls = this.config.get( CcdpConfigParser.KEY_INTF_IMPLS );
    return impls.get( CcdpConfigParser.KEY_TASK_ALLOCATOR );
  }
  
  public JsonNode getResourceManagerIntfCfg()
  {
    JsonNode impls = this.config.get( CcdpConfigParser.KEY_INTF_IMPLS );
    return impls.get( CcdpConfigParser.KEY_RESOURCE_MGR );
  }
  
  public JsonNode getStorageIntfCfg()
  {
    JsonNode impls = this.config.get( CcdpConfigParser.KEY_INTF_IMPLS );
    return impls.get( CcdpConfigParser.KEY_STORAGE );
  }
  
  public JsonNode getResourceMonitorIntfCfg()
  {
    JsonNode impls = this.config.get( CcdpConfigParser.KEY_INTF_IMPLS );
    return impls.get( CcdpConfigParser.KEY_RESOURCE_MON );
  }
  
  public JsonNode getDatabaseIntfCfg()
  {
    JsonNode impls = this.config.get( CcdpConfigParser.KEY_INTF_IMPLS );
    return impls.get( CcdpConfigParser.KEY_DATABASE );
  }
  
  public JsonNode getTaskingParamsCfg()
  {
    JsonNode impls = this.config.get( CcdpConfigParser.KEY_INTF_IMPLS );
    return impls.get( CcdpConfigParser.KEY_TASKING );
  }
  
  public JsonNode getResourcesCfg()
  {
    JsonNode prov = this.config.get( CcdpConfigParser.KEY_RES_PROV );
    return prov.get( CcdpConfigParser.KEY_RESOURCES );
  }
  
  public JsonNode getResourceCfg( String resName )
  {
    JsonNode resources = this.getResourcesCfg();
    return resources.get( resName );
  }
  
  public JsonNode getConfigValue( String key )
  {
    return this.findElement(this.config, key);
  }
  
  private JsonNode findElement(JsonNode jsonNode, String key ) 
  {
    Iterator<String> names = jsonNode.fieldNames();
    while(names.hasNext()) 
    {
      String name = names.next();
      if( name == key )
        return jsonNode.get(key);
      else
        return findElement(jsonNode.get(name), key);
    }
    return null;
  }
  
  public static void main(String[] args) throws Exception
  {
    String cfg_file = System.getProperty("ccdp.config.file");
    CcdpUtils.loadProperties(cfg_file);
    CcdpUtils.configLogger();
    
    CcdpConfigParser parser = new CcdpConfigParser( args[0] );
    System.out.println("Getting Key subnet-id: " + parser.getConfigValue("subnet-id") );
    
  }
}
