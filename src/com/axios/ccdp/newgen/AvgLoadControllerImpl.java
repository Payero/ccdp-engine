/**
 * 
 */
package com.axios.ccdp.newgen;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.log4j.Logger;

import com.axios.ccdp.resources.CcdpVMResource;
import com.axios.ccdp.resources.CcdpVMResource.ResourceStatus;
import com.axios.ccdp.tasking.CcdpTaskRequest;
import com.axios.ccdp.utils.CcdpImageInfo;
import com.axios.ccdp.utils.CcdpUtils;
import com.axios.ccdp.utils.CcdpUtils.CcdpNodeType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * @author Oscar E. Ganteaume
 *
 */
public class AvgLoadControllerImpl
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
   * Instantiates a new object and starts receiving and processing incoming 
   * assignments    
   */
  public AvgLoadControllerImpl()
  {
    this.logger.debug("Initiating Tasker object");
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
   *        max:  -1
   *        
   *    Deallocation:
   *        CPU:  0
   *        MEM:  0
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
    allocate.put("time", 2);
    allocate.put("max-tasks", -1);
    
    ObjectNode deallocate = this.mapper.createObjectNode();
    deallocate.put("cpu", 0);
    deallocate.put("mem", 0);
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
      if( config.has("allocate.no.more.than") )
        allocate.put("max-tasks", config.get("allocate.no.more.than").asInt());
      
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
   * the utilization level of the given resources.  If there is a need for more
   * resources then it returns the image information to launch otherwrise it 
   * returns null.
   * 
   * @param resources the list of resources to test for need of additional 
   *        resources
   * @return The image configuration required to launch more resources or null
   *         if no additonal resources are needed
   */
  public CcdpImageInfo needResourceAllocation(List<CcdpVMResource> resources)
  {
    CcdpImageInfo imgCfg = null;
    if( resources == null || resources.size() == 0 )
      return imgCfg;
    
    JsonNode alloc = this.config.get("allocate");
    double cpu = alloc.get("cpu").asDouble();
    double mem = alloc.get("mem").asDouble();
    int tasks = alloc.get("max-tasks").asInt();
    int sz = resources.size();
    double[] assignedCPU = new double[sz];
    double[] assignedMEM = new double[sz];
    double[] availableCPU = new double[sz];
    double[] availableMEM = new double[sz];
    int load = 0;
    
    // let's try to guess the node type
    Map<CcdpNodeType, CcdpImageInfo> types = new HashMap<>();
    
    for( int i = 0; i < sz; i++ )
    {
      CcdpVMResource vm = resources.get(i);
      CcdpNodeType type = vm.getNodeType();
      types.put(type, CcdpUtils.getImageInfo(type));
      
      assignedCPU[i] = vm.getAssignedCPU();
      assignedMEM[i] = vm.getAssignedMemory();
      availableCPU[i] = vm.getCPU();
      availableMEM[i] = vm.getTotalMemory();
      load += vm.getNumberTasks();
    }
    
    for( CcdpNodeType type : types.keySet() )
      imgCfg = new CcdpImageInfo(CcdpUtils.getImageInfo(type));
    
    if( types.size() == 1 )
      this.logger.info("Need more " + imgCfg.getNodeTypeAsString() + " nodes");
    else
      this.logger.warn("Has more than one type of node, returning first one");
    
    if( cpu == 0 && mem == 0 && tasks > 0 )
    {
      this.logger.info("Using Max number of Tasks " + tasks);
      if(sz > 0 )
      {
        int avgLoad = (int)(load / sz) ;
        if( avgLoad >= tasks )
        {
          String txt = "Need Resources: the Average Load " + avgLoad + 
              " is greater than allowed " + tasks;
          
          this.logger.info(txt);
          return imgCfg;
        }
        else
        {
          String txt = "Does not need Resources: the Average Load " + avgLoad + 
              " is less than allowed " + tasks;
          this.logger.info(txt);
          return null;
        }
      }
      else
      {
        this.logger.info("Need Resources: There are no resources running");
        return imgCfg;
      }
    }
    
    // Now let's check averages...
    double avgCpu = this.getAverage(assignedCPU, availableCPU);
    double avgMem = this.getAverage(assignedMEM, availableMEM);
    
    
    this.logger.debug("Average CPU Utilization: " + avgCpu);
    this.logger.debug("Average MEM Utilization: " + avgMem);
    long now = System.currentTimeMillis();
    
    
    if( avgCpu >= cpu && avgMem >= mem )
    {
      this.logger.info("High utilization for this session, checking time");
      long last = 0;
      for( CcdpVMResource vm : resources )
      {
        if( vm.getLastAssignmentTime() > last )
          last = vm.getLastAssignmentTime();
      }  
      int diff = (int)( ( (now - last) / 1000) / 60 );
      // if the last time a task was allocated is less than the allocation time
      // then we need more resources
      if( diff <= alloc.get("time").asInt() )
      {
        this.logger.info("Last allocation was recent, need resources");
        return imgCfg;  
      }
    }
    else
      this.logger.info("Low utilization rate, don't need additional resources");
    
    return null;
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
    JsonNode dealloc = this.config.get("deallocate");
    double cpu = dealloc.get("cpu").asDouble();
    double mem = dealloc.get("mem").asDouble();
    long now = System.currentTimeMillis();
    
    if( cpu == 0 && mem == 0 )
    {
      this.logger.info("Using last allocation time only");
      List<CcdpVMResource> terminate = new ArrayList<>();
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
      return terminate;
    }// end of using tasking only
    
    this.logger.info("Using CPU and Memory average");
    List<CcdpVMResource> terminate = new ArrayList<>();
    int sz = resources.size();
    double[] assignedCPU = new double[sz];
    double[] assignedMEM = new double[sz];
    double[] availableCPU = new double[sz];
    double[] availableMEM = new double[sz];
    
    for( int i = 0; i < sz; i++ )
    {
      CcdpVMResource vm = resources.get(i);
      if( vm.isSingleTasked() && ( vm.getTasks().size() == 0 ) )
      {
        this.logger.info("VM Single Tasked and task is complete, terminating");
        terminate.add(vm);
      }
      else
      {
        this.logger.debug("Adding resources to average lists");
        assignedCPU[i] = vm.getAssignedCPU();
        assignedMEM[i] = vm.getAssignedMemory();
        availableCPU[i] = vm.getCPU();
        availableMEM[i] = vm.getTotalMemory();
      }
    }
    
    // Now let's check averages...
    double avgCpu = this.getAverage(assignedCPU, availableCPU);
    double avgMem = this.getAverage(assignedMEM, availableMEM);
    this.logger.debug("Avg CPU: " + avgCpu + " Avg Mem: " + avgMem);
    
    this.logger.debug("Average CPU Utilization: " + avgCpu);
    this.logger.debug("Average MEM Utilization: " + avgMem);
    
    
    if( avgCpu <= cpu && avgMem <= mem )
    {
      this.logger.info("Low Utilization for this Session, checking assignement time");
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
   * Assigns all the tasks in the given list to the target VM based on 
   * resources availability and other conditions.
   * 
   * @param tasks a list of tasks to consider running in the intended VM
   * @param resources all available resources that could be potentially
   *        used to run this tasks
   * 
   * @return a map specifying which tasks can be run on which resource
   */
  public Map<CcdpVMResource, List<CcdpTaskRequest>> assignTasks(List<CcdpTaskRequest> tasks, 
                                           List<CcdpVMResource> resources)
  {
    Map<CcdpVMResource, List<CcdpTaskRequest>> tasked = new HashMap<>();
    
    if( resources == null || resources.isEmpty() )
    {
      System.out.println("SOMETHING WAS NULL OR EMPTY");
      return tasked;
    }
    
    System.out.println("NUMBER OF TASKS WE HAVE IS: " + tasks.size());
    
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
        CcdpVMResource target = CcdpVMResource.leastUsed(resources);
        if (target == null) {
          this.logger.error("The target is null!");
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
        this.logger.info("CPU = " + cpu + " Assigning a Resource just for task " + task.getName());
        CcdpVMResource target = this.getAssignedResource(task, resources);
        if( target != null  )
        {
          this.logger.info("FOUND A NOT NULL TARGET. ACTUALLY ASSIGNED");
          String iid = target.getInstanceId();
          task.assigned();
          task.setHostId( iid );
          if( !tasked.containsKey( target ) )
            tasked.put(target, new ArrayList<CcdpTaskRequest>());
          tasked.get(target).add( task );
        }
        else
        {
          this.logger.warn("FAILED TO FIND AN ASSIGNED RESOURCE FOR THE TASK " + task.getName());
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
   * Checks the amount of CPU and memory available in this VM resource.  If the VM has enough resources to run the
   * task then it returns the CcdpTaskRequest otherwise it returns null
   * 
   * @param task the task to assign to a resource
   * @param resource the resource to determine whether or not it can be run this task
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
         
         return resource;
       }
     }
      
     return null;
   }
   
   /**
    * Finds the VM that is assigned to this task.  This method is invoked when 
    * a task requires to be executed alone on a VM.  If the VM is found and its
    * ResourceStatus is set to RUNNING then it returns the resource otherwise returns
    * null
    * 
    * @param task the task to assign to the VM 
    * @param resource the VM to test for assignment
    * 
    * @return the VM if found and is RUNNING
    */
   private CcdpVMResource getAssignedResource(CcdpTaskRequest task, 
       List<CcdpVMResource> resources )
   {
     String hid = task.getHostId();
     
     for( CcdpVMResource resource : resources )
     {
       String id = resource.getInstanceId();
       this.logger.debug("Comparing Host " + id + " and task's Host Id " + hid);
       if( (hid != null && hid.equals( id )) || resource.isFree() )
       {
         this.logger.debug(resource.getInstanceId() + " Status: " + resource.getStatus() );
         if (!resource.isSingleTasked()) {
           if( resource.getStatus().equals(ResourceStatus.RUNNING)  ||
               resource.getStatus().equals(ResourceStatus.LAUNCHED))
           {
             String tid = task.getTaskId();
             this.logger.info("VM " + id + " was assigned to task " + tid);
             //Since this method is only called when the task needs to be executed alone we set
             //the vm as single tasked
             resource.setSingleTask(task.getTaskId());
             return resource;
           }
           else
           {
             this.logger.info("Found assigned resource but is not RUNNING");
           }
         } else {
           //delete this else statement laster
           this.logger.info("FOUND RESOURCE :; " + resource.getInstanceId() +
               " BUT IT'S ALREADY BEEN TASKED TO : :: " + resource.getSingleTask());
         }
       }
     }
     
     return null;
   }
   
   /**
    * Gets the average of the given list.  This is obtain by simply adding all
    * the values and dividing it by the size of the array
    * 
    * @param dbls the list of values to get the average
    * 
    * @return the average value of the given list
    */
   private double getAverage( double[] assignedDbls, double[] availableDbls )
   {
     double assignedTotal = 0;
     double availableTotal = 0;
     int sz = availableDbls.length;
     for( int i = 0; i < sz; i++ )
     {
       assignedTotal += assignedDbls[i];
       availableTotal += availableDbls[i];
     }
     
     double avgUsed = assignedTotal / sz;
     double avgTotal = availableTotal / sz;
     
     return ( avgUsed * 100 ) / avgTotal;
   }
   
}

