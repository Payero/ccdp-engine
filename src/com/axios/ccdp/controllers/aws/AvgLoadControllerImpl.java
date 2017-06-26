/**
 * 
 */
package com.axios.ccdp.controllers.aws;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.log4j.Logger;

import com.axios.ccdp.connections.intfs.CcdpTaskingControllerIntf;
import com.axios.ccdp.resources.CcdpVMResource;
import com.axios.ccdp.resources.CcdpVMResource.ResourceStatus;
import com.axios.ccdp.tasking.CcdpTaskRequest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * @author Oscar E. Ganteaume
 *
 */
public class AvgLoadControllerImpl implements CcdpTaskingControllerIntf
{

  /**
   * Generates debug print statements based on the verbosity level.
   */
  private Logger logger = Logger.getLogger(AvgLoadControllerImpl.class
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
   * Stores the first time a high utilization rate was noticed
   */
  private Map<String, Double> high_use_time = new HashMap<>();
  
  /**
   * Instantiates a new object and starts receiving and processing incoming 
   * assignments    
   */
  public AvgLoadControllerImpl()
  {
    this.logger.debug("Initiating Avg Load Controller object");
    // making the JSON Objects Pretty Print
    this.mapper.enable(SerializationFeature.INDENT_OUTPUT);
    this.config = this.mapper.createObjectNode();
  }

  /**
   * Sets all the parameters required for this object to determine resource
   * allocation and deallocation.  If the CPU, Memory, and time are set in the
   * configuration file then it uses those values otherwise it uses the 
   * following:
   * 
   *    Allocation:
   *        CPU:  70
   *        MEM:  70
   *        Time: 3
   *        
   *    Deallocation:
   *        CPU:  10
   *        MEM:  10
   *        Time: 5
   * 
   * If the CPU and the memory are set to zero then the it will deallocate the
   * resources based on the last time it was tasked
   * 
   * @param config the configuration used to set the allocation/deallocation
   *        parameters
   */
  public void configure(ObjectNode config)
  {
    // set all the defaults first
    ObjectNode allocate = this.mapper.createObjectNode();
    allocate.put("cpu", 70);
    allocate.put("mem", 70);
    allocate.put("time", 3);
    
    ObjectNode deallocate = this.mapper.createObjectNode();
    deallocate.put("cpu", 10);
    deallocate.put("mem", 10);
    deallocate.put("time", 5);
    
    // if a configuration is given then check values
    if( config != null )
    {
      // Setting all the allocation parameters
      if( config.has("allocate.avg.load.cpu") )
        allocate.put("cpu", config.get("allocate.avg.load.cpu").asDouble());
      if( config.has("allocate.avg.load.mem") )
        allocate.put("mem", config.get("allocate.avg.load.mem").asDouble());
      if( config.has("allocate.avg.load.time") )
        allocate.put("time", config.get("allocate.avg.load.time").asInt());
      
      // Setting all the de-allocation parameters
      if( config.has("deallocate.avg.load.cpu") )
        deallocate.put("cpu", config.get("deallocate.avg.load.cpu").asDouble());
      if( config.has("deallocate.avg.load.mem") )
        deallocate.put("mem", config.get("deallocate.avg.load.mem").asDouble());
      if( config.has("deallocate.avg.load.time") )
        deallocate.put("time", config.get("deallocate.avg.load.time").asInt());
    }
    
    this.config.set("allocate", allocate);
    this.config.set("deallocate", deallocate);
    this.logger.info("Configuring Tasker using " + this.config.toString());    
 
  }

  /**
   * Determines whether or not additional resources are needed based on
   * the utilization level of the given resources.  If the resources combined
   * reaches the threshold for over a determined time then it returns true 
   * otherwise it returns false.
   * 
   * @param resources the list of resources to test for need of additional 
   *        resources
   * 
   * @return true if more resources need to be allocated or false otherwise
   * 
   */
  public boolean needResourceAllocation(List<CcdpVMResource> resources)
  {
    this.logger.debug("Checking Allocation Requirements");
    // making sure we have valid data
    if( resources == null || resources.isEmpty() )
      return true;
    
    JsonNode alloc = this.config.get("allocate");
    double cpu = alloc.get("cpu").asDouble();
    double mem = alloc.get("mem").asDouble();
    
    Map<String, Double> map = this.getAvgLoad(resources);
    String sid = resources.get(0).getAssignedSession();
    
    // now let's check averages...
    double avgCpuLoad = map.get("CPU");
    double avgMem = map.get("MEM");
    double avgTime = map.get("TIME");
    
    String txt = "SID " + sid + "Avg Cpu Load: " + 
                 avgCpuLoad + "Avg Mem Load: " + avgMem; 
    this.logger.info(txt);
    long now = System.currentTimeMillis();
    
    if( avgCpuLoad >= cpu && avgMem >= mem )
    {
      this.logger.info("High utilization for this session, checking time");
      // we need to do add it just once
      if( !this.high_use_time.containsKey(sid) )
        this.high_use_time.put(sid, avgTime);
      
      int time = alloc.get("time").asInt();
      double last_high_res_rec = this.high_use_time.get(sid);
      int diff = (int)( ( (now - last_high_res_rec) / 1000) / 60 );
      
      this.logger.debug("Allocation allowed time " + time);
      this.logger.debug("Last assignement time: " + diff);
      
      // if the diff is greater than the first time we noticed a spike in 
      // utilization then we need more resources
      if( diff >= time )
      {
        this.logger.info("The spike started longer than " + time + " mins ago");
        return true;  
      }
      else
        this.logger.info("Recent spike, waiting...");
    }
    else
    {
      this.logger.info("Low utilization rate, don't need additional resources");
      // if low again, need to remove it from the list
      if( this.high_use_time.containsKey(sid) )
        this.high_use_time.remove(sid);
    }
    
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
    this.logger.info("Checking Deallocation Requirements");
    List<CcdpVMResource> terminate = new ArrayList<>();
    // making sure we have good input data
    if( resources == null || resources.isEmpty() )
      return terminate;
    
    JsonNode dealloc = this.config.get("deallocate");
    double cpu = dealloc.get("cpu").asDouble();
    double mem = dealloc.get("mem").asDouble();
    long now = System.currentTimeMillis();
    
    for( CcdpVMResource vm : resources )
    {
      if( vm.isSingleTasked() && ( vm.getTasks().size() == 0 ) )
      {
        this.logger.info("VM Single Tasked and task is complete, terminating");
        terminate.add(vm);
      }
    }
    
    Map<String, Double> map = this.getAvgLoad(resources);
    
    // Now let's check averages...
    double avgCpu = map.get("CPU");
    double avgMem = map.get("EM");
    String sid = resources.get(0).getAssignedSession();
    this.logger.debug("SID: " + sid + " Avg CPU: " + 
                      avgCpu + " Avg Mem: " + avgMem);
    
    
    if( avgCpu <= cpu && avgMem <= mem )
    {
      this.logger.info("Low use for this session, checking assignement time");
      for( CcdpVMResource vm : resources )
      {
        
        long last = vm.getLastAssignmentTime();
        int diff = (int)( ( (now - last) / 1000) / 60 );
        // is the time of the last assignment greater than allowed and it was
        // running (avoiding new launches)
        if( diff >= dealloc.get("time").asInt() && 
            ResourceStatus.RUNNING.equals( vm.getStatus() ) &&
            vm.getTasks().size() == 0 &&
            !vm.isSingleTasked()
           )
        {
          this.logger.info("VM has not been allocated for a while, "
                            + "marked for termination");
          
          this.logger.debug("VM Details: " + vm.toString());
          
          terminate.add(vm);  
        }
      }  
    }
    
    return terminate;
  }
  
  /**
   * Calculates the average load of memory, cpu, and allocation time for all 
   * the given resources combined.  If the resources is null or is empty it
   * returns null
   * 
   * The keys used to gather the information are: CPU, MEM, and TIME
   * 
   * @param resources a list of resources to determine the average load 
   * @return a map containing the three averages
   */
  private Map<String, Double> getAvgLoad( List<CcdpVMResource> resources )
  {
    if( resources == null || resources.isEmpty() )
      return null;
    
    Map<String, Double> map = new HashMap<>();
    int sz = resources.size();
    double totalMemLoad = 0;
    double totalMem = 0;
    double totalCpuLoad =0;
    long totalTime = 0l;
    
    for( CcdpVMResource vm : resources )
    {
      // do not consider single tasked VMs
      if( vm.isSingleTasked() )
        continue;
      
      totalMem += vm.getTotalMemory();
      totalMemLoad += vm.getMemLoad();
      totalCpuLoad += vm.getCPULoad();
      totalTime += vm.getLastAssignmentTime();
    }
    
    // now let's check the averages (we need percentages from mem and cpu)
    double avgCpuLoad = ( totalCpuLoad / sz) * 100;
    double totalAvgMem = totalMem / sz;
    double avgMemLoad = totalMemLoad / sz;
    double avgLoadTime = totalTime / sz;
    
    double avgMem = ( avgMemLoad / totalAvgMem ) * 100;
    
    map.put("MEM", avgMem);
    map.put("CPU", avgCpuLoad);
    map.put("TIME", avgLoadTime);
    
    return map;
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
        this.logger.info("CPU = " + cpu + " Assigning Task based on session");
        if( this.isLeastUtilized( task, target, resources) )
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
        this.logger.info("CPU = " + cpu + " Assigning a Resource just for this task");
        if( this.canAssignResource(task, target) )
        {
          //TODO Changing the task's cpu to make sure "it will fit"
          this.logger.info("Setting the Task's CPU to max amount for this resource");
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
        this.logger.info("CPU = " + cpu + " Assigning Task using First Fit");
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
    
    if( target.isSingleTasked() )
    {
      this.logger.debug("Target is assigned to a dedicated task " + target.getSingleTask() );
      return false;
    }
    
    
    if( resources == null || resources.isEmpty() )
    {
      this.logger.debug("The list is invalid, returning true (use target VM)");
      return true;
    }
    
    CcdpVMResource vm = CcdpVMResource.leastUsed(resources);
    if( vm != null && vm.equals(target))
    {
      return true;
    }
    return false;
  }
  
  
  /**
   * Checks the amount of CPU and memory available in this VM resource.  If the 
   * VM has enough resources to run the task then it returns the 
   * CcdpTaskRequest otherwise it returns null
   * 
   * @param task the task to assign to a resource
   * @param resource the resource to determine whether or not it can be run  
   *        this task
   * 
   * @return true if this task can be executed on this node
   */
   private boolean canRunTask( CcdpTaskRequest task, CcdpVMResource resource )
   {
     this.logger.debug("Determining if task fits in resource");
     if( task.isSubmitted() )
     {
       this.logger.debug("Job already submitted, skipping it");
       return false;
     }
     
     // if the VM is not running then do not assign task to it
     ResourceStatus status = resource.getStatus();
     if( !ResourceStatus.RUNNING.equals(status) )
     {
       this.logger.info("VM " + resource.getInstanceId() + " not running " + 
           status.toString());
       return false;
     }
     
     double offerCpus = resource.getCPU();
     double offerMem = resource.getTotalMemory();
     
     this.logger.debug(String.format("Offer CPUs: %f, Memory: %f", 
                       offerCpus, offerMem));
     
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
    * Finds if this VM is assigned to this task.  This method is invoked when 
    * a task requires to be executed alone on a VM.  If the VM host id matches 
    * the task's resource id and the VM's ResourceStatus is set to RUNNING then 
    * it returns true otherwise it returns false
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

