/**
 * 
 */
package com.axios.ccdp.impl.controllers;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.apache.log4j.Logger;

import com.axios.ccdp.resources.CcdpImageInfo;
import com.axios.ccdp.resources.CcdpVMResource;
import com.axios.ccdp.resources.CcdpVMResource.ResourceStatus;
import com.axios.ccdp.tasking.CcdpTaskRequest;
import com.axios.ccdp.utils.CcdpUtils;
import com.fasterxml.jackson.databind.JsonNode;

/**
 * @author Oscar E. Ganteaume
 *
 */
public class NumberTasksControllerImpl extends CcdpVMControllerAbs
{
  private Logger logger = Logger.getLogger(NumberTasksControllerImpl.class);
  
  /**
   * The default value of the maximum number of tasks to execute
   */
  public static int DEF_MAX_NUMBER_TASKS = 5;
  /**
   * The default maximum number of minutes since the VM was last tasked
   */
  public static int DEF_MAX_IDLE_TIME = 5;
  /**
   * The maximum number of tasks to run on a given VM
   */
  private int max_tasks = DEF_MAX_NUMBER_TASKS;
  /**
   * The maximum number of minutes since the VM was last tasked
   */
  private int max_time = DEF_MAX_IDLE_TIME;
  
  /**
   * Instantiates a new object and starts receiving and processing incoming 
   * assignments    
   */
  public NumberTasksControllerImpl()
  {
    super();
    this.logger.debug("New NumberTasksControllerImpl called");
  }

  /**
   * Sets all the parameters required for this object to determine resource
   * allocation and deallocation.  If the CPU, Memory, and time are set in the
   * configuration file then it uses those values otherwise it uses the 
   * following:
   * 
   *  max.number.tasks: The total number of tasks to be run per VM
   *  max.waiting.time: The time in minutes since the last allocation.  If the 
   *                    difference between the current time and the last time 
   *                    this resource was allocated is greater than this time
   *                    the resource is terminated
   *   
   * 
   * @param config the configuration used to set the allocation/deallocation
   *        parameters
   */
  public void configure(JsonNode config)
  {
    if( config == null )
      throw new RuntimeException("The configuration cannot be null");
    
    JsonNode alloc = config.get("allocate");
    int tmp = this.getParam(alloc, "no-more-than");
    if( tmp > 0 )
      this.max_tasks = tmp;
    else
      this.max_tasks = DEF_MAX_NUMBER_TASKS;
    
    JsonNode dealloc = config.get("deallocate");
    tmp = this.getParam(dealloc, "avg-time-load");
    if( tmp > 0 )
      this.max_time = tmp;
    else
      this.max_time = DEF_MAX_IDLE_TIME;
  }

  /**
   * Checks if the field was set in the ObjectNode object and if found and is
   * set to an integer greater than 0 it set the given field to that parameter
   * 
   * @param config The object containing the desired configuration
   * @param field the name of the field to extract
   * @param param the parameter to set
   */
  private int getParam(JsonNode config, String field)
  {
    this.logger.trace("Looking for " + field);
    if( config.has(field) )
    {
      this.logger.trace("Found it");
      int tmp = config.get(field).asInt();
      this.logger.trace("The vaule is " + tmp);
      if( tmp > 0 )
      {
        return tmp;
      }
      else
      {
        String msg = "The field " + field + " needs to be a positive integer";
        this.logger.warn(msg);
      }
    }
    else
    {
      this.logger.warn("The field " + field + " was not found");
    }
    return -1;
  }
  
