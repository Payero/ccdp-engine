package com.axios.ccdp.utils;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.net.URLConnection;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang3.text.StrSubstitutor;
import org.apache.log4j.BasicConfigurator;
import org.apache.log4j.Logger;
import org.apache.log4j.PropertyConfigurator;

import com.amazonaws.services.route53.model.InvalidArgumentException;
import com.axios.ccdp.tasking.CcdpTaskRequest;
import com.axios.ccdp.tasking.CcdpThreadRequest;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Simple utility class used to perform different tasks by multiple objects
 * within the system.  Its main purpose is to provide a library of methods that
 * are used by a mix of objects in order to avoid repetition.  It also provides
 * any default value as well as all the key definitions across the system.
 * 
 * @author Oscar E. Ganteaume
 *
 */
public class CcdpUtils
{
  /**  The default name of the configuration file  */
  public static final String CFG_FILENAME = "ccdp-config.properties";
  /**  The default name of the log4j configuration file  */
  public static final String LOG4J_CFG_FILENAME = "log4j.properties";
  /**  The key name of the property storing the log4j config filename  */
  public static final String CFG_KEY_LOG4J_CFG_FILE = "log4j.config.file";
  /**  The key name of the property storing the configuration filename  */
  public static final String CFG_KEY_CFG_FILE = "ccdp.config.file";
  /**  The key name of the property the root path of the system  */
  public static final String CFG_KEY_FMWK_ROOT = "ccdp.framework.root";
  /**  The key name of the property with the self contained executor jar  */
  public static final String CFG_KEY_EXEC_JAR = "executor.src.jar.file";
  /**  The key name of the property used to locate the mesos master  */
  public static final String CFG_KEY_MESOS_MASTER_URI = "mesos.master.uri";
  /**  The key name of the property used to send tasks to the Scheduler  */
  public static final String CFG_KEY_TASKING_CHANNEL = "to.scheduler.channel";
  /**  The key name of the property used to set the Unique ID of this session */
  public static final String CFG_KEY_TASKING_UUID = "tasking.uuid";
  /**  The key name of the property used to send events to other entities  */
  public static final String CFG_KEY_RESPONSE_CHANNEL = "from.scheduler.channel";
  /**  The key name of the property used to connect to a broker  */
  public static final String CFG_KEY_BROKER_CONNECTION = "broker.connection";
//  /** The key name of the property used determine min number of free agents **/
//  public static final String CFG_KEY_INITIAL_VMS = "min.number.free.agents";
  /** The key name of the property used add or not an agent at initialization **/
  public static final String CFG_KEY_SKIP_AGENT = "skip.local.agent";
  /** The key name of the property used to set the checking cycle in seconds **/
  public static final String CFG_KEY_CHECK_CYCLE = "resources.checking.cycle";
  /** Comma delimited list of id that should not be terminated **/
  public static final String CFG_KEY_SKIP_TERMINATION = "do.not.terminate";
  
  /** Properties used by the tasking object receiving external tasks */
  public static final String CFG_KEY_CONN_INTF = "connectionIntf";
  /** Properties used by the tasking controller object */
  public static final String CFG_KEY_TASK_CTR = "taskContrIntf";
  /** Properties used by the resource controller object */
  public static final String CFG_KEY_RESOURCE = "resourceIntf";
  /** Properties used by the storage controller object */
  public static final String CFG_KEY_STORAGE = "storageIntf";
  
  /** Class handling connection to external entities */
  public static final String CFG_KEY_CONNECTION_CLASSNAME = "connection.intf.classname";
  /** Class handling connection to external entities */
  public static final String CFG_KEY_TASKING_CLASSNAME = "tasking.intf.classname";
  /** Class handling task allocation and determining when to start/stop VMs */
  public static final String CFG_KEY_TASKER_CLASSNAME = "task.allocator.intf.classname";
  /** Class used to interact with the cloud provider to start/stop VMs */
  public static final String CFG_KEY_RESOURCE_CLASSNAME = "resource.intf.classname";
  /** Class used to interact with the storage solution */
  public static final String CFG_KEY_STORAGE_CLASSNAME = "storage.intf.classname";

  
  /** The JSON key used to store the user's session id **/
  public static final String KEY_SESSION_ID = "session-id";
  /** The JSON key used to store the resource's instance id **/
  public static final String KEY_INSTANCE_ID = "instance-id";
  /** The JSON key used to store task id **/
  public static final String KEY_TASK_ID = "task-id";
  /** The JSON key used to store the thread id **/
  public static final String KEY_THREAD_ID = "thread-id";
  /** The JSON key used to store the resource's instance id **/
  public static final String KEY_TASK_STATUS = "task-status";
  
