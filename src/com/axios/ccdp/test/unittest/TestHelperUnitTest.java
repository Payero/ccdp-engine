package com.axios.ccdp.test.unittest;

import java.io.File;

import org.apache.log4j.BasicConfigurator;
import org.apache.log4j.Logger;
import org.apache.log4j.PropertyConfigurator;
import org.junit.Test;

import com.axios.ccdp.utils.CcdpUtils;

import static org.junit.Assert.*;

public class TestHelperUnitTest
{
//  public static final String CFG_FILE_KEY = "config.file";
//  
//  public static final String CFG_LOG4J_KEY = "log4j.cfg.file";
  
  public static String DEFAULT_CFG_FILE = "ccdp-config.json";
  
  public static Logger getLogger()
  {
    return TestHelperUnitTest.getLogger(TestHelperUnitTest.class);
  }
  
  @SuppressWarnings("rawtypes")
  public static Logger getLogger(Class clazz)
  {
    return TestHelperUnitTest.getLogger(clazz.getName());
  }
  
  public static Logger getLogger(String name)
  {
    Logger logger = Logger.getLogger(name);
    String cfg_file = System.getProperty(CcdpUtils.SYS_KEY_LOG4J_CFG_FILE);
    
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
    System.out.println("      ant -Dccdp.config.file=<new file> test ");
    System.out.println("");
    
    String path = System.getenv("CCDP_HOME");
    if( path == null )
      path = System.getProperty("CCDP_HOME");
    
    if( path == null || path.length() == 0 )
    {
      String txt = "Cannot find CCDP_HOME environment variable or "
          + "System Property.  Please check the configuration";
      System.err.println(txt);
      System.exit(-1);
    }
    assertTrue("The CCDP_HOME env variable cannot be empty", path.length() > 0);
    File file = new File(path);
    assertTrue("The path: " + path + " is invalid", file.isDirectory());
    
    // getting the name of the configuration file .  If is null or invalid
    // attempts to use the default one
    String fname = System.getProperty(CcdpUtils.CFG_KEY_CFG_FILE);
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
      e.printStackTrace();
      fail();
    }
  }
  
  @Test
  public void helloHelper()
  {
    assert("Hello Helper!!".equals("Hello Helper!!"));
    assert("Hello Helper!!" == "Hello Helper!!" );
  }
  
  
}
