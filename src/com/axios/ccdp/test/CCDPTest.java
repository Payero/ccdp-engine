package com.axios.ccdp.test;


import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.log4j.Logger;

import com.axios.ccdp.connections.intfs.CcdpVMControllerIntf;
import com.axios.ccdp.factory.CcdpObjectFactory;
import com.axios.ccdp.resources.CcdpVMResource;
import com.axios.ccdp.tasking.CcdpPort;
import com.axios.ccdp.tasking.CcdpTaskRequest;
import com.axios.ccdp.tasking.CcdpThreadRequest;
import com.axios.ccdp.tasking.CcdpThreadRequest.TasksRunningMode;
import com.axios.ccdp.utils.CcdpImageInfo;
import com.axios.ccdp.utils.CcdpUtils;


public class CCDPTest 
{

  /**
   * Generates debug print statements based on the verbosity level.
   */
  private Logger logger = Logger.getLogger(CCDPTest.class.getName());
  
  
  public CCDPTest()
  {
    this.logger.debug("Running CCDP Test");
    try
    {
      this.runTest();
    }
    catch( Exception e )
    {
      this.logger.error("Message: " + e.getMessage(), e);
    }
    
  }
  
  
  private void runTest() throws Exception
  {
    this.logger.debug("Running the Test");
    
    CcdpTaskRequest task1 = new CcdpTaskRequest();
    task1.setName("task-1");
    
    CcdpTaskRequest task2 = new CcdpTaskRequest();
    task2.setName("task-2");
    
    CcdpTaskRequest task3 = new CcdpTaskRequest();
    task3.setName("task-3");
    
    
    CcdpVMResource vm1 = new CcdpVMResource();
    vm1.setAgentId("agent-1");
    vm1.getTasks().add(task1);
    
    CcdpVMResource vm2 = new CcdpVMResource();
    vm2.setAgentId("agent-2");
    vm2.getTasks().add(task1);
    vm2.getTasks().add(task1);
    vm2.getTasks().add(task2);
    vm2.getTasks().add(task2);
    vm2.getTasks().add(task3);
    vm2.getTasks().add(task3);
    vm2.getTasks().add(task3);
    vm2.getTasks().add(task3);
    
    CcdpVMResource vm3 = new CcdpVMResource();
    vm3.setAgentId("agent-3");
    vm3.getTasks().add(task1);
    vm3.getTasks().add(task2);
    vm3.getTasks().add(task3);
    
    CcdpVMResource vm4 = new CcdpVMResource();
    vm4.setAgentId("agent-4");
    vm4.getTasks().add(task3);
    vm4.getTasks().add(task3);
    vm4.getTasks().add(task3);
    
    List<CcdpVMResource> resources = new ArrayList<>();
    resources.add(vm1);
    resources.add(vm2);
    resources.add(vm3);
    resources.add(vm4);
    
    String name = "task-2";
    List<CcdpVMResource> sorted = this.getSortedList(name, resources);
    
    int to_kill = 4;
    int remaining = to_kill;
    boolean done = false;
    
    for( CcdpVMResource vm : sorted )
    {
      if( done )
      {
        this.logger.info("Done killing tasks");
        break;
      }
        
      
      for( CcdpTaskRequest task : vm.getTasks() )
      {
        if( name.equals(task.getName() ) )
        {
          this.logger.info("Found a matching task in " + vm.getAgentId());
          remaining--;
          if(remaining <= 0 )
          {
            done = true;
            break;
          }
        }
      }
    }
    
    if( !done )
    {
      this.logger.error("Got a request to kill more tasks than are currently running");
    }
  }
  
  private List<CcdpVMResource> getSortedList(String name, List<CcdpVMResource> resources )
  {
   Collections.sort(resources, new NumberTasksComparator(name));
   Collections.reverse(resources);
   return resources;
  }
  
  public class NumberTasksComparator implements Comparator<CcdpVMResource>
  {
    private String taskName = null;
    
    public NumberTasksComparator( String taskName )
    {
      this.taskName = taskName;
    }
    
    @Override
    public int compare(CcdpVMResource res1, CcdpVMResource res2)
    {
      Integer res1_tasks = 0;
      Integer res2_tasks = 0;
      for( CcdpTaskRequest task : res1.getTasks() )
      {
        if( this.taskName.equals( task.getName() ) )
          res1_tasks++;
      }
      
      for( CcdpTaskRequest task : res2.getTasks() )
      {
        if( this.taskName.equals( task.getName() ) )
          res2_tasks++;
      }
      
      return res1_tasks.compareTo(res2_tasks);
    }    
  }
  
  
  
  public static void main( String[] args ) throws Exception
  {
    String cfg_file = System.getProperty("ccdp.config.file");
    CcdpUtils.loadProperties(cfg_file);
    CcdpUtils.configLogger();
    
    new CCDPTest();
  }

}



final class Tuple2<T1, T2> 
{
  public final T1 _1;
  
  public final T2 _2;

  public Tuple2( final T1 v1,  final T2 v2) {
      _1 = v1;
      _2 = v2;
  }

  public static <T1, T2> Tuple2<T1, T2> create( final T1 v1,  final T2 v2) {
    System.out.println("T1: " + v1.getClass().getName() + " T2: "+ v2.getClass().getName());
    return new Tuple2<>(v1, v2);
  }

  public static <T1, T2> Tuple2<T1, T2> t( final T1 v1,  final T2 v2) {
      return create(v1, v2);
  }
}