  /** The name of the public session-id to use when none found in requests   */
  public static final String PUBLIC_SESSION_ID = "public-session";
  

  /****************************************************************************/
  /****************************************************************************/
  
  /** Stores the number of seconds to send/receive heartbeats **/
  public static final String CFG_KEY_HB_FREQ = "heartbeat.freq.secs";
  /** Stores the name of the Queue where to send events to the main app **/
  public static final String CFG_KEY_MAIN_CHANNEL = "ccdp.main.queue";
  /** Stores the name of the configuration key with the log folder location **/
  public static final String CFG_KEY_LOG_DIR = "ccdp.log.dir";
  /** Stores the name of the key with the log directory location **/
  public static final String KEY_CCDP_LOG_DIR = "ccdp-log-dir";
  
  /****************************************************************************/
  /**
   * Defines all the different types of processing nodes supported for data 
   * processing.  It is intended to be able to instantiate images based on the
   * processing needs
   * 
   * @author Oscar E. Ganteaume
   *
   */
  public static enum CcdpNodeType { EC2, EMS, HADOOP, SERV, NIFI, 
                                    CUSTOM, OTHER, DEFAULT, UNKNOWN };
  
  /****************************************************************************/
  /**
   * Stores all the properties used by the system
   */
  private static Properties properties = System.getProperties();
  
  private static Logger logger = Logger.getLogger(CcdpUtils.class);
  
  private static ObjectMapper mapper = new ObjectMapper();
  
  private static Map<CcdpNodeType, CcdpImageInfo> 
                                                      images = new HashMap<>();
  /**
   * Configures the running environment using the properties file whose name
   * matches the CcdpUtils.KEY_CFG_FILE property.  If not found then it attempts
   * to get it from the classpath using Resources ClassLoader
   * 
   * @throws FileNotFoundException a FileNotFoundException is thrown if the 
   *         given file name does not exists
   * @throws IOException an IOException is thrown if there are problems reading
   *         the configuration file
   */
  public static void configureProperties() 
                                      throws FileNotFoundException, IOException
  {
    String key = CcdpUtils.CFG_KEY_CFG_FILE;
    if( CcdpUtils.properties.containsKey( key ) )
      CcdpUtils.configProperties(CcdpUtils.properties.getProperty( key ));
    else
      CcdpUtils.configProperties(null);
  }
  
  /**
   * Configures the running environment using the properties using the given
   * filename.  If the file is null then it attempts to get it from the 
   * classpath using Resources ClassLoader using the default properties filename
   * as given by the CcdpUtils.CFG_FILENAME class variable
   * 
   * @param cfgFile the name of the file containing the desired configuration
   * 
   * @throws FileNotFoundException a FileNotFoundException is thrown if the 
   *         given file name does not exists
   * @throws IOException an IOException is thrown if there are problems reading
   *         the configuration file
   */
  public static void configProperties(String cfgFile )
            throws FileNotFoundException, IOException
  {
    if( cfgFile == null )
    {
      String name = CcdpUtils.CFG_FILENAME;
      URL url = CcdpUtils.class.getClassLoader().getResource(name);
      
      // making sure it was found
      if( url != null )
      {
        logger.debug("Configuring CCDP using URL: " + url);
        CcdpUtils.loadProperties( url.openStream() );
      }
      else
      {
        logger.error("Could not find " + name + " file");
      }
    }
    else
    {
      logger.debug("Configuring CCDP using file: " + cfgFile);
      File file = new File(cfgFile);
      if( file.isFile() )
        CcdpUtils.loadProperties(cfgFile);
      else
      {
        String name = CcdpUtils.CFG_FILENAME;
        URL url = CcdpUtils.class.getClassLoader().getResource(name);
        
        // making sure it was found
        if( url != null )
        {
          logger.debug("Configuring CCDP using URL: " + url);
          CcdpUtils.loadProperties( url.openStream() );
        }
        else
        {
          logger.error("Could not find " + name + " file");
        }
      }
    }
  }
  
