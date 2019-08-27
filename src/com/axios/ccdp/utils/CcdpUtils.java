package com.axios.ccdp.utils;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.URL;
import java.net.URLConnection;
import java.net.UnknownHostException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang3.text.StrSubstitutor;
import org.apache.log4j.BasicConfigurator;
import org.apache.log4j.Logger;
import org.apache.log4j.PropertyConfigurator;

import com.amazonaws.services.route53.model.InvalidArgumentException;
import com.axios.ccdp.factory.CcdpObjectFactory;
import com.axios.ccdp.intfs.CcdpImgLoaderIntf;
import com.axios.ccdp.resources.CcdpImageInfo;
import com.axios.ccdp.tasking.CcdpTaskRequest;
import com.axios.ccdp.tasking.CcdpThreadRequest;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

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
  /**  The key name of the property storing the configuration filename  */
  public static final String CFG_KEY_CFG_FILE = "ccdp.config.file";
  /**  The default name of the configuration file  */
  public static final String CFG_FILENAME = "ccdp-config.json";
  /**  The key name of the property storing the log4j config filename  */
  public static final String SYS_KEY_LOG4J_CFG_FILE = "log4j.config.file";
  public static final String JSON_KEY_LOG4J_CFG_FILE = "config-file";
  public static final String JSON_KEY_LOGS_DIR = "logs-dir";
  /**  The default name of the log4j configuration file  */
  public static final String LOG4J_CFG_FILENAME = "log4j.properties";
  
  /** The string for CCDP free agents' session id */
  public static final String FREE_AGENT_SID = "FREE_AGENT";
  public static final String SERVERLESS_NODETYPE = "NONE";
  
  /** Stores the property to determine if an agent should send HB or not **/
  public static final String CFG_KEY_SKIP_HEARTBEATS ="skip-heartbeats";  
  /** Stores the number of seconds to send/receive heartbeats **/
  public static final String CFG_KEY_HB_FREQ = "heartbeat-req-secs";
  public static final String CFG_KEY_SKIP_TERMINATION = "do-not-terminate";
  public static final String CFG_KEY_CHECK_CYCLE = "resources-check-cycle";
  /** Stores the name of the Queue where to send events to the main app **/
  public static final String CFG_KEY_MAIN_CHANNEL = "main-queue";
  /** Defines the default name to use for 'default' resources **/
  public static final String DEFAULT_RES_NAME = "DEFAULT";
  /**  The key name of the property used to connect to a broker  */
  public static final String CFG_KEY_BROKER_CONNECTION = "broker";
  
  /**  The key name of the property used to send events to other entities  */
  public static final String CFG_KEY_RESPONSE_CHANNEL = "from.scheduler.channel";  
  
  /**  The key names for Lambda Task fields  */
  public static final String CFG_SERVERLESS = "serverless";
  public static final String CFG_SERVERLESS_ARGS = "arguments";
  public static final String CFG_SERVERLESS_CONFIG = "serverless-config";
  /**  The key names for Lambda Server Configuration fields  **/
  public static final String S_CFG_PROVIDER = "provider";
  public static final String S_CFG_GATEWAY = "URL-Gateway";
  public static final String S_CFG_BUCKET_NAME = "bkt_name";
  public static final String S_CFG_ZIP_FILE = "zip_file";
  public static final String S_CFG_MOD_NAME = "mod_name";
  public static final String S_CFG_KEEP_FILES = "keep_files";
  public static final String S_CFG_VERB_LEVEL = "verb_level";
  public static final String S_CFG_RES_FILE = "res_file";
  public static final String S_CFG_LOCAL_FILE = "local_file";
 
  //  /**  The key name of the property storing the configuration filename  */
