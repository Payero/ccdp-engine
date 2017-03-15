/**
 * 
 */
package com.axios.ccdp.mesos.controllers.aws;

import java.util.ArrayList;
import java.util.List;

import org.apache.log4j.Logger;

import com.axios.ccdp.mesos.connections.intfs.CcdpTaskingControllerIntf;
import com.axios.ccdp.mesos.resources.CcdpVMResource;
import com.axios.ccdp.mesos.resources.CcdpVMResource.ResourceStatus;
import com.axios.ccdp.mesos.tasking.CcdpTaskRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sun.xml.bind.v2.TODO;

/**
 * @author Oscar E. Ganteaume
 *
 */
public class AWSCcdpTaskingControllerImpl implements CcdpTaskingControllerIntf
{

  /**
   * Generates debug print statements based on the verbosity level.
   */
  private Logger logger = Logger.getLogger(AWSCcdpTaskingControllerImpl.class
      .getName());
  
  /**
   * Creates all the ObjectNode and ArrayNode required by this object
   */
  private ObjectMapper mapper = new ObjectMapper();

  /**
   * Stores the configuration passed to this object
   */
  private ObjectNode config = null;
  
  /**
   * Instantiates a new object and starts receiving and processing incoming 
   * assignments    
   */
  public AWSCcdpTaskingControllerImpl()
  {
    this.logger.debug("Initiating Tasker object");
    // making the JSON Objects Pretty Print
    this.mapper.enable(SerializationFeature.INDENT_OUTPUT);
    this.config = this.mapper.createObjectNode();
  }

  /**
   * Sets all the parameters required for this object to determine resource
   * allocation and deallocation.
   * 
   */
  public void configure(ObjectNode config)
  {
    if( config != null )
    {
      this.config = config;
      this.logger.info("Configuring Tasker using " + this.config.toString());
    }
  }

  /**
   * Determines whether or not additional resources are needed based on
   * the utilization level of the given resources.  If the resources combined
   * reaches the threshold then it returns true otherwise it returns false.
   * 
   * @param resources the list of resources to test for need of additional 
   *        resources
   */
  public boolean needResourceAllocation(List<CcdpVMResource> resources)
  {
    // TODO Auto-generated method stub
    return false;
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
  public List<CcdpVMResource> deallocateResource(List<CcdpVMResource> resources)
  {
    // TODO Auto-generated method stub
    return null;
  }
  
  /**
   * Assigns all the tasks in the given list to the target VM based on 
   * resources availability and other conditions.
   * 
   * @param tasks a list of tasks to consider running in the intended VM
   * @param target the VM candidate to run the tasks
   * @param resources all available resources that could be potentially
   *        used to run this tasks
   */
  public List<CcdpTaskRequest> assignTasks(List<CcdpTaskRequest> tasks, 
                                           CcdpVMResource target,
                                           List<CcdpVMResource> resources)
  {
    List<CcdpTaskRequest> assigned = new ArrayList<>();
    
    for( CcdpTaskRequest task: tasks )
    {
      double cpu = task.getCPU();
      
      
      // CPU = 0 means use any logic
      // 0 > CPU < 100 means assign it where it fits
      // CPU > 100 means run this task by itself on a node
      //
      if( cpu == 0 )
      {
        this.logger.info("CPU = " + cpu + " Assigning Task based on session");
        if( this.isLeastUtilized(task, target, resources) )
        {
          task.setSubmitted(true);
          task.assigned();
          target.addTask(task);
          assigned.add( task );
        }
      }
      else if( cpu >= 100 )
      {
        this.logger.info("CPU = " + cpu + " Assigning a Resource just for this task");
        if( this.canAssignResource(task, target) )
        {
          //TODO Chaning the task's cpu, is that good?
          this.logger.info("Setting the Task's CPU to max amount for this resource");
          task.setCPU(target.getCPU());
          task.setSubmitted(true);
          task.assigned();
          target.addTask(task);
          assigned.add( task );
        }
      }
      else
      {
        this.logger.info("CPU = " + cpu + " Assigning Task using First Fit");
        if( this.canRunTask(task, target) )
        {
          task.setSubmitted(true);
          task.assigned();
          target.addTask(task);
          assigned.add( task );
        }
      }
    }
    
    return assigned;
  }

  
  
  /**
   * Uses the list of resources to determine which one is the least
   * utilized.  If the target resource is the least utilized then 
   * it returns true otherwise it returns false.
   * 
   * @param target the intended VM to run the task
   * @param resources a list of available VMs 
   * 
   * @return true if the task has not been submitted and the target VM is the
   *         least utilized in the list
   */
  private boolean isLeastUtilized(CcdpTaskRequest task, CcdpVMResource target, 
                                  List<CcdpVMResource> resources)
  {
    this.logger.debug("Finding least used VM of the session");
    
    if( task.isSubmitted() )
    {
      this.logger.debug("Job already submitted, skipping it");
      return false;
    }
    
    CcdpVMResource vm = CcdpVMResource.leastUsed(resources);
    if( vm != null && vm.equals(target))
    {
      return true;
    }
    return false;
  }
  
  
  /**
   * Checks the amount of CPU and memory available in this VM resource.  If the VM has enough resources to run the
   * task then it returns the CcdpTaskRequest otherwise it returns null
   * 
   * @param task the task to assign to a resource
   * @param resource the resource to determine whether or not it can be run this task
   * 
   * @return true if this task can be executed on this node
   */
   private boolean canRunTask( CcdpTaskRequest task, CcdpVMResource resource )
   {
     this.logger.debug("Running First Fit");
     if( task.isSubmitted() )
     {
       this.logger.debug("Job already submitted, skipping it");
       return false;
     }
     
     // if the VM is not running then do not assign task to it
     ResourceStatus status = resource.getStatus();
     if( !ResourceStatus.RUNNING.equals(status) )
     {
       String msg = "VM " + resource.getInstanceId() + " not running " + status.toString(); 
       this.logger.info(msg);
       return false;
     }
     
     double offerCpus = resource.getCPU();
     double offerMem = resource.getMEM();
     
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
       
       return true;
     }
     
     return false;
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
   private boolean canAssignResource(CcdpTaskRequest task, CcdpVMResource resource )
   {
     String tid = task.getTaskId();
     if( tid != null && tid.equals( resource.getSingleTask() ) )
     {
       if( resource.getStatus().equals(ResourceStatus.RUNNING) )
       {
         String iid = resource.getInstanceId();
         this.logger.info("VM " + iid + " was assigned to " + tid);
         return true;
       }
       else
       {
         this.logger.info("Found assigned resource but is not RUNNING");
       }
     }
     
     return false;
   }
}