  /**
   * Configures the logging system using either the default properties file or
   * the default values.  It tries to load the 'log4j.properties' file from 
   * the classpath.  If the file is not found then it uses the 
   * BasicConfigurator class to configure using the default values.
   * 
   */
  public static void configLogger()
  {
    String key = CcdpUtils.CFG_KEY_LOG4J_CFG_FILE;
    if( CcdpUtils.properties.containsKey(  key ) )
      CcdpUtils.configLogger(CcdpUtils.properties.getProperty( key ));
    else
      CcdpUtils.configLogger(null);
  }
  
  /**
   * Configures the logging mechanism based on the given configuration file.
   * If the file is null, it tries to load the 'log4j.properties' file from 
   * the classpath.  If the file is not found then it uses the 
   * BasicConfigurator class to configure using the default values.
   * 
   * @param cfgFile the file to use to configure the logging system
   */
  public static void configLogger(String cfgFile )
  {
    if( cfgFile == null )
    {
      String name = CcdpUtils.LOG4J_CFG_FILENAME;
      URL url = CcdpUtils.class.getClassLoader().getResource(name);
      
      // making sure it was found
      if( url != null )
      {
        PropertyConfigurator.configure(url);
        logger.debug("Configuring Logger using URL: " + url);
      }
      else
      {
        BasicConfigurator.configure();
        logger.debug("Configuring Logger using BasicConfigurator ");
      }
    }
    else
    {
      String fname = CcdpUtils.expandVars(cfgFile.trim());
      PropertyConfigurator.configure(fname);
      logger.debug("Configuring Logger using file: " + fname);
    }
  }
  
  /**
   * Pauses for the given amount of time in seconds.  This basically calls 
   * Thread.sleep and uses the number of seconds * 1000 as the argument.  The
   * main reason of this function is to avoid the try/catch block every time.
   * 
   * @param secs the number of seconds to pause
   */
  public static void pause( double secs )
  {
    try
    {
      Thread.sleep( (long)(secs * 1000) );
    }
    catch( Exception e) {}
  }
  
  /**
   * Loads all the properties from the given filename.  If the name of the 
   * configuration file is invalid or if it has problem reading the file it 
   * throws an Exception.  All the variables in each property is expanded before
   * it is added to the class properties object.
   * 
   * @param fname the name of the configuration file
   * 
   * @throws FileNotFoundException a FileNotFoundException is thrown if the 
   *         given file name does not exists
   * @throws IOException an IOException is thrown if there are problems reading
   *         the configuration file
   * 
   */
  public static void loadProperties( String fname ) 
                        throws FileNotFoundException, IOException
  {
    CcdpUtils.loadProperties( new File(fname) );
  }
  
  /**
   * Loads all the properties from the given File.  If the name of the 
   * configuration file is invalid or if it has problem reading the file it 
   * throws an Exception.  All the variables in each property is expanded before
   * it is added to the class properties object.
   * 
   * @param file the File object with the desired configuration
   * 
   * @throws IOException an IOException is thrown if there are problems reading
   *         the configuration file
   * 
   */
  public static void loadProperties( File file ) throws IOException
  {
    if( file.isFile() )
    {
      InputStream stream = new FileInputStream(file);
      CcdpUtils.loadProperties(stream);
    }
    else
    {
      String err = "The configuration file " + file.getAbsolutePath() +
                   " was not found";
      System.err.println(err);
      throw new IllegalArgumentException(err);
    }
  }
  