  /**
   * Determines whether or not additional resources are needed based on
   * the utilization level of the given resources.  If the resources combined
   * reaches the threshold then it returns true otherwise it returns false.
   * 
   * @param resources the list of resources to test for need of additional 
   *        resources
   * @return The image configuration required to launch more resources or null
   *         if no additional resources are needed
   * 
   */
  public CcdpImageInfo allocateResources(List<CcdpVMResource> resources)
  {
    this.logger.trace("Checking resource allocation");
    CcdpImageInfo imgCfg = null;
    if( resources == null || resources.size() == 0 )
      return imgCfg;
    
    List<CcdpVMResource> avail = new ArrayList<>();
    for( CcdpVMResource tmp : resources )
    {
      if( !tmp.isSingleTasked() )
        avail.add(tmp);
    }
    
    if( avail.size() == 0 )
      return imgCfg;
    
    String type = avail.get(0).getNodeType();
    boolean are_diff = false;
    for( CcdpVMResource vm : avail )
    {
      String t = vm.getNodeType();
      if( !t.equals(type) )
      {
        are_diff = true;
        break;
      }
    }
    
    if( are_diff )
      this.logger.warn("Has more than one type of node, returning first one");
    
    int sz = avail.size();
    this.logger.trace("Resources size: " + resources.size() + ", Available: " + sz);
    this.logger.trace("Using Max number of Tasks = "+ this.max_tasks);
    
    int total_tasks = 0;
    for( CcdpVMResource res : avail )
      total_tasks += res.getNumberTasks();
    
    int avgLoad = (int)( total_tasks / sz);
    
    if( avgLoad > this.max_tasks )
    {
      String txt = "Need Resources: the Average Load " + avgLoad + 
          " is greater than allowed " + this.max_tasks;
      this.logger.info(txt);
      imgCfg = CcdpImageInfo.copyImageInfo(CcdpUtils.getImageInfo(type));
    }
    else
    {
      String txt = "Does not need Resources: the Average Load " + avgLoad + 
          " is lower than allowed " + this.max_tasks;
      this.logger.trace(txt);
    }
    
    return imgCfg;
  }
  
  /**
   * Determines whether or not VM resources need to be terminated due to 
   * low utilization or need.  If one or more of the current RUNNING resources
   * falls below the threshold then is added to the list. 
   * 
   * @param resources the list of resources to test for need to deallocate
   * 
   * @return a list of resources that need to be terminated
   */
  public List<CcdpVMResource> deallocateResources(List<CcdpVMResource> resources)
  {
    long now = System.currentTimeMillis();
    List<CcdpVMResource> terminate = new ArrayList<>();
    for( CcdpVMResource vm : resources )
    {
      long last = vm.getLastAssignmentTime();
      int diff = (int)( ( (now - last) / 1000) / 60 );
      StringBuffer buf = 
          new StringBuffer("\nInstanceId: " + vm.getInstanceId() + "\n");
      buf.append("Current State: " + vm.getStatus() + "\n");
      buf.append("Number of Tasks: " + vm.getNumberTasks() + "\n");
      buf.append("Single Tasked? " + vm.isSingleTasked() + "\n");
      buf.append("Last Assignment: " + new Date(last) + "\n");
      buf.append("Time Diff: " + diff + "\n");
      buf.append("Max Time: " + this.max_tasks + "\n");
      
      this.logger.trace(buf.toString());
      
      // is the time of the last assignment greater than allowed and it was
      // running (avoiding new launches)
      if( diff >= this.max_time && 
          ResourceStatus.RUNNING.equals( vm.getStatus() ) &&
          vm.getTasks().size() == 0 &&
          !vm.isSingleTasked()
         )
      {
        this.logger.info("VM " + vm.getInstanceId() + 
         " has not been allocated for a while, " + "marked for termination");
        
        terminate.add(vm);  
      }
    }
    return terminate;
  }
  
  /**
   * Gets the first VM that is running less number of tasks than the maximum
   * number of tasks configured
   * 
   * @param task the task or action that needs to be executed
   * @param resources a list of all available resources to run this task
   * 
   * @return the resource selected to run the task or null if none found
   */
  protected CcdpVMResource 
    customTaskAssignment(CcdpTaskRequest task, List<CcdpVMResource> resources)
  {
    CcdpVMResource target = null;
    if( task.isSubmitted() )
    {
      this.logger.debug("Job already submitted, skipping it");
      return target;
    }
    
    for( CcdpVMResource vm : resources )
    {
      if( vm.isSingleTasked() )
      {
        this.logger.debug("Task is assigned to a dedicated VM: " + 
                          vm.getSingleTask() + " skipping it");
        continue;
      }
      
      int tasks = vm.getNumberTasks();
      if( tasks < this.max_tasks )
        return vm;
    }
    
    return null;
    
  }
  
}

