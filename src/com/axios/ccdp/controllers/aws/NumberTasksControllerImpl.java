/**
 * 
 */
package com.axios.ccdp.controllers.aws;

import java.util.ArrayList;
import java.util.List;

import org.apache.log4j.Logger;

import com.axios.ccdp.connections.intfs.CcdpTaskingControllerIntf;
import com.axios.ccdp.resources.CcdpVMResource;
import com.axios.ccdp.resources.CcdpVMResource.ResourceStatus;
import com.axios.ccdp.tasking.CcdpTaskRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * @author Oscar E. Ganteaume
 *
 */
public class NumberTasksControllerImpl implements CcdpTaskingControllerIntf
{
  /**
   * The default value of the maximum number of tasks to execute
   */
  public static int DEF_MAX_NUMBER_TASKS = 5;
  /**
   * The default maximum number of minutes since the VM was last tasked
   */
  public static int DEF_MAX_IDLE_TIME = 5;
  /**
   * Generates debug print statements based on the verbosity level.
   */
  private Logger logger = Logger.getLogger(NumberTasksControllerImpl.class
      .getName());
  /**
   * Creates all the ObjectNode and ArrayNode required by this object
   */
  private ObjectMapper mapper = new ObjectMapper();
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
    this.logger.debug("Initiating Tasker object");
    // making the JSON Objects Pretty Print
    this.mapper.enable(SerializationFeature.INDENT_OUTPUT);
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
  public void configure(ObjectNode config)
  {
    if( config == null )
    {
      throw new RuntimeException("The configuration cannot be null");
    }
    int tmp = this.getParam(config, "allocate.no.more.than");
    if( tmp > 0 )
      this.max_tasks = tmp;
    tmp = this.getParam(config, "allocate.no.more.than");
    if( tmp > 0 )
      this.max_time = tmp;
  }

  /**
   * Checks if the field was set in the ObjectNode object and if found and is
   * set to an integer greater than 0 it set the given field to that parameter
   * 
   * @param config The object containing the desired configuration
   * @param field the name of the field to extract
   * @param param the parameter to set
   */
  private int getParam(ObjectNode config, String field)
  {
    if( config.has(field) )
    {
      int tmp = config.get(field).asInt();
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
   * 
   * @return true if more resources need to be allocated or false otherwise
   * 
   */
  public boolean needResourceAllocation(List<CcdpVMResource> resources)
  {
    if( resources == null )
      return true;
    
    int sz = resources.size();
    this.logger.info("Using Max number of Tasks "+ this.max_tasks);
    
    if( sz > 0 )
    {
      int total_tasks = 0;
      for( CcdpVMResource res : resources )
      {
        total_tasks += res.getNumberTasks();
      }
      
      int avgLoad = (int)( total_tasks / sz);

      
      if( avgLoad >= this.max_tasks )
      {
        String txt = "Need Resources: the Average Load " + avgLoad + 
            " is greater than allowed " + this.max_tasks;
        this.logger.info(txt);
        return true;
      }
      else
      {
        String txt = "Does not need Resources: the Average Load " + avgLoad + 
            " is lower than allowed " + this.max_tasks;
        this.logger.info(txt);
        return false;
      }
    }
    else
    {
      this.logger.info("Need Resources: There are no resources running");
      return true;
    }
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
    long now = System.currentTimeMillis();
    List<CcdpVMResource> terminate = new ArrayList<>();
    for( CcdpVMResource vm : resources )
    {
      long last = vm.getLastAssignmentTime();
      int diff = (int)( ( (now - last) / 1000) / 60 );
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
      
      // cannot assigned less mem than required
      if(task.getMEM() < CcdpTaskRequest.MIN_MEM_REQ )
        task.setMEM(CcdpTaskRequest.MIN_MEM_REQ);
      
      // CPU = 0 means use any logic
      // 0 > CPU < 100 means assign it where it fits
      // CPU > 100 means run this task by itself on a node
      //
      if( cpu == 0 )
      {
        this.logger.debug("CPU = " + cpu + " Assigning Task based on session");
        if( this.hasCapacity( task, target ) )
        {
          // cannot have a CPU less than the minimum required (mesos-master dies)
          task.setCPU(CcdpTaskRequest.MIN_CPU_REQ);
          task.setSubmitted(true);
          task.assigned();
        //this.addTaskToResource(target, task);
          target.addTask(task);
          task.setHostId(target.getInstanceId());
          assigned.add( task );
        }
      }
      else if( cpu >= 100 )
      {
        this.logger.debug("CPU = " + cpu + " Assigning a Resource just for this task");
        if( this.canAssignResource(task, target) )
        {
          //TODO Changing the task's cpu to make sure "it will fit"
          this.logger.debug("Setting the Task's CPU to max amount for this resource");
          task.setCPU(CcdpTaskRequest.MIN_CPU_REQ);
          task.setSubmitted(true);
          task.assigned();
          task.setHostId(target.getInstanceId());
          //this.addTaskToResource(target, task);
          target.addTask(task);
          assigned.add( task );
        }
      }
      else
      {
        this.logger.debug("CPU = " + cpu + " Assigning Task using First Fit");
        if( this.canRunTask(task, target) )
        {
          task.setSubmitted(true);
          task.assigned();
          target.addTask(task);
          task.setHostId(target.getInstanceId());
        //this.addTaskToResource(target, task);
          target.addTask(task);
        }
      }
    }
    
    return assigned;
  }

  /**
   * Determines whether or not the target VM has the capacity to run the task.
   * This is determine by the number of tasks currently running as well as by
   * the task not being submitted
   *  
   * @param task  the task to run
   * @param target the intended VM to run the task
   * 
   * @return true if the task has not been submitted and the target VM is not
   *         running as many tasks as required
   */
  private boolean hasCapacity( CcdpTaskRequest task, CcdpVMResource target )
  {
    if( task.isSubmitted() )
    {
      this.logger.debug("Job already submitted, skipping it");
      return false;
    }
    
    if( target.isSingleTasked() )
    {
      this.logger.debug("Task is assigned to a dedicated task: " + 
                        target.getSingleTask());
      return false;
    }
    
    this.logger.info("Using Max Number of Tasks " + this.max_tasks);
    if( target.getNumberTasks() >= this.max_tasks )
      return false;
    else
      return true;
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
       String msg = "VM " + resource.getInstanceId() + 
                    " not running " + status.toString(); 
       this.logger.info(msg);
       return false;
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
     String hid = task.getHostId();
     String id = resource.getInstanceId();
     this.logger.debug("Comparing Host " + id + " and task's Host Id " + hid);
     if( hid != null && hid.equals( id ) )
     {
       this.logger.debug(resource.getInstanceId() + " Status: " + resource.getStatus() );
       if( resource.getStatus().equals(ResourceStatus.RUNNING) )
       {
         String tid = task.getTaskId();
         this.logger.info("VM " + id + " was assigned to task " + tid);
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