  /**
   * Loads all the properties from the given InputStream.  If it has problem 
   * reading the InputStream it throws an Exception.  All the variables in each 
   * property is expanded before it is added to the class properties object.
   * 
   * @param stream the InputStream object with the desired configuration
   * 
   * @throws IOException an IOException is thrown if there are problems reading
   *         the InputStream
   * 
   */
  public static void loadProperties( InputStream stream ) throws IOException
  {
    Properties props = new Properties();
    props.load(stream);
    Enumeration<?> keys = props.keys();
    while( keys.hasMoreElements() )
    {
      String key = (String)keys.nextElement();
      String val = CcdpUtils.expandVars(props.getProperty(key)).trim();
      CcdpUtils.properties.setProperty(key, val);
    }
    // now we can load the image configuration
    CcdpUtils.loadImageConfiguration();
  }
  
  /**
   * Tests whether or not the configuration contains the given key
   * 
   * @param key the key containing the desired property
   * @return true if the key exists or false otherwise
   */
  public static boolean containsKey(String key)
  {
    return CcdpUtils.properties.containsKey(key);
  }
  
  /**
   * Gets the value of the property stored using the given key.  If the property
   * is not found it returns null
   * 
   * @param key the key containing the desired property
   * @return the value of the property if found or null otherwise
   */
  public static String getProperty(String key)
  {
    return CcdpUtils.properties.getProperty(key);
  }
  
  /**
   * Gets the value of the property stored using the given key as an integer.  
   * If the property is not found it throws a NumberFormatException
   * 
   * @param key the key containing the desired property
   * @return the integer value of the property if found
   * 
   * @throws NumberFormatException a NumberFormatException is thrown if the 
   *         value stored in the key is invalid
   */
  public static int getIntegerProperty(String key)
  {
    return Integer.valueOf(CcdpUtils.getProperty(key));
  }

  /**
   * Gets the value of the property stored using the given key as a double.  
   * If the property is not found it throws a NumberFormatException
   * 
   * @param key the key containing the desired property
   * @return the double value of the property if found
   * 
   * @throws NumberFormatException a NumberFormatException is thrown if the 
   *         value stored in the key is invalid
   */
  public static double getDoubleProperty(String key)
  {
    return Double.valueOf(CcdpUtils.getProperty(key));
  }
  
  /**
   * Gets the value of the property stored using the given key.  If the property
   * is not found it returns null
   * 
   * @param key the key containing the desired property
   * @return the value of the property if found or null otherwise
   */
  public static boolean getBooleanProperty(String key)
  {
    return Boolean.valueOf(CcdpUtils.getProperty(key));
  }
  
  /**
   * Returns all the key, value pairs from the configuration where the keys 
   * start with the given filter.  
   * 
   * @param filter the first characters representing all the desired keys
   * 
   * @return a Map with all the key, value pairs from the configuration where 
   *         the keys start with the given filter.
   */
  public static HashMap<String, String> getKeysByFilter( String filter )
  {
    logger.debug("Parsing all the keys starting with " + filter);
    HashMap<String, String> map = new HashMap<String, String>();
    Enumeration<?> e = CcdpUtils.properties.propertyNames();
    
    if( filter == null )
      return map;
    
    int start = filter.length() + 1;
    
    while( e.hasMoreElements() )
    {
      String key = (String) e.nextElement();
      if( key.startsWith(filter) )
      {
        String val = CcdpUtils.properties.getProperty(key).trim();
        // making sure I am ignoring the first one
        if( key.length() > filter.length() )
        {
          String prop = key.substring(start);
          logger.debug("Storing Property[" + prop + "] = " + val);
          map.put(prop, val);
        }
      }
    }
    
    return map;
  }
  