//  public static final String CFG_KEY_CFG_FILE = "ccdp.config.file";
//  /**  The key name of the property the root path of the system  */
//  public static final String CFG_KEY_FMWK_ROOT = "ccdp.framework.root";
//  /**  The key name of the property with the self contained executor jar  */
//  public static final String CFG_KEY_EXEC_JAR = "executor.src.jar.file";
//  /**  The key name of the property with the self contained executor jar  */
//  public static final String CFG_KEY_EXEC_CMD = "executor.cmd.line";
//  /**  The key name of the property used to locate the mesos master  */
//  public static final String CFG_KEY_MESOS_MASTER_URI = "mesos.master.uri";
//  /**  The key name of the property used to send tasks to the Scheduler  */
//  public static final String CFG_KEY_TASKING_CHANNEL = "to.scheduler.channel";
//  /**  The key name of the property used to set the Unique ID of this session */
//  public static final String CFG_KEY_TASKING_UUID = "tasking.uuid";
//  /**  The key name of the property used to send events to other entities  */
//  public static final String CFG_KEY_RESPONSE_CHANNEL = "from.scheduler.channel";
//  /**  The key name of the property used to connect to a broker  */
//  public static final String CFG_KEY_BROKER_CONNECTION = "broker.connection";
////  /** The key name of the property used determine min number of free agents **/
////  public static final String CFG_KEY_INITIAL_VMS = "min.number.free.agents";
//  /** The key name of the property used add or not an agent at initialization **/
//  public static final String CFG_KEY_SKIP_AGENT = "skip.local.agent";
//  /** The key name of the property used to set the checking cycle in seconds **/
//  public static final String CFG_KEY_CHECK_CYCLE = "resources.checking.cycle";
//  /** Comma delimited list of id that should not be terminated **/
//  public static final String CFG_KEY_SKIP_TERMINATION = "do.not.terminate";
//  
//  /** Properties used by the tasking object receiving external tasks */
//  public static final String CFG_KEY_CONN_INTF = "connectionIntf";
//  /** Properties used by the tasking controller object */
//  public static final String CFG_KEY_TASK_CTR = "taskContrIntf";
//  /** Properties used by the resource controller object */
//  public static final String CFG_KEY_RESOURCE = "resourceIntf";
//  /** Properties used by the storage controller object */
//  public static final String CFG_KEY_STORAGE = "storageIntf";
//  /** Properties used by the resource monitor object */
//  public static final String CFG_KEY_RES_MON = "res.mon.intf";
//  /** Properties used by the database object */
//  public static final String CFG_KEY_DB_INTF = "database.intf";
// 
//  
//  /** Class handling connection to external entities */
//  public static final String CFG_KEY_CONNECTION_CLASSNAME = "connection.intf.classname";
//  /** Class handling connection to external entities */
//  public static final String CFG_KEY_TASKING_CLASSNAME = "tasking.intf.classname";
//  /** Class handling task allocation and determining when to start/stop VMs */
//  public static final String CFG_KEY_TASKER_CLASSNAME = "task.allocator.intf.classname";
//  /** Class used to interact with the cloud provider to start/stop VMs */
//  public static final String CFG_KEY_RESOURCE_CLASSNAME = "resource.intf.classname";
//  /** Class used to interact with the storage solution */
//  public static final String CFG_KEY_STORAGE_CLASSNAME = "storage.intf.classname";
//  /** Class used to measure resource utilization */
//  public static final String CFG_KEY_RES_MON_CLASSNAME = "res.mon.intf.classname";
//  /** Class used to connect to the database */
//  public static final String CFG_KEY_DATABASE_CLASSNAME = "database.intf.classname";
//  /** Class used to create a new the image loader */
//  public static final String CFG_KEY_LOADER_CLASSNAME = "loader-class";
//  
//  
//  /** Stores the property to determine if an agent should send HB or not **/
//  public static final String CFG_KEY_SKIP_HEARTBEATS ="do.not.send.hearbeat";
//  /** The JSON key used to store the user's session id **/
//  public static final String KEY_SESSION_ID = "session-id";
//  /** The JSON key used to store the resource's instance id **/
//  public static final String KEY_INSTANCE_ID = "instance-id";
//  /** The JSON key used to store task id **/
//  public static final String KEY_TASK_ID = "task-id";
//  /** The JSON key used to store the thread id **/
//  public static final String KEY_THREAD_ID = "thread-id";
//  /** The JSON key used to store the resource's instance id **/
//  public static final String KEY_TASK_STATUS = "task-status";
//  
//  /** The name of the public session-id to use when none found in requests   */
//  public static final String PUBLIC_SESSION_ID = "public-session";
//  
//
  /****************************************************************************/
  /****************************************************************************/
  

