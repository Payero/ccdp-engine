/**
 * 
 */
package com.axios.ccdp.impl.controllers;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.log4j.Logger;

import com.axios.ccdp.resources.CcdpImageInfo;
import com.axios.ccdp.resources.CcdpVMResource;
import com.axios.ccdp.resources.CcdpVMResource.ResourceStatus;
import com.axios.ccdp.tasking.CcdpTaskRequest;
import com.axios.ccdp.utils.CcdpUtils;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * @author Oscar E. Ganteaume
 *
 */
public class AvgLoadControllerImpl extends CcdpVMControllerAbs
{
  private Logger logger = Logger.getLogger(AvgLoadControllerImpl.class);
  
  /**
   * Instantiates a new object and starts receiving and processing incoming 
   * assignments    
   */
  public AvgLoadControllerImpl()
  {
    super();
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
  public void configure(JsonNode config)
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
      if( config.has("allocate") )
      {
        JsonNode alloc = config.get("allocate");
        if( alloc.has("avg-cpu-load") )
          allocate.put("cpu", alloc.get("avg-cpu-load").asDouble());
        if( alloc.has("avg-mem-load") )
          allocate.put("mem", alloc.get("avg-mem-load").asDouble());
        if( alloc.has("avg-time-load") )
          allocate.put("time", alloc.get("avg-time-load").asInt());
        if( alloc.has("no-more-than") )
          allocate.put("max-tasks", alloc.get("no-more-than").asInt());
      }
      
      // Setting all the de-allocation parameters
      if( config.has("deallocate") )
      {
        JsonNode dealloc = config.get("deallocate");
        if( dealloc.has("avg-cpu-load") )
          deallocate.put("cpu", dealloc.get("avg-cpu-load").asDouble());
        if( dealloc.has("avg-mem-load") )
          deallocate.put("mem", dealloc.get("avg-mem-load").asDouble());
        if( dealloc.has("avg-time-load") )
          deallocate.put("time", dealloc.get("avg-time-load").asInt());
      }

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
  public CcdpImageInfo allocateResources(List<CcdpVMResource> resources)
  {
    this.logger.trace("Checking Avg Load for resource allocation");
    CcdpImageInfo imgCfg = null;
    if( resources == null || resources.size() == 0 )
      return imgCfg;

    // REVISIT THE WHOLE SINGLE TASKED ORDEAL HERE
    
//    List<CcdpVMResource> avail = new ArrayList<>();
//    for( CcdpVMResource tmp : resources )
//    {
//      if( !tmp.isSingleTasked() )
//        avail.add(tmp);
//    }
//    
//    if( avail.size() == 0 )
//      return imgCfg;
    
    JsonNode alloc = this.config.get("allocate");
    double cpu = alloc.get("cpu").asDouble();
    double mem = alloc.get("mem").asDouble();
    int tasks = alloc.get("max-tasks").asInt();
    int sz = resources.size();
    this.logger.info("Allocating Resources based on " + sz + " VMs");
    double[] assignedCPU = new double[sz];
    double[] assignedMEM = new double[sz];
    double[] availableCPU = new double[sz];
    double[] availableMEM = new double[sz];
    int load = 0;
    
    // let's try to guess the node type
    Map<String, CcdpImageInfo> types = new HashMap<>();
    
    for( int i = 0; i < sz; i++ )
    {
      CcdpVMResource vm = resources.get(i);
      String type = vm.getNodeType();
      types.put(type, CcdpUtils.getImageInfo(type));
      
      double vm_cpu = vm.getCPULoad() * 100;
      double vm_mem = vm.getMemLoad();
      double vm_tc = vm.getCPU();
      double vm_tm = vm.getTotalMemory();
      
      String iid = vm.getInstanceId();
      this.logger.trace("Instance " + iid + " CPU: " + vm_cpu + " MEM " + vm_mem );
      assignedCPU[i] = vm_cpu;
      assignedMEM[i] = vm_mem;
      
      availableCPU[i] = vm_tc;
      availableMEM[i] = vm_tm;
      load += vm.getNumberTasks();
    }
    
    for( String type : types.keySet() )
      imgCfg = CcdpImageInfo.copyImageInfo(CcdpUtils.getImageInfo(type));
    
    if( types.size() == 1 )
      this.logger.info("Need more " + imgCfg.getNodeType() + " nodes");
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
    double avgCpu = this.getAverageCPU(assignedCPU);
    double avgMem = this.getAverageMem(assignedMEM, availableMEM);
    
    
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
  public List<CcdpVMResource> deallocateResources(List<CcdpVMResource> resources)
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
        
        assignedCPU[i] = vm.getCPULoad()*100;
        assignedMEM[i] = vm.getMemLoad();
        availableCPU[i] = vm.getCPU();
        availableMEM[i] = vm.getTotalMemory();
      }
    }
    
    // Now let's check averages...
    double avgCpu = this.getAverageCPU(assignedCPU);
    double avgMem = this.getAverageMem(assignedMEM, availableMEM);
    this.logger.debug("Avg CPU: " + avgCpu + " Avg Mem: " + avgMem);
    
    this.logger.debug("Dealloc: Avg CPU Utilization: " + avgCpu);
    this.logger.debug("Dealloc: Avg MEM Utilization: " + avgMem);
    
    
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
   * Allows custom code to be invoked automatically when the CPU is set to zero
   * (0).  Each child class needs to implement this method
   * 
   * @param task the task or action that needs to be executed
   * @param resources a list of all available resources to run this task
   * 
   * @return the resource selected to run the task or null if none found
   */

   protected CcdpVMResource 
     customTaskAssignment(CcdpTaskRequest task, List<CcdpVMResource> resources)
   { 
     CcdpVMResource leastUsed = CcdpVMResource.leastUsed(resources);
     int cpu_load = this.config.get("allocate").get("avg-cpu-load").asInt();
     if(leastUsed ==null || ((leastUsed.getCPULoad()*100) >= cpu_load) ) {
       boolean first = true;
       
       //this is because sometime when the vm just stater running there is a 
       //spike in the cpu load but there are not task running.
       if(leastUsed !=null && leastUsed.getTasks().size() == 0)
         return leastUsed;
       
       leastUsed = null;
       for( CcdpVMResource res : resources )
       {
         if(ResourceStatus.LAUNCHED.equals(res.getStatus())) {
           if( first )
           {
             leastUsed = res;
             first = false;
             continue;
           }
           //if the vm are in launch state all we can do is try to allocate task to the vms with less task
           if( res.getNumberTasks() < leastUsed.getNumberTasks() )
             leastUsed = res;
           
          
         }
       }
     }
     return leastUsed;

   }

   
   /**
    * Gets the average of the given list.  This is obtain by simply adding all
    * the values and dividing it by the size of the array
    * 
    * @param dbls the list of values to get the average
    * 
    * @return the average value of the given list
    */
   private double getAverageMem( double[] assignedDbls, double[] availableDbls )
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
     
     return (avgUsed/avgTotal)*100;
   }
   
   /**
    * Gets the average of the given list.  This is obtain by simply adding all
    * the values and dividing it by the size of the array
    * 
    * @param dbls the list of values to get the average
    * 
    * @return the average value of the given list
    */
   private double getAverageCPU( double[] assignedDbls)
   {
     double assignedTotal = 0;
     int sz = assignedDbls.length;
     for( int i = 0; i < sz; i++ )
     {
       assignedTotal += assignedDbls[i];
     }
     logger.debug("The total assigned cpu is " + assignedTotal);
     double avgUsed = assignedTotal / sz;
     
     return avgUsed;
   }
}