  /**
   * Returns all the key, value pairs from the configuration where the keys 
   * start with the given filter as a JSON (ObjectNode) object  
   * 
   * @param filter the first characters representing all the desired keys
   * 
   * @return an ObjectNode with all the key, value pairs from the configuration  
   *         where the keys start with the given filter.
   */
  public static ObjectNode getJsonKeysByFilter( String filter )
  {
    HashMap<String, String> map = CcdpUtils.getKeysByFilter(filter);
    
    return mapper.convertValue(map, ObjectNode.class);
  }
  
  
  /**
   * Expands the value of environment variables contained in the value.   If it
   * is not found, it prints an error message warning the user about the missing
   * configuration.  After replacing all the environment variables then it 
   * replaces all the system properties.
   * 
   * @param value the String that may or may not contain an embedded variable
   *        to be replaced
   * @return the string with all environmental and properties variables expanded
   */
  public static String expandVars(String value) 
  {
    // returns null if the value is null
    if (null == value) 
      return null;
    // Searches for environment variables
    Pattern p = Pattern.compile("\\$\\{(\\w+)\\}|\\$(\\w+)");
    Matcher m = p.matcher(value);
    StringBuffer sb = new StringBuffer();
    // while a matching character is found
    while (m.find()) 
    {
      String envVarName = null == m.group(1) ? m.group(2) : m.group(1);
      String envVarValue = System.getenv(envVarName);
      // did not find it 
      if( envVarValue == null )
      {
        String txt = "The environment variable " + envVarName + 
            " is used, but not defined, please check your configuration";
        logger.error(txt);
        System.err.println(txt);
      }
      else
      {
        m.appendReplacement(sb,
            null == envVarValue ? "" : Matcher.quoteReplacement(envVarValue));
      }
     }
    m.appendTail(sb);
    // now replace all the System Properties
    return StrSubstitutor.replaceSystemProperties(sb.toString());
  }
  
  /**
   * Uses the incoming JSON representation of a tasking job to generate a list 
   * of CcdpThreadRequest.  The data needs to have an array of either Threads,
   * Tasks, or Jobs.  If neither of the three types of tasking is found, then
   * it will throw an IllegalArgumentException.
   * 
   * This method essentially generates a JsonObject and returns the value from
   * invoking the toCcdpThreadRequest( json ) method.
   * 
   * @param file the name of the file with the tasks to execute
   * 
   * @return an object containing all the information needed to run a task or
   *         a processing Thread
   * 
   * @throws IllegalArgumentException an IllegalArgumentException is thrown if
   *         none of the three options are found in the JSON data
   * @throws JsonProcessingException a JsonProcessingException is thrown if the
   *         JSON object contains an invalid field or it cannot be processed
   * @throws IOException an IOException is thrown if the data cannot be 
   *          processed
   */
  public static List<CcdpThreadRequest> toCcdpThreadRequest( File file )
                                   throws JsonProcessingException, IOException
  {
    // loading Jobs from the command line
    if( file != null && file.isFile() )
    {
      byte[] data = Files.readAllBytes( Paths.get( file.getAbsolutePath() ) );
      return CcdpUtils.toCcdpThreadRequest(new String( data, "UTF-8"));
    }
    
    return null;
  }
  
  /**
   * Uses the incoming JSON representation of a tasking job to generate a list 
   * of CcdpThreadRequest.  The data needs to have an array of either Threads,
   * Tasks, or Jobs.  If neither of the three types of tasking is found, then
   * it will throw an IllegalArgumentException.
   * 
   * This method essentially generates a JsonObject and returns the value from
   * invoking the toCcdpThreadRequest( json ) method.
   * 
   * @param data the string representation of the tasking to generate
   * 
   * @return an object containing all the information needed to run a task or
   *         a processing Thread
   * 
   * @throws IllegalArgumentException an IllegalArgumentException is thrown if
   *         none of the three options are found in the JSON data
   * @throws JsonProcessingException a JsonProcessingException is thrown if the
   *         JSON object contains an invalid field or it cannot be processed
   * @throws IOException an IOException is thrown if the data cannot be 
   *          processed
   */
  public static List<CcdpThreadRequest> toCcdpThreadRequest( String data )
                                   throws JsonProcessingException, IOException
  {
    JsonNode node = mapper.readTree( data );
    
    return CcdpUtils.toCcdpThreadRequest(node);
  }
  