//  /** Stores the name of the configuration key with the log folder location **/
//  public static final String CFG_KEY_LOG_DIR = "ccdp.logs.dir";

  /** Prints statements to the screen based on the verbosity level **/
  private static Logger logger = Logger.getLogger(CcdpUtils.class);
  /** Generates all the different JSON objects **/
  private static ObjectMapper mapper = new ObjectMapper();
  /** Stores all the different images based on the type **/
  private static Map<String, CcdpImageInfo> images = new HashMap<>();
  /** Parses all the properties and facilitates it access  **/
  private static CcdpConfigParser parser = null;
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
    String cfgFile = CcdpUtils.getConfigValue( CcdpUtils.CFG_KEY_CFG_FILE );
    CcdpUtils.configProperties( cfgFile );
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
    String cfgFile = 
        CcdpUtils.getConfigValue( CcdpUtils.JSON_KEY_LOG4J_CFG_FILE );
    CcdpUtils.configLogger( cfgFile );
  }
  
  /**
   * Configures the logging mechanism based on the given configuration file.
   * If the file is null, it tries to load the 'log4j.properties' file from 
   * the classpath.  If the file is not found then it uses the 
   * BasicConfigurator class to configure using the default values.
   * 
   * @param cfgFile the file to use to configure the logging system
   */
  public static void configLogger( String cfgFile )
  {
    String logs_dir = CcdpUtils.getConfigValue(CcdpUtils.JSON_KEY_LOGS_DIR);
    if( logs_dir != null )
      System.setProperty("ccdp.logs.dir", CcdpUtils.expandVars(logs_dir) );
    
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
    if( fname == null )
    {
      fname = CcdpUtils.getConfigValue( CcdpUtils.CFG_KEY_CFG_FILE );
    }
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
      //System.out.println("File path: " + file.getPath());
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
    
    CcdpUtils.parser = new CcdpConfigParser( stream );
    
    // now we can load the image configuration
    CcdpUtils.loadImageInfo();
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
      // did not find it trying Property
      if( envVarValue == null )
      {
        envVarValue = System.getProperty(envVarName);
      }
      
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
          if( task.getCommand().isEmpty() && task.getServerless() == false)
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

        // Don't allow tasks with no sid
        if ( task.getSessionId() == null)
        {
          logger.debug("Ignoring a task without a session ID:");
          logger.debug("Ignored task:\n" + task.toPrettyPrint());
          continue;
        }
        // if at least one of them has a session-id, then use is
        else if( request.getSessionId() == null )
          request.setSessionId( task.getSessionId() );

        // if the request already has a session and it does not match the task, discard the task
        else if ( !task.getSessionId().equals(request.getSessionId()) )
        {
          logger.debug("Ignoring task with SID: " + task.getSessionId() +
                  " because it doesn't match the request id: " + request.getSessionId());
          logger.debug("Ignored task:\n" + task.toPrettyPrint());
          continue;
        }
        // If task id and request id match
        else
          logger.debug("Task ID matched request ID");

        // making sure the task has a command if it isn't serverless
        if( !task.getServerless() && task.getCommand().isEmpty() )
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
        
        JsonNode cmd, cfg;
        
        if( job.has("cpu") )
          task.setCPU( job.get("cpu").asDouble() );
        if( job.has("node-type") )
        {
          String type = job.get("node-type").asText();
          task.setNodeType( type.toUpperCase() );
        }
        else
        {
          task.setNodeType("DEFAULT");
        }
        
        if( job.has("mem") )
          task.setMEM( job.get("mem").asDouble() );
        if( job.has(CcdpConfigParser.KEY_SESSION_ID) )
        {
          String sid = job.get(CcdpConfigParser.KEY_SESSION_ID).asText();
          task.setSessionId(sid);
          request.setSessionId(sid);
        }
        
        if( (!job.has(CFG_SERVERLESS) || (job.has(CFG_SERVERLESS) && !job.get(CFG_SERVERLESS).asBoolean())) && job.has("command") )
        {
          cmd = job.get("command");
          List<String> args = new ArrayList<String>();
          for(int n = 0; n < cmd.size(); n++ )
            args.add( cmd.get(n).asText() );
          
          task.setCommand(args);
        }
        else if (job.has(CFG_SERVERLESS) && job.get(CFG_SERVERLESS).asBoolean())
        {
          logger.debug("Serverless Job, command not required");
          Map<String, String> config = new HashMap<String, String>();
          cfg = job.get(CFG_SERVERLESS_CONFIG);
          try
          {
            task.setServeless(true);
            
            List<String> args = new ArrayList<String>();
            for(int n = 0; n < job.get(CFG_SERVERLESS_ARGS).size(); n++ )
              args.add( job.get(CFG_SERVERLESS_ARGS).get(n).asText() );
            task.setServerArgs(args);
            
            config.put(S_CFG_PROVIDER, cfg.get(S_CFG_PROVIDER).asText());
            config.put(S_CFG_GATEWAY, cfg.get(S_CFG_GATEWAY).asText());
            config.put(S_CFG_BUCKET_NAME, cfg.get(S_CFG_BUCKET_NAME).asText());
            config.put(S_CFG_ZIP_FILE, cfg.get(S_CFG_ZIP_FILE).asText());
            config.put(S_CFG_MOD_NAME, cfg.get(S_CFG_MOD_NAME).asText());
            config.put(S_CFG_KEEP_FILES, cfg.get(S_CFG_KEEP_FILES).asText());
            config.put(S_CFG_VERB_LEVEL, cfg.get(S_CFG_VERB_LEVEL).asText());
          }
          catch (Exception e)
          {
            logger.debug("A necessary field was missing from the serverless task.");
            e.printStackTrace();
          }
          // Add local and remote save locations if they are given
          if ( cfg.has(S_CFG_LOCAL_FILE) && cfg.get(S_CFG_LOCAL_FILE) != null )
            config.put(S_CFG_LOCAL_FILE, cfg.get(S_CFG_LOCAL_FILE).asText());
          if ( cfg.has(S_CFG_RES_FILE) && cfg.get(S_CFG_RES_FILE) != null )
            config.put( S_CFG_RES_FILE, cfg.get(S_CFG_RES_FILE).asText() );
          task.setServelessCfg(config);
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
  public static CcdpImageInfo getImageInfo(String type)
  {
    //System.out.println("getImageInfo type: " + type);
    List<String> types = CcdpUtils.parser.getNodeTypes();
    
    if( type == null || !types.contains( type ) )
      throw new InvalidArgumentException("Invalid Node Type: " + type);
    
    return CcdpUtils.images.get(type);
  }
  
  
  /**
   * Loads all the image information from the configuration file.  All the 
   * different node types are defined in the CcdpUtils.CcdpNodeType enum
   */
  private static void loadImageInfo()
  {
    List<String> nodes = CcdpUtils.parser.getNodeTypes();
    CcdpObjectFactory factory = CcdpObjectFactory.newInstance();

    for( String node : nodes )
    {
      JsonNode imgCfg = CcdpUtils.parser.getResourceCfg( node );
      if( imgCfg == null )
      {
        CcdpUtils.logger.warn("The Node Type " + node + " could be found");
        continue;
      }
      CcdpImgLoaderIntf loader = 
          factory.getCcdpImgLoaderIntf(node, imgCfg);
      
      CcdpImageInfo img = loader.getImageInfo();
      CcdpUtils.images.put(node, img);
    }
  }// end of the method
  
  
  
  /**
   * Returns an <code>InetAddress</code> object encapsulating what is most 
   * likely the machine's LAN IP address.
   * 
   * This method is intended for use as a replacement of JDK method 
   * <code>InetAddress.getLocalHost</code>, because
   * that method is ambiguous on Linux systems. Linux systems enumerate the 
   * loopback network interface the same way as regular LAN network interfaces, 
   * but the JDK <code>InetAddress.getLocalHost</code> method does not specify 
   * the algorithm used to select the address returned under such circumstances, 
   * and will often return the loopback address, which is not valid for network 
   * communication. Details
   * <a href="http://bugs.sun.com/bugdatabase/view_bug.do?bug_id=4665037">here</a>.
   * 
   * This method will scan all IP addresses on all network interfaces on the 
   * host machine to determine the IP address most likely to be the machine's 
   * LAN address. If the machine has multiple IP addresses, this method will 
   * prefer a site-local IP address (e.g. 192.168.x.x or 10.10.x.x, usually 
   * IPv4) if the machine has one (and will return the first site-local address 
   * if the machine has more than one), but if the machine does not hold a 
   * site-local address, this method will return simply the first non-loopback 
   * address found (IPv4 or IPv6).
   * 
   * If this method cannot find a non-loopback address using this selection 
   * algorithm, it will fall back to calling and returning the result of JDK 
   * method <code>InetAddress.getLocalHost</code>.
   * 
   * @return the InetAddress that is the most likely interface being used
   *
   * @throws UnknownHostException If the LAN address of the machine cannot be 
   *         found.
   */
  @SuppressWarnings("rawtypes")
  public static InetAddress getLocalHostAddress() throws UnknownHostException 
  {
    try 
    {
      InetAddress candidateAddress = null;
      // Iterate all NICs (network interface cards)...
      
      for (Enumeration ifaces = NetworkInterface.getNetworkInterfaces(); ifaces.hasMoreElements();) 
      {
        NetworkInterface iface = (NetworkInterface) ifaces.nextElement();
        // Iterate all IP addresses assigned to each card...
        for (Enumeration inetAddrs = iface.getInetAddresses(); inetAddrs.hasMoreElements();) 
        {
          InetAddress inetAddr = (InetAddress) inetAddrs.nextElement();
          if (!inetAddr.isLoopbackAddress()) 
          {
            if (inetAddr.isSiteLocalAddress()) 
            {
              String host = inetAddr.getHostAddress();
              /**
               * Any address in the range 192.168.xxx.xxx is a private (aka 
               * site local) IP address. These are reserved for use within an 
               * organization. The same applies to 10.xxx.xxx.xxx addresses, 
               * and 172.16.xxx.xxx through 172.31.xxx.xxx.
               * Addresses in the range 169.254.xxx.xxx are link local IP 
               * addresses. These are reserved for use on a single network 
               * segment.
               * Addresses in the range 224.xxx.xxx.xxx through 239.xxx.xxx.xxx 
               * are multicast addresses.
               * The address 255.255.255.255 is the broadcast address.
               * Anything else should be a valid public point-to-point IPv4 
               * address.

               */
              String[] vals = host.split("\\.");
              int first = Integer.valueOf(vals[0]);
              
              if( host.startsWith("169.254") ) 
                continue;
              else if ( host.equals("255.255.255.255") )
                continue;
              else if( first == 172 )
              {
                int val = Integer.valueOf(vals[1]);
                if( val >= 16 && val <= 31 )
                  continue;
              }
              else if( first >= 224 &&  first <= 239 )
                continue;
              
              // Found non-loopback site-local address. Return it immediately...
              return inetAddr;
            }
            else if (candidateAddress == null) 
            {
              // Found non-loopback address, but not necessarily site-local.
              // Store it as a candidate to be returned if site-local address 
              // is not subsequently found...
              candidateAddress = inetAddr;
              // Note that we don't repeatedly assign non-loopback non-site-local 
              // addresses as candidates, only the first. For subsequent 
              // iterations, candidate will be non-null.
            }
          }
        }
      }
      
      if (candidateAddress != null) 
      {
        // We did not find a site-local address, but we found some other 
        // non-loopback address.  Server might have a non-site-local address 
        // assigned to its NIC (or it might be running IPv6 which deprecates the 
        // "site-local" concept).  Return this non-loopback candidate address...
        return candidateAddress;
      }
      // At this point, we did not find a non-loopback address.
      // Fall back to returning whatever InetAddress.getLocalHost() returns...
      InetAddress jdkSuppliedAddress = InetAddress.getLocalHost();
      if (jdkSuppliedAddress == null) 
      {
        String msg = "The JDK InetAddress.getLocalHost() method " +
                     "unexpectedly returned null.";
        throw new UnknownHostException(msg);
      }
      return jdkSuppliedAddress;
    }
    catch (Exception e) 
    {
      UnknownHostException unknownHostException = 
          new UnknownHostException("Failed to determine LAN address: " + e);
      unknownHostException.initCause(e);
      throw unknownHostException;
    }
  }
  
  /**
   * Gets the configuration value stored either as a key in the configuration 
   * object, as a System Property, or an environment variable.  First it tries 
   * to see if it was set in the configuration object.  If it was not found 
   * then checks the system property, if it does not exists then it tries to 
   * get it as an environment variable.  It returns null if it is not in any of 
   * the three storages mentioned before
   * 
   * @param key the name of the key to look in the configuration file, system
   *        properties, or as an environmental value
   * @return the string representation of what is stored in that key or null if
   *         not found
   */
  public static String getConfigValue( String key )
  {
    JsonNode node = CcdpUtils.parser.getConfigValue(key);
    
    if( node != null )
      return node.asText();
    else if ( System.getProperty(key) != null )
      return System.getProperty(key);
    else
      return System.getenv( key );
  }
  
  /**************************************************************************
   ****************     From the Configuration Parser     ******************* 
   **************************************************************************/
  /**
   * Gets all the configuration parameters used by the logging object
   * 
   * @return an object containing all the different configuration parameters
   */
  public static JsonNode getLoggingCfg()
  {
    return CcdpUtils.parser.getLoggingCfg();
  }
  
  /**
   * Sets all the configuration parameters used by the logging object
   * 
   * @param node an object containing all the different configuration parameters
   */
  public static void setLoggingCfg(JsonNode node)
  {
    CcdpUtils.parser.setLoggingCfg(node);
  }
  
  /**
   * Gets all the configuration parameters used by the engine object
   * 
   * @return an object containing all the different configuration parameters
   */
  public static JsonNode getEngineCfg()
  {
    return CcdpUtils.parser.getEngineCfg();
  }
  
  /**
   * Sets all the configuration parameters used by the engine object
   * 
   * @param node an object containing all the different configuration parameters
   */
  public static void setEngineCfg(JsonNode node)
  {
    CcdpUtils.parser.setEngineCfg(node);
  }
  
  /**
   * Gets all the node types under the resource provisioning tag
   * 
   * @return a list containing all the node types under the resource 
   *         provisioning tag
   */
  public static List<String> getNodeTypes()
  {
    return CcdpUtils.parser.getNodeTypes();
  }
  
  /**
   * Gets all the serverless types under the resource provisioning tag
   * 
   * @return a list containing all the serverless types under the resource 
   *         provisioning tag
   */
  public static List<String> getServerlessTypes()
  {
    return CcdpUtils.parser.getServerlessTypes();
  }
  
  /**
   * Gets all the configuration parameters used by the connection object
   * 
   * @return an object containing all the different configuration parameters
   */
  public static JsonNode getConnnectionIntfCfg()
  {
    return CcdpUtils.parser.getConnnectionIntfCfg();
  }
  
  /**
   * Sets all the configuration parameters used by the connection object
   * 
   * @param node an object containing all the different configuration parameters
   */
  public static void setConnectionIntfCfg(JsonNode node)
  {
    CcdpUtils.parser.setConnnectionIntfCfg(node);
  }
  
  /**
   * Gets all the configuration parameters used by the task allocator object
   * 
   * @return an object containing all the different configuration parameters
   */
  public static JsonNode getTaskAllocatorIntfCfg()
  {
    return CcdpUtils.parser.getTaskAllocatorIntfCfg();
  }
  
  /**
   * Sets all the configuration parameters used by the task allocator object
   * 
   * @param node an object containing all the different configuration parameters
   */
  public static void setTaskAllocatorIntfCfg(JsonNode node)
  {
    CcdpUtils.parser.setTaskAllocatorIntfCfg(node);
  }
  
  /**
   * Gets all the configuration parameters used by the resource manager object
   * 
   * @return an object containing all the different configuration parameters
   */
  public static JsonNode getResourceManagerIntfCfg()
  {
    return CcdpUtils.parser.getResourceManagerIntfCfg();
  }

  /**
   * Sets all the configuration parameters used by the task resource manager 
   * object
   * 
   * @param node an object containing all the different configuration parameters
   */
  public static void setResourceManagerIntfCfg(JsonNode node)
  {
    CcdpUtils.parser.setResourceManagerIntfCfg(node);
  }
  
  /**
   * Gets all the configuration parameters used by the storage object
   * 
   * @return an object containing all the different configuration parameters
   */
  public static JsonNode getStorageIntfCfg()
  {
    return CcdpUtils.parser.getStorageIntfCfg();
  }
  
  /**
   * Sets all the configuration parameters used by the task storage object
   * 
   * @param node an object containing all the different configuration parameters
   */
  public static void setStorageIntfCfg(JsonNode node)
  {
    CcdpUtils.parser.setStorageIntfCfg(node);
  }
  
  /**
   * Gets all the configuration parameters used by the resource monitor object
   * 
   * @return an object containing all the different configuration parameters
   */
  public static JsonNode getResourceMonitorIntfCfg()
  {
    return CcdpUtils.parser.getResourceMonitorIntfCfg();
  }

  /**
   * Sets all the configuration parameters used by the task resource monitor 
   * object
   * 
   * @param node an object containing all the different configuration parameters
   */
  public static void setResourceMonitorIntfCfg(JsonNode node)
  {
    CcdpUtils.parser.setResourceMonitorIntfCfg(node);
  }
  
  /**
   * Gets all the configuration parameters used by the database object
   * 
   * @return an object containing all the different configuration parameters
   */
  public static JsonNode getDatabaseIntfCfg()
  {
    return CcdpUtils.parser.getDatabaseIntfCfg();
  }
  
  /**
   * Sets all the configuration parameters used by the database object
   * 
   * @param node an object containing all the different configuration parameters
   */
  public static void setDatabaseIntfCfg(JsonNode node)
  {
    CcdpUtils.parser.setDatabaseIntfCfg(node);
  }
  
//  /**
//   * Gets all the configuration parameters used by the tasking parameters in
//   * the form of "allocate" and "deallocate"
//   * 
//   * @return an object containing all the different configuration parameters
//   */
//  public static JsonNode getTaskingParamsCfg()
//  {
//    return CcdpUtils.parser.getTaskingParamsCfg();
//  }
//  
//  /**
//   * Sets all the configuration parameters used by the tasking object
//   * 
//   * @param node an object containing all the different configuration parameters
//   */
//  public static void setTaskinParamsCfg(JsonNode node)
//  {
//    CcdpUtils.parser.setTaskingParamsCfg(node);
//  }
  
  /**
   * Gets all the resources configured under the resource provisioning task
   * 
   * @return a map like object with all the different resources
   */
  public static JsonNode getResourcesCfg()
  {
    return CcdpUtils.parser.getResourcesCfg();
  }
  
  /**
   * Gets all the serverless resources configured under the resource provisioning task
   * 
   * @return a map like object with all the different serverless resources
   */
  public static JsonNode getServerlessCfg()
  {
    return CcdpUtils.parser.getServerlessCfg();
  }
  
  /**
   * Sets all the resources configured under the resource provisioning task
   * 
   * @param node a map like object with all the different resources
   */
  public static void setResourcesCfg(JsonNode node)
  {
    CcdpUtils.parser.setResourcesCfg(node);
    // if the image changes then we need to reload them
    CcdpUtils.loadImageInfo();
  }
  
  /**
   * Gets the configuration for a single resource object.  
   * 
   * @param resName the name of the resource to retrieve
   * 
   *  @return the resource matching the given name or null if not found
   */
  public static JsonNode getResourceCfg( String resName )
  {
    return CcdpUtils.parser.getResourceCfg(resName);
  }
  
  /**
   * Sets the configuration for a single resource object.  Once configured the
   * new object is stored in the resources object
   * 
   * @param resName the resName of the resource to store 
   * @param node a map like object the configuration for a single resource 
   *        object
   */
  public static void setResourceCfg(String resName, JsonNode node)
  {
    CcdpUtils.parser.setResourceCfg(resName, node);
    // if the image changes then we need to reload them
    CcdpUtils.loadImageInfo();
  }
}
