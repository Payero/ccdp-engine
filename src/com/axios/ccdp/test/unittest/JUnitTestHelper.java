package com.axios.ccdp.test.unittest;

import java.io.File;

import org.apache.log4j.BasicConfigurator;
import org.apache.log4j.Logger;
import org.apache.log4j.PropertyConfigurator;

import com.axios.ccdp.utils.CcdpUtils;

import static org.junit.Assert.*;

public class JUnitTestHelper
{
  public static final String CFG_FILE_KEY = "config.file";
  
  public static final String CFG_LOG4J_KEY = "log4j.cfg.file";
  
  public static String DEFAULT_CFG_FILE = "ccdp-config.properties";
  
  /**
   * Generates debug print statements based on the verbosity level.
   */
  private static Logger logger = Logger.getLogger(JUnitTestHelper.class.getName());

  public static Logger getLogger()
  {
    return JUnitTestHelper.getLogger(JUnitTestHelper.class);
  }
  
  @SuppressWarnings("rawtypes")
  public static Logger getLogger(Class clazz)
  {
    return JUnitTestHelper.getLogger(clazz.getName());
  }
  
  public static Logger getLogger(String name)
  {
    Logger logger = Logger.getLogger(name);
    String cfg_file = System.getProperty(CFG_LOG4J_KEY);
    
    if( cfg_file != null )
      PropertyConfigurator.configure(cfg_file);
    else
      BasicConfigurator.configure();
    
    return logger;
  }
  
  public static void initialize()
  {
    System.out.println("     Unit Test Setting Information     ");
    System.out.println("  The configuration file can be set by setting the ");
    System.out.println("  ccdp.cfg.file system property for example: ");
    System.out.println("      ant -Dccdp.cfg.file=<new file> test ");
    System.out.println("");
    
    String path = System.getenv("CCDP_HOME");
    if( path == null )
      path = System.getProperty("CCDP_HOME");
    assertNotNull("The CCDP_HOME environment variable is not defined", path);
    assertTrue("The CCDP_HOME env variable cannot be empty", path.length() > 0);
    File file = new File(path);
    assertTrue("The path: " + path + " is invalid", file.isDirectory());
    
    
    // getting the name of the configuration file .  If is null or invalid
    // attempts to use the default one
    String fname = System.getProperty(CFG_FILE_KEY);
    if( fname != null )
    {
      // if it can't find the file set it to null to use default file
      file = new File(fname);
      if( !file.isFile() )
        fname = null;
    }
    // attempt to use default file
    if( fname == null )
      fname = path + "/config/" + DEFAULT_CFG_FILE;
    
    // final test, make sure is there
    file = new File(fname);
    if( !file.isFile() )
      fail();
    
    // load all the Properties
    try
    {
      CcdpUtils.loadProperties(file);
      CcdpUtils.configLogger();
      
    }
    catch( Exception e )
    {
      fail();
    }
  }
  
  
}