  /**
   * Uses the incoming JSON representation of a tasking job to generate a list
   * of CcdpThreadRequest.  The data needs to have an array of either Threads,
   * Tasks, or Jobs.  If neither of the three types of tasking is found, then
   * it will throw an IllegalArgumentException.
   * 
   * 
   * @param json a JsonObject representing the tasking to generate
   * 
   * @return an object containing all the information needed to run a task or
   *         a processing Thread
   * 
   * @throws IllegalArgumentException an IllegalArgumentException is thrown if
   *         none of the three options are found in the JSON data
   * @throws JsonProcessingException a JsonProcessingException is thrown if the
   *         JSON object contains an invalid field or it cannot be processed
   */
  public static List<CcdpThreadRequest> toCcdpThreadRequest( JsonNode json ) 
                                                throws JsonProcessingException
  {
    List<CcdpThreadRequest> requests = new ArrayList<CcdpThreadRequest>();
    
    // first let's look for Threads; the simplest case
    if( json.has("threads"))
    {
      logger.debug("Processing Threads");
      JsonNode threads = json.get("threads");
      
      for( int i=0; i < threads.size(); i++ )
      {
        CcdpThreadRequest req = 
            mapper.treeToValue(threads.get(i), CcdpThreadRequest.class);
        
        // Making sure each task has a command
        for( CcdpTaskRequest task : req.getTasks() )
        {
          if( task.getCommand().isEmpty() )
            throw new RuntimeException("The command is required");
        }
        
        requests.add(req);
      }      
    }
    
    // In case there are some Tasks without a Thread information
    if( json.has("tasks"))
    {
      logger.debug("Processing Tasks");
      
      JsonNode tasks = json.get("tasks");
      CcdpThreadRequest request = new CcdpThreadRequest();
      String uuid = UUID.randomUUID().toString();
      request.setThreadId(uuid);
      
      for( int i=0; i < tasks.size(); i++ )
      {
        CcdpTaskRequest task = 
            mapper.treeToValue(tasks.get(i), CcdpTaskRequest.class);
        
        // if at least one of them has a session-id, then use is
        if( task.getSessionId() != null )
          request.setSessionId( task.getSessionId() );
        
        // making sure the task has a command
        if( task.getCommand().isEmpty() )
          throw new RuntimeException("The command is required");
        
        request.getTasks().add(task);
        
      }
      requests.add(request);
    }
    
    // Need to create a Task for each Job and then attach them to a Thread so
    // it can be added to the Thread list.  If they are just jobs then need to 
    // be treated as separate threads so they can run in parallel
    if( json.has("jobs"))
    {
      logger.debug("Processing Jobs");
      
      JsonNode jobs = json.get("jobs");
      
      for( int i=0; i < jobs.size(); i++ )
      {
        CcdpThreadRequest request = new CcdpThreadRequest();
        
        request.setThreadId( UUID.randomUUID().toString() );
        
        CcdpTaskRequest task = new CcdpTaskRequest();
        task.setTaskId( UUID.randomUUID().toString() );
        
        JsonNode job = jobs.get(i);
        
        JsonNode cmd;
        
        if( job.has("cpu") )
          task.setCPU( job.get("cpu").asDouble() );
        if( job.has("mem") )
          task.setMEM( job.get("mem").asDouble() );
        if( job.has(CcdpUtils.KEY_SESSION_ID) )
        {
          String sid = job.get(CcdpUtils.KEY_SESSION_ID).asText();
          task.setSessionId(sid);
          request.setSessionId(sid);
        }
        
        if( job.has("command") )
        {
          cmd = job.get("command");
          List<String> args = new ArrayList<String>();
          for(int n = 0; n < cmd.size(); n++ )
            args.add( cmd.get(n).asText() );
          
          task.setCommand(args);
        }
        else
        {
          throw new RuntimeException("The command is required");
        }
        
        request.getTasks().add(task);
        requests.add(request);
      }
    }
    
    if( requests.size() == 0 )
    {
      String txt = "Could not find neither Threads, Tasks, or Jobs.  Please"
          + " make sure the tasking configuration is properly set";
      throw new IllegalArgumentException(txt);
    }
    
    return requests;
  }
  
