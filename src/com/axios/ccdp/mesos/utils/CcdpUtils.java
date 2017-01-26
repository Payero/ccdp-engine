package com.axios.ccdp.mesos.utils;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.Enumeration;
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
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

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
  public static final String KEY_LOG4J_CFG_FILE = "log4j.config.file";
  /**  The key name of the property storing the configuration filename  */
  public static final String KEY_CFG_FILE = "ccdp.config.file";
  /**  The key name of the property the root path of the system  */
  public static final String KEY_FMWK_ROOT = "ccdp.framework.root";
  /**  The key name of the property with the self contained executor jar  */
  public static final String KEY_EXEC_JAR = "executor.src.jar.file";
  /**  The key name of the property used to locate the mesos master  */
  public static final String KEY_MESOS_MASTER_URI = "mesos.master.uri";
  /**  The key name of the property used to send tasks to the Scheduler  */
  public static final String KEY_TASKING_CHANNEL = "to.scheduler.channel";
  /**  The key name of the property used to send events to other entities  */
  public static final String KEY_RESPONSE_CHANNEL = "from.scheduler.channel";
  /**  The key name of the property used to connect to a broker  */
  public static final String KEY_BROKER_CONNECTION = "broker.connection";
  /**  The key name of the property used to generate an object factory  */
  public static final String KEY_FACTORY_IMPL = "factory.interface.impl";
  /**
   * Stores all the properties used by the system
   */
  private static Properties properties = System.getProperties();
  
  private static Logger logger = Logger.getLogger(CcdpUtils.class);
  
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
    String key = CcdpUtils.KEY_CFG_FILE;
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
    String key = CcdpUtils.KEY_LOG4J_CFG_FILE;
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
      PropertyConfigurator.configure(cfgFile);
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
   */
  public static List<CcdpThreadRequest> toCcdpThreadRequest( String data )
  {
    JsonParser parser = new JsonParser();
    JsonObject json = parser.parse( data ).getAsJsonObject();    
    
    return CcdpUtils.toCcdpThreadRequest(json);
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
   */
  public static List<CcdpThreadRequest> toCcdpThreadRequest( JsonObject json )
  {
    List<CcdpThreadRequest> requests = new ArrayList<CcdpThreadRequest>();
    
    Gson gson = new Gson();
    // first let's look for Threads; the simplest case
    if( json.has("Threads"))
    {
      logger.debug("Processing Threads");
      JsonArray threads = json.getAsJsonArray("Threads");
      
      for( int i=0; i < threads.size(); i++ )
      {
        JsonObject obj = (JsonObject)threads.get(i);
        CcdpThreadRequest req = gson.fromJson(obj, CcdpThreadRequest.class); 
        requests.add(req);
      }      
    }
    
    // In case there are some Tasks without a Thread information
    if( json.has("Tasks"))
    {
      logger.debug("Processing Tasks");
      
      JsonArray tasks = json.getAsJsonArray("Tasks");
      
      for( int i=0; i < tasks.size(); i++ )
      {
        CcdpThreadRequest request = new CcdpThreadRequest();
        String uuid = UUID.randomUUID().toString();
        request.setThreadId(uuid);

        JsonObject obj = (JsonObject)tasks.get(i);
        CcdpTaskRequest task = gson.fromJson(obj, CcdpTaskRequest.class);
        if( i == 0 )
          request.setStartingTask(task.getTaskId());
        request.getTasks().add(task);
        requests.add(request);
      }    
    }
    
    // Need to create a Task for each Job and then attach them to a Thread so
    // it can be added to the Thread list.  If they are just jobs then need to 
    // be treated as separate threads so they can run in parallel
    if( json.has("Jobs"))
    {
      logger.debug("Processing Jobs");
      
      JsonArray jobs = json.getAsJsonArray("Jobs");
      
      for( int i=0; i < jobs.size(); i++ )
      {
        CcdpThreadRequest request = new CcdpThreadRequest();
        
        request.setThreadId( UUID.randomUUID().toString() );
        
        CcdpTaskRequest task = new CcdpTaskRequest();
        task.setTaskId( UUID.randomUUID().toString() );
        
        if( i == 0 )
          request.setStartingTask(task.getTaskId());
        
        JsonObject job = (JsonObject)jobs.get(i);
        double cpu = 0;
        double mem = 0;
        JsonArray cmd = new JsonArray();
        
        if( job.has("CPU") )
          cpu = job.get("CPU").getAsDouble();
        if( job.has("MEM") )
          mem = job.get("MEM").getAsDouble();
        if( job.has("Command") )
          cmd = job.get("Command").getAsJsonArray();
        List<String> args = new ArrayList<String>();
        for(int n = 0; n < cmd.size(); n++ )
          args.add( cmd.get(n).getAsString() );
        
        task.setCPU(cpu);
        task.setMEM(mem);
        task.setCommand(args);
        
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
