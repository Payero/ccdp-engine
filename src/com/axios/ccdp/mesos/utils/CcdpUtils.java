package com.axios.ccdp.mesos.utils;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Properties;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang3.text.StrSubstitutor;
import org.apache.log4j.BasicConfigurator;
import org.apache.log4j.Logger;
import org.apache.log4j.PropertyConfigurator;

import com.axios.ccdp.mesos.tasking.CcdpTaskRequest;
import com.axios.ccdp.mesos.tasking.CcdpThreadRequest;
import com.fasterxml.jackson.core.JsonProcessingException;
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
  public static String CFG_FILENAME = "ccdp-config.properties";
  /**  The default name of the log4j configuration file  */
  public static String LOG4J_CFG_FILENAME = "log4j.properties";
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
  /**  The key name of the property used to generate an object factory  */
  public static final String CFG_KEY_FACTORY_IMPL = "factory.interface.impl";
  /** The key name of the property used determine min number of free agents **/
  public static final String CFG_KEY_INITIAL_VMS = "min.number.free.agents";
  
  /** Properties used by the tasking object receiving external tasks */
  public static final String CFG_KEY_TASK_MSG = "taskingIntf";
  /** Properties used by the tasking controller object */
  public static final String CFG_KEY_TASK_CTR = "taskContrIntf";
  /** Properties used by the resource controller object */
  public static final String CFG_KEY_RESOURCE = "resourceIntf";
  /** Properties used by the storage controller object */
  public static final String CFG_KEY_STORAGE = "storageIntf";
  

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
  
  /**
   * Stores all the properties used by the system
   */
  private static Properties properties = System.getProperties();
  
  private static Logger logger = Logger.getLogger(CcdpUtils.class);
  
  private static ObjectMapper mapper = new ObjectMapper();
  
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
      CcdpUtils.loadProperties(cfgFile);
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
      System.out.println("the file " + fname);
      PropertyConfigurator.configure(CcdpUtils.expandVars(cfgFile));
      logger.debug("Configuring Logger using file: " + cfgFile);
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
      String val = CcdpUtils.expandVars(props.getProperty(key));
      CcdpUtils.properties.setProperty(key, val);
    }
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
    while( e.hasMoreElements() )
    {
      String key = (String) e.nextElement();
      if( key.startsWith(filter) )
      {
        String val = CcdpUtils.properties.getProperty(key).trim();
        
        int start = filter.length() + 1;
        String prop = key.substring(start);
        logger.debug("Storing Property[" + prop + "] = " + val);
        map.put(prop, val);
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
    if( json.has("Threads"))
    {
      logger.debug("Processing Threads");
      JsonNode threads = json.get("threads");
      
      for( int i=0; i < threads.size(); i++ )
      {
        CcdpThreadRequest req = 
            mapper.treeToValue(threads.get(i), CcdpThreadRequest.class);
        requests.add(req);
      }      
    }
    
    // In case there are some Tasks without a Thread information
    if( json.has("tasks"))
    {
      logger.debug("Processing Tasks");
      
      JsonNode tasks = json.get("tasks");
      for( int i=0; i < tasks.size(); i++ )
      {
        CcdpThreadRequest request = new CcdpThreadRequest();
        String uuid = UUID.randomUUID().toString();
        request.setThreadId(uuid);
        CcdpTaskRequest task = 
            mapper.treeToValue(tasks.get(i), CcdpTaskRequest.class);
        
        // if at least one of them has a session-id, then use is
        if( task.getSessionId() != null )
          request.setSessionId( task.getSessionId() );
        
        request.getTasks().add(task);
        requests.add(request);
      }    
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
}
