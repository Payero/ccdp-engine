package com.axios.ccdp.test.unittest;

import org.apache.log4j.Logger;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;

import com.axios.ccdp.cloud.mock.MockCcdpVMControllerImpl;

import static org.junit.Assert.*;

import java.io.File;

public class MockVMUnitTest
{

  /**
   * Generates debug print statements based on the verbosity level.
   */
  private Logger logger = Logger.getLogger(MockVMUnitTest.class.getName());
  /**
   * The actual object to test
   */
  MockCcdpVMControllerImpl controller = null;
  
  public MockVMUnitTest()
  {
    this.logger.debug("Initializing Controller Mock Unit Test");
  }
  
  /**
   * Runs before any of the methods so is used to initialize all the objects
   * such as connections and configuration properties
   */
  @BeforeClass
  public static void initialize()
  {
    JUnitTestHelper.initialize();
  }
  
  /**
   * This method gets invoke before every test and can be used to do some
   * cleaning before running the tests.  It makes sure the CCDP_HOME environment
   * variable is set properly
   */
  @Before
  public void setUpTest()
  {
    String path = System.getenv("CCDP_HOME");
    assertNotNull("The CCDP_HOME environment variable is not defined", path);
    assertTrue("The CCDP_HOME env variable cannot be empty", path.length() > 0);
    File file = new File(path);
    assertTrue("The path: " + path + " is invalid", file.isDirectory());
    
    this.controller = new MockCcdpVMControllerImpl();
  }
  
  /**
   * This method is invoked after every test and can be used to do some 
   * cleaning after running the tests
   */
  @After
  public void tearDownTest()
  {
    
  }
  
  /**
   * Runs once at the end of the unit testing and can be used to close all the 
   * connections open during initialization
   */
  @AfterClass
  public static void terminate()
  {
    
  }
  
}
