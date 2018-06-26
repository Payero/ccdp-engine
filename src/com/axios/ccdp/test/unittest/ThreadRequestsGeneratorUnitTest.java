package com.axios.ccdp.test.unittest;

import java.io.File;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.log4j.Logger;
import org.junit.Test;

import com.axios.ccdp.tasking.CcdpTaskRequest;
import com.axios.ccdp.tasking.CcdpThreadRequest;
import com.axios.ccdp.tasking.CcdpThreadRequest.TasksRunningMode;
import com.axios.ccdp.utils.CcdpUtils;
import com.axios.ccdp.utils.CcdpUtils.CcdpNodeType;

import junit.framework.TestCase;

import org.hamcrest.CoreMatchers.*;
import org.hamcrest.core.AnyOf;
import org.junit.Assert;

public class ThreadRequestsGeneratorUnitTest extends TestCase
{

  /**
   * Generates debug print statements based on the verbosity level.
   */
  private Logger logger = Logger
      .getLogger(ThreadRequestsGeneratorUnitTest.class.getName());

  private static final Map<String, String> testFiles = new HashMap<>();
  static
  {
    testFiles.put("jobs-mult-docker",           "${CCDP_HOME}/data/tasks/jobs_multi_docker.json");
    testFiles.put("jobs-mult",                  "${CCDP_HOME}/data/tasks/jobs_multi.json");
    testFiles.put("jobs-single",                "${CCDP_HOME}/data/tasks/jobs_single.json");
    testFiles.put("jobs-single-no-cmd",         "${CCDP_HOME}/data/tasks/jobs_single_no_command.json");
    testFiles.put("jobs-single-no-sid",         "${CCDP_HOME}/data/tasks/jobs_single_no_session_id.json");
    testFiles.put("jobs-single-no-sid-no-res",  "${CCDP_HOME}/data/tasks/jobs_single_no_session_id_no_resources.json");
    testFiles.put("tasks-mult",                 "${CCDP_HOME}/data/tasks/tasks_multi.json");
    testFiles.put("task-single-cmd",            "${CCDP_HOME}/data/tasks/tasks_single_cmd_only.json");
    testFiles.put("tasks-single",               "${CCDP_HOME}/data/tasks/tasks_single.json");
    testFiles.put("threads-mult-tasks",         "${CCDP_HOME}/data/tasks/threads_single_multi_tasks.json");
    testFiles.put("threads-single-task",        "${CCDP_HOME}/data/tasks/threads_single_task.json");
  }
  
  private String root = null;
  
  public ThreadRequestsGeneratorUnitTest()
  {
    
  }
  
  public void setUp()
  {
    String cfg_file = System.getProperty(CcdpUtils.CFG_KEY_CFG_FILE);
    try
    {
      CcdpUtils.loadProperties(cfg_file);
      CcdpUtils.configLogger();
      this.root = System.getenv("CCDP_HOME");
      this.logger.debug("Running from " + this.root);
    }
    catch( Exception e )
    {
      System.err.println("Could not setup environment");
    }
  }
  
  @Test
  public void testJobsMulti()
  {
    this.logger.debug("Testing Jobs Multi Tasks");
    String key = "jobs-mult";
    List<CcdpThreadRequest> reqs = this.getRequests(key);
    assertNotNull(reqs);
    assertEquals(reqs.size(), 2);
    
    for( CcdpThreadRequest req : reqs )
    {
      assertEquals(req.getTasksRunningMode(), TasksRunningMode.PARALLEL);
      assertEquals( req.getPendingTasks(), 1);
      assertEquals(1, req.getTasks().size());
      
    }
  }
  
  @Test
  public void testJobsMultiDocker()
  {
    this.logger.debug("Testing Jobs Multi Tasks and Docker");
    String key = "jobs-mult-docker";
    List<CcdpThreadRequest> reqs = this.getRequests(key);
    assertNotNull(reqs);
    assertEquals(reqs.size(), 2);
    
    for( CcdpThreadRequest req : reqs )
    {
      assertEquals(req.getTasksRunningMode(), TasksRunningMode.PARALLEL);
      assertEquals( req.getPendingTasks(), 1);
      assertEquals(1, req.getTasks().size());
      assertEquals(1, req.getTasks().size());
      CcdpTaskRequest task = req.getNextTask();
      assertEquals(task.getCPU(), 1.0, 0.10);
      assertEquals(task.getMEM(), 128.0, 0.01);
    }
  }
  
