/**
 * 
 */
package com.axios.ccdp.impl.controllers;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.log4j.Logger;

import com.axios.ccdp.intfs.CcdpTaskingControllerIntf;
import com.axios.ccdp.resources.CcdpImageInfo;
import com.axios.ccdp.resources.CcdpVMResource;
import com.axios.ccdp.resources.CcdpVMResource.ResourceStatus;
import com.axios.ccdp.tasking.CcdpTaskRequest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Class used to define the basic functionality of determining when to start
 * or to stop a resource.  
 * 
 * When allocating resources this class uses the CPU to determine how to 
 * evaluate the need for resources or not as follow:
 * 
 *  CPU = 0:  invokes the abstract method customTaskAssignment(task, resources)
 *  CPU = 100: Finds an empty resource to allocate the task
 *  else:      Uses the first fit algorithm to determine where to run the task
 * 
 * 
 * @author Oscar E. Ganteaume
 *
 */
public abstract class CcdpVMControllerAbs implements CcdpTaskingControllerIntf
{

  /**
   * Generates debug print statements based on the verbosity level.
   */
  private Logger logger = Logger.getLogger(CcdpVMControllerAbs.class
      .getName());
  
  /**
   * Creates all the ObjectNode and ArrayNode required by this object
   */
  protected ObjectMapper mapper = new ObjectMapper();

  /**
   * Stores the configuration passed to this object
   */
  protected ObjectNode config = null;

  
  /**
   * Instantiates a new object and starts receiving and processing incoming 
   * assignments    
   */
  public CcdpVMControllerAbs()
  {
    this.logger.debug("Initiating Tasker object");
    // making the JSON Objects Pretty Print
    this.mapper.enable(SerializationFeature.INDENT_OUTPUT);
    this.config = this.mapper.createObjectNode();
  }

  /**
   * Sets all the parameters required for this object to determine resource
   * allocation and deallocation.  This method needs to be implemented by each 
   * child class as they would know what is required to determine when to 
   * allocate or deallocate resources
   * 
   * @param config the configuration used to set the allocation/deallocation
   *        parameters
   */
  public abstract void configure(JsonNode config);

