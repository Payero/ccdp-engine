/**
 * 
 */
package com.axios.ccdp.test.unittest.mock;

import static org.junit.Assert.*;

import org.apache.log4j.Logger;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import com.axios.ccdp.test.unittest.JUnitTestHelper;

/**
 * @author Oscar E. Ganteaume
 *
 */
public class MockCcdpTaskRunnerUnitTest
{

  /**
   * Generates debug print statements based on the verbosity level.
   */
  private Logger logger = Logger
      .getLogger(MockCcdpTaskRunnerUnitTest.class.getName());

  public MockCcdpTaskRunnerUnitTest()
  {

  }

  /**
   * @throws java.lang.Exception
   */
  @BeforeClass
  public static void setUpBeforeClass() throws Exception
  {
    JUnitTestHelper.initialize();
  }

  /**
   * @throws java.lang.Exception
   */
  @AfterClass
  public static void tearDownAfterClass() throws Exception
  {
  }

  /**
   * @throws java.lang.Exception
   */
  @Before
  public void setUp() throws Exception
  {
  }

  /**
   * @throws java.lang.Exception
   */
  @After
  public void tearDown() throws Exception
  {
  }

  /**
   * Test method for {@link com.axios.ccdp.cloud.mock.MockCcdpTaskRunner#run()}.
   */
  @Test
  public void testRun()
  {
    fail("Not yet implemented"); // TODO
  }

  /**
   * Test method for {@link com.axios.ccdp.cloud.mock.MockCcdpTaskRunner#MockCcdpTaskRunner(com.axios.ccdp.tasking.CcdpTaskRequest, com.axios.ccdp.connections.intfs.CcdpTaskLauncher)}.
   */
  @Test
  public void testMockCcdpTaskRunner()
  {
    fail("Not yet implemented"); // TODO
  }
}