  @Test
  public void testJobsSingle()
  {
    this.logger.debug("Testing Jobs Single");
    String key = "jobs-single";
    List<CcdpThreadRequest> reqs = this.getRequests(key);
    assertNotNull(reqs);
    assertEquals(reqs.size(), 1);
    
    for( CcdpThreadRequest req : reqs )
    {
      assertEquals(req.getTasksRunningMode(), TasksRunningMode.PARALLEL);
      assertEquals( req.getPendingTasks(), 1);
      assertEquals(1, req.getTasks().size());
      CcdpTaskRequest task = req.getNextTask();
      assertEquals(task.getCPU(), 0.1, 0.01);
      assertEquals(task.getMEM(), 128.0, 0.01);
      assertEquals(task.getCommand().size(), 5);
    }
  }
  

  @Test
  public void testJobsSingleNoCmd()
  {
    this.logger.debug("Testing Jobs Single without command");
    String key = "jobs-single-no-cmd";
    List<CcdpThreadRequest> reqs = this.getRequests(key);
    assertNull(reqs);
  }
  
  @Test
  public void testJobsSingleNoSid()
  {
    this.logger.debug("Testing Jobs Single No Sid");
    String key = "jobs-single-no-sid";
    List<CcdpThreadRequest> reqs = this.getRequests(key);
    assertNotNull(reqs);
    assertEquals(reqs.size(), 1);
    
    for( CcdpThreadRequest req : reqs )
    {
      assertEquals(req.getTasksRunningMode(), TasksRunningMode.PARALLEL);
      assertEquals( req.getPendingTasks(), 1);
      assertEquals(1, req.getTasks().size());
      CcdpTaskRequest task = req.getNextTask();
      assertEquals(task.getCPU(), 0.1, 0.01);
      assertEquals(task.getMEM(), 128.0, 0.01);
      assertEquals(task.getCommand().size(), 5);
      assertNull(task.getSessionId());
    }
  }
  
  @Test
  public void testJobsSingleNoSidNoRes()
  {
    this.logger.debug("Testing Jobs Single No Sid No Res");
    String key = "jobs-single-no-sid-no-res";
    List<CcdpThreadRequest> reqs = this.getRequests(key);
    assertNotNull(reqs);
    assertEquals(reqs.size(), 1);
    
    for( CcdpThreadRequest req : reqs )
    {
      assertEquals(req.getTasksRunningMode(), TasksRunningMode.PARALLEL);
      assertEquals( req.getPendingTasks(), 1);
      assertEquals(1, req.getTasks().size());
      CcdpTaskRequest task = req.getNextTask();
      assertEquals(task.getCPU(), 0.0, 0.01);
      assertEquals(task.getMEM(), 0.0, 0.01);
      assertEquals(task.getCommand().size(), 5);
      assertNull(task.getSessionId());
    }
  }
  
  
  @Test
  public void testTasksMulti()
  {
    this.logger.debug("Testing Tasks Multi Tasks");
    String key = "tasks-mult";
    List<CcdpThreadRequest> reqs = this.getRequests(key);
    assertNotNull(reqs);
    assertEquals(reqs.size(), 1);
    
    for( CcdpThreadRequest req : reqs )
    {
      assertEquals(req.getTasksRunningMode(), TasksRunningMode.PARALLEL);
      assertEquals( req.getPendingTasks(), 3);
      assertEquals(3, req.getTasks().size());
      CcdpTaskRequest task = req.getNextTask();
      task.setSubmitted(true);
      assertEquals(task.getTaskId(), "task-123");
      assertEquals(task.getCPU(), 10.0, 0.01);
      assertEquals(task.getCommand().size(), 3);
      
      task = req.getNextTask();
      task.setSubmitted(true);
      assertEquals(task.getCPU(), 100.0, 0.01);
      assertEquals(task.getCommand().size(), 6);
      
      task = req.getNextTask();
      task.setSubmitted(true);
      assertEquals(task.getCPU(), 0.0, 0.01);
      assertEquals(task.getCommand().size(), 4);
    }
  }
  
  @Test
  public void testTasksSingleCmdOnly()
  {
    this.logger.debug("Testing Tasks Single Command only");
    String key = "task-single-cmd";
    List<CcdpThreadRequest> reqs = this.getRequests(key);
    assertNotNull(reqs);
    assertEquals(reqs.size(), 1);
    
    for( CcdpThreadRequest req : reqs )
    {
      assertEquals(req.getTasksRunningMode(), TasksRunningMode.PARALLEL);
      assertEquals( req.getPendingTasks(), 1);
      assertEquals(1, req.getTasks().size());
      CcdpTaskRequest task = req.getNextTask();
      task.setSubmitted(true);
      assertNotNull(task.getTaskId());
      assertEquals(task.getCPU(), 0.0, 0.01);
      assertEquals(task.getCommand().size(), 2);
      assertNull( req.getNextTask() );
    }
  }
  