  /**
   * Gets the InstanceId of the EC2 instance invoking this method.
   * 
   * @return a string representing the unique instance id of the EC2 instance
   * @throws Exception an Exception is thrown if there is a problem invoking 
   *         the meta-data URL
   */
  public static String retrieveEC2InstanceId() throws Exception
  {
    return CcdpUtils.retrieveEC2Info("instance-id");
  }
 
  /**
   * Gets any of the entries stored as meta-data.  As of 04/22/2017 the only 
   * valid keys are the following:
   * 
   *    ami-id
   *    ami-launch-index
   *    ami-manifest-path
   *    block-device-mapping/
   *    hostname
   *    instance-action
   *    instance-id
   *    instance-type
   *    local-hostname
   *    local-ipv4
   *    mac
   *    metrics/
   *    network/    
   *    placement/
   *    profile
   *    public-hostname
   *    public-ipv4
   *    public-keys/
   *    reservation-id
   *    security-groups
   *    services/   
   *
   * @param key the name of the property to retrieve
   * 
   * @return a string representing the unique instance id of the EC2 instance
   * @throws Exception an Exception is thrown if there is a problem invoking 
   *         the meta-data URL
   */
  public static String retrieveEC2Info(String key) throws Exception
  {
    String data = "";
    String inputLine;
    URL EC2MetaData = 
        new URL("http://169.254.169.254/latest/meta-data/" + key );
    URLConnection EC2MD = EC2MetaData.openConnection();
    // if not an AWS Instance will return after 1 second
    EC2MD.setConnectTimeout(1000);
    
    BufferedReader in = new BufferedReader(
    new InputStreamReader( EC2MD.getInputStream()) );
    while ((inputLine = in.readLine()) != null)
    {
      data = inputLine;
    }
    in.close();
    return data;
  }

  /**
   * Gets the image configuration for a specific type of node.  If the type is
   * null then it throws an exception
   * 
   * @param type the type of node whose image information is required
   * 
   * @return an object representing the image configuration for the specified 
   *         node type
   * @throws InvalidArgumentException an InvalidArgumentException is thrown if
   *         the type is null            
   * */
  public static CcdpImageInfo getImageInfo(CcdpNodeType type)
  {
    if( type == null )
      throw new InvalidArgumentException("The Node Type cannot be null");
    
    return CcdpUtils.images.get(type);
  }
  
  /**
   * Loads all the image information from the configuration file.  All the 
   * different node types are defined in the CcdpUtils.CcdpNodeType enum
   */
  private static void loadImageConfiguration()
  {
    
    for( CcdpNodeType type : CcdpNodeType.values() )
    {
      String strType = type.toString().toLowerCase();
      
      CcdpImageInfo img = new CcdpImageInfo();
      img.setNodeType(type);
      
      String filter = CFG_KEY_RESOURCE + "." + strType;

      Map<String, String> map = CcdpUtils.getKeysByFilter(filter);
      if( !map.isEmpty() )
      {
        if( map.containsKey("min.number.free.agents") )
          img.setMinReq(Integer.parseInt(map.get("min.number.free.agents")));
        if( map.containsKey("image.id") )
          img.setImageId(map.get("image.id"));
        if( map.containsKey("security.group") )
          img.setSecGrp(map.get("security.group"));
        if( map.containsKey("subnet.id") )
          img.setSubnet(map.get("subnet.id"));
        if( map.containsKey("key.file.name") )
          img.setKeyFile(map.get("key.file.name"));
        
        try
        {
          if(map.containsKey("tags"))
          {
            map = mapper.readValue(map.get("tags"),
                new TypeReference<HashMap<String, String>>() {});
            img.setTags(map);
          }
        }
        catch(Exception e )
        {
          System.err.println("Could not parse the image tags: " + e.getMessage());
        }
      }// the map is not empty
      CcdpUtils.images.put(type, img);
      logger.debug("ImagCfg: " + img.toPrettyPrint());
    }// end of the NodeType loop
  }// end of the method
  
}
