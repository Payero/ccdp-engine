package com.axios.ccdp.mesos.utils;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Enumeration;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang3.text.StrSubstitutor;
import org.apache.log4j.BasicConfigurator;
import org.apache.log4j.PropertyConfigurator;

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
        System.out.println("Configuring CCDP using URL: " + url);
        CcdpUtils.loadProperties( url.openStream() );
      }
      else
      {
        System.err.println("Could not find " + name + " file");
      }
    }
    else
    {
      System.out.println("Configuring CCDP using file: " + cfgFile);
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
        System.out.println("Configuring Logger using URL: " + url);
        PropertyConfigurator.configure(url);
      }
      else
      {
        System.out.println("Configuring Logger using BasicConfigurator ");
        BasicConfigurator.configure();
      }
    }
    else
    {
      System.out.println("Configuring Logger using file: " + cfgFile);
      PropertyConfigurator.configure(cfgFile);
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
}