  @Test
  public void testTasksSingle()
  {
    this.logger.debug("Testing Tasks Single");
    String key = "tasks-single";
    List<CcdpThreadRequest> reqs = this.getRequests(key);
    assertNotNull(reqs);
    assertEquals(reqs.size(), 1);
    
    for( CcdpThreadRequest req : reqs )
    {
      assertEquals(req.getTasksRunningMode(), TasksRunningMode.PARALLEL);
      assertEquals( req.getPendingTasks(), 1);
      assertEquals(1, req.getTasks().size());
      CcdpTaskRequest task = req.getNextTask();
      task.setSubmitted(true);
      assertEquals(task.getTaskId(), "csv_reader");
      assertEquals(task.getClassName(), "tasks.csv_demo.CsvReader");
      assertEquals(task.getNodeType(), CcdpNodeType.EC2);
      
      assertEquals(task.getCPU(), 10.0, 0.01);
      assertEquals(task.getMEM(), 128.0, 0.01);
      assertEquals(task.getCommand().size(), 2);
      assertEquals(task.getConfiguration().size(), 1);
      assertNull( req.getNextTask() );
    }
  }
  
  @Test
  public void testThreadsSingleTask()
  {
    this.logger.debug("Testing Threads Single Task");
    String key = "threads-single-task";
    List<CcdpThreadRequest> reqs = this.getRequests(key);
    assertNotNull(reqs);
    assertEquals(reqs.size(), 1);
    
    for( CcdpThreadRequest req : reqs )
    {
      assertEquals(req.getThreadId(), "thread-1");
      assertEquals(req.getName(), "CSV 2 JSON Converter");
      assertEquals(req.getDescription(), "Converts CSV files into JSON strings");
      assertEquals(req.getTasksRunningMode(), TasksRunningMode.SEQUENCE);
      assertEquals( req.getPendingTasks(), 1);
      assertEquals(1, req.getTasks().size());
      CcdpTaskRequest task = req.getNextTask();
      task.setSubmitted(true);
      assertEquals(task.getTaskId(), "csv_reader");
      assertEquals(task.getName(), "Csv File Reader");
      assertEquals(task.getNodeType(), CcdpNodeType.EC2);
      
      assertEquals(task.getCPU(), 0.0, 0.1);
      assertEquals(task.getMEM(), 0.0, 0.1);
      assertEquals(task.getCommand().size(), 2);
      assertEquals(task.getConfiguration().size(), 1);
      assertNull( req.getNextTask() );
    }
  }
  
  @Test
  public void testThreadsMultiTask()
  {
    this.logger.debug("Testing Threads Multiple Tasks");
    String key = "threads-mult-tasks";
    List<CcdpThreadRequest> reqs = this.getRequests(key);
    assertNotNull(reqs);
    assertEquals(reqs.size(), 1);
    List<String> expected = Arrays.asList("The Sender", "Someone Else");
    
    for( CcdpThreadRequest req : reqs )
    {
      assertEquals(req.getThreadId(), "thread-1");
      assertEquals(req.getName(), "PI Estmator");
      assertEquals(req.getReplyTo(), "The Sender");
      assertEquals(req.getDescription(), "Estimates the value of PI");
      assertEquals(req.getTasksRunningMode(), TasksRunningMode.PARALLEL);
      
      assertEquals(3, req.getTasks().size());
      for( CcdpTaskRequest task : req.getTasks() )
      {
        assertEquals(task.getNodeType(),CcdpNodeType.EMS);
        assertEquals(task.getCPU(), 0.0, 0.01);
        assertEquals(task.getMEM(), 0.0, 0.01);
        task.setSubmitted(true);
      }
      assertNull( req.getNextTask() );
    }
  }
  
  private List<CcdpThreadRequest> getRequests(String key)
  {
    String fname = testFiles.get(key);
    try
    {
      fname = CcdpUtils.expandVars(fname);
      this.logger.debug("Using File " + fname);
      return CcdpUtils.toCcdpThreadRequest(new File(fname));
    }
    catch( Exception e )
    {
      return null;
    }
    
  }
  
  
  
  public void tearDown()
  {
    this.logger.info("Done with test");
  }
  
  
}