  /**
   * Determines whether or not additional resources are needed based on
   * the utilization level of the given resources.  If there is a need for more
   * resources then it returns the image information to launch otherwise it 
   * returns null.
   * 
   * @param resources the list of resources to test for need of additional 
   *        resources
   * @return The image configuration required to launch more resources or null
   *         if no additional resources are needed
   */
  public abstract CcdpImageInfo 
                        allocateResources(List<CcdpVMResource> resources);
  
  
  /**
   * Determines whether or not VM resources need to be terminated due to 
   * low utilization or need.  If one or more of the current RUNNING resources
   * falls below the threshold then is added to the list. 
   * 
   * @param resources the list of resources to test for need to deallocate
   * 
   * @return a list of resources that need to be terminated
   */
  public abstract List<CcdpVMResource> 
                        deallocateResources(List<CcdpVMResource> resources);
  
  
  /**
   * Allows custom code to be invoked automatically when the CPU is set to zero
   * (0).  Each child class needs to implement this method
   * 
   * @param task the task or action that needs to be executed
   * @param resources a list of all available resources to run this task
   * 
   * @return the resource selected to run the task or null if none found
   */
  protected abstract CcdpVMResource 
    customTaskAssignment(CcdpTaskRequest task, List<CcdpVMResource> resources);
  
  
  /**
   * Assigns all the tasks in the given list to the target VM based on 
   * resources availability and other conditions.
   * 
   * CPU = 0        means use any logic
   * 0 &gt; CPU &lt; 100  means assign it where it fits
   * CPU &gt; 100      means run this task by itself on a node
   * 
   * @param tasks a list of tasks to consider running in the intended VM
   * @param resources all available resources that could be potentially
   *        used to run this tasks
   * 
   * @return a map specifying which tasks can be run on which resource
   */
  public Map<CcdpVMResource, List<CcdpTaskRequest>> 
        assignTasks(List<CcdpTaskRequest> tasks, List<CcdpVMResource> resources)
  {
    Map<CcdpVMResource, List<CcdpTaskRequest>> tasked = new HashMap<>();
    
    if( resources == null || resources.isEmpty() )
      return tasked;
    
    for( CcdpTaskRequest task: tasks )
    {
      double cpu = task.getCPU();
      // cannot assigned less mem than required
      if(task.getMEM() < CcdpTaskRequest.MIN_MEM_REQ )
        task.setMEM(CcdpTaskRequest.MIN_MEM_REQ);
      
      // don't send the same task twice
      if (task.isSubmitted())
         continue;
      
      // CPU = 0 means use any logic
      // 0 > CPU < 100 means assign it where it fits
      // CPU > 100 means run this task by itself on a node
      //
      if( cpu == 0 )
      {
        this.logger.info("CPU = " + cpu + " Assigning Task based on session");
        CcdpVMResource target = this.customTaskAssignment(task, resources);
        if (target == null) 
        {
          this.logger.error("The target is null!");
          return tasked;
        }
   
        String iid = target.getInstanceId();
        task.assigned();
        task.setHostId(iid);
        if( !tasked.containsKey( target ) )
          tasked.put(target, new ArrayList<CcdpTaskRequest>());
        tasked.get(target).add( task );
      }
      else if( cpu >= 100 )
      {
        this.logger.info("CPU = " + cpu + " Assigning a Resource just for this task");
        CcdpVMResource target = this.getAssignedResource(task, resources);
        if( target != null  )
        {
          String iid = target.getInstanceId();
          task.assigned();
          task.setHostId( iid );
          if( !tasked.containsKey( target ) )
            tasked.put(target, new ArrayList<CcdpTaskRequest>());
          tasked.get(target).add( task );
        }
      }
      else
      {
        this.logger.info("CPU = " + cpu + " Assigning Task using First Fit");
        CcdpVMResource target = this.getFirstFit(task, resources);
        if( target != null  )
        {
          String iid = target.getInstanceId();
          task.assigned();
          task.setHostId( iid );
          if( !tasked.containsKey( target ) )
            tasked.put(target, new ArrayList<CcdpTaskRequest>());
          tasked.get(target).add( task );
        }
      }
    }
    
    return tasked;
  }

  
  
  
  /**
   * Checks the amount of CPU and memory available in this VM resource.  If the 
   * VM has enough resources to run the task then it returns the CcdpTaskRequest 
   * otherwise it returns null
   * 
   * @param task the task to assign to a resource
   * @param resource the resource to determine whether or not it can be run 
   *        this task
   * 
   * @return true if this task can be executed on this node
   */
   private CcdpVMResource getFirstFit( CcdpTaskRequest task, 
       List<CcdpVMResource> resources )
   {
     this.logger.debug("Running First Fit");
     if( task.isSubmitted() )
     {
       this.logger.debug("Job already submitted, skipping it");
       return null;
     }
     
     boolean foundRunningVm = false;
     
     for( CcdpVMResource resource : resources )
     {
       // if the VM is not running then do not assign task to it
       ResourceStatus status = resource.getStatus();
       if( !ResourceStatus.RUNNING.equals(status) )
       {
         String msg = "VM " + resource.getInstanceId() + 
                      " not running " + status.toString(); 
         this.logger.debug(msg);
         continue;
       }
       
       double offerCpus = resource.getCPU();
       double offerMem = resource.getTotalMemory();
       
       String str = 
       String.format("Offer CPUs: %f, Memory: %f", offerCpus, offerMem);
       this.logger.debug(str);
       
       double jobCpus = task.getCPU();
       double jobMem = task.getMEM();
       this.logger.debug("Job Cpus: " + jobCpus + " Job Mem: " + jobMem);
       // does the offer has more resources than needed?
       if( jobCpus <= offerCpus && jobMem <= offerMem )
       {
         this.logger.info("Enough resources for a new Job");
         offerCpus -= jobCpus;
         offerMem -= jobMem;
         foundRunningVm = true;
         return resource;
       }
     }
     this.logger.debug("foundRunningVm= " + foundRunningVm);
     //if we did not find a running resource lets check if we have launched resources
     if(!foundRunningVm) {
       for( CcdpVMResource resource : resources )
       {
         // if the VM is not Launched then do not assign task to it
         ResourceStatus status = resource.getStatus();
         if( !ResourceStatus.LAUNCHED.equals(status) )
         {
           String msg = "VM " + resource.getInstanceId() + 
                        " not Launched " + status.toString(); 
           this.logger.debug(msg);
           continue;
         }
         
         double offerCpus = resource.getCPU();
         double offerMem = resource.getTotalMemory();
         
         String str = 
         String.format("Offer CPUs: %f, Memory: %f", offerCpus, offerMem);
         this.logger.debug(str);
         
         double jobCpus = task.getCPU();
         double jobMem = task.getMEM();
         this.logger.debug("Job Cpus: " + jobCpus + " Job Mem: " + jobMem);
         // does the offer has more resources than needed?
         if( jobCpus <= offerCpus && jobMem <= offerMem )
         {
           this.logger.info("Enough resources for a new Job");
           offerCpus -= jobCpus;
           offerMem -= jobMem;
           foundRunningVm = true;
           return resource;
         }
       }
     }
      
     return null;
   }
   
   /**
    * Finds the VM that is assigned to this task.  This method is invoked when 
    * a task requires to be executed alone on a VM.  If the VM is found and its
    * ResourceStatus is set to RUNNING then it returns true otherwise it returns
    * false
    * 
    * @param task the task to assign to the VM 
    * @param resource the VM to test for assignment
    * 
    * @return true if the VM is found and is RUNNING
    */
   private CcdpVMResource getAssignedResource(CcdpTaskRequest task, 
       List<CcdpVMResource> resources )
   {
     String hid = task.getHostId();
     
     for( CcdpVMResource resource : resources )
     {
       String id = resource.getInstanceId();
       this.logger.debug("Comparing Host " + id + " and task's Host Id " + hid);
       if( hid != null && hid.equals( id ) )
       {
         this.logger.debug(resource.getInstanceId() + " Status: " + 
                           resource.getStatus() );
         if( resource.getStatus().equals(ResourceStatus.RUNNING) )
         {
           String tid = task.getTaskId();
           this.logger.info("VM " + id + " was assigned to task " + tid);
           return resource;
         }
         else
         {
           this.logger.info("Found assigned resource but is not RUNNING");
         }
       }
     }
     
     return null;
   }
   
  
  /***************************************************************************
   *
   * The following three methods is to allow the factory to use reflection and
   * being able to create the objects the same way as mesos.
   * 
   ***************************************************************************/
  public  List<CcdpTaskRequest> assignTasks(List<CcdpTaskRequest> tasks, 
                                            CcdpVMResource target,
                                            List<CcdpVMResource> considering)
  {
    this.logger.warn("Empty Method to allow same construction as Mesos!!");
    return null;
  }

  public  boolean needResourceAllocation(List<CcdpVMResource> resources )
  {
    this.logger.warn("Empty Method to allow same construction as Mesos!!");
    return false;
  }
  
  public  List<CcdpVMResource> deallocateResource(List<CcdpVMResource> resources)
  {
    this.logger.warn("Empty Method to allow same construction as Mesos!!");
    return null;
  }

}

