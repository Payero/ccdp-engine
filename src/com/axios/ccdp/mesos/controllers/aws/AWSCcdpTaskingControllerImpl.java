/**
 * 
 */
package com.axios.ccdp.mesos.controllers.aws;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;

import org.apache.log4j.Logger;
import org.apache.mesos.Protos;
import org.apache.mesos.Protos.CommandInfo;
import org.apache.mesos.Protos.ExecutorInfo;
import org.apache.mesos.Protos.Resource;
import org.apache.mesos.Protos.SlaveID;
import org.apache.mesos.Protos.TaskID;
import org.apache.mesos.Protos.TaskInfo;
import org.apache.mesos.Protos.Value;

import com.axios.ccdp.mesos.connections.intfs.CcdpTaskingControllerIntf;
import com.axios.ccdp.mesos.resources.CcdpVMResource;
import com.axios.ccdp.mesos.resources.CcdpVMResource.ResourceStatus;
import com.axios.ccdp.mesos.tasking.CcdpTaskRequest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.protobuf.ByteString;

/**
 * @author Oscar E. Ganteaume
 *
 */
public class AWSCcdpTaskingControllerImpl 
                          implements CcdpTaskingControllerIntf<TaskInfo>
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
  
  private ExecutorInfo executor;
  
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

  public void configure(ObjectNode config)
  {
    if( config != null )
    {
      this.config = config;
      this.logger.info("Configuring Tasker using " + this.config.toString());
    }
  }


  public boolean needResourceAllocation(List<CcdpVMResource> resources)
  {
    // TODO Auto-generated method stub
    return false;
  }
  
  public List<CcdpVMResource> deallocateResource(List<CcdpVMResource> resources)
  {
    // TODO Auto-generated method stub
    return null;
  }
  
  public List<TaskInfo> assignTasks(List<CcdpTaskRequest> tasks, 
                                    List<CcdpVMResource> resources)
  {
    List<TaskInfo> assigned = new ArrayList<>();
    
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
        TaskInfo ti = this.doLeastUtilized(task, resources);
        if( ti != null )
        {
          task.setSubmitted(true);
          task.assigned();
          assigned.add( ti );
        }
      }
      else if( cpu >= 100 )
      {
        this.logger.info("CPU = " + cpu + " Assigning a Resource just for this task");
        TaskInfo ti = this.getAssignedResource(task, resources);
        if( ti != null )
        {
          task.setSubmitted(true);
          task.assigned();
          assigned.add( ti );
        }
      }
      else
      {
        this.logger.info("CPU = " + cpu + " Assigning Task using First Fit");
        TaskInfo ti = this.doFirstFit(task, resources);
        if( ti != null )
        {
          task.setSubmitted(true);
          task.assigned();
          assigned.add( ti );
        }
      }
    }
    
    return assigned;
  }



  
  /**
   * Checks the amount of CPU and memory available in the offer and starts 
   * launching jobs that has not been submitted before and requires less 
   * resources than the ones available in the Offer.
   * 
   * @param task the task to assign to a resource
   * @param resources a list of Resources that could be used to run this task
   * 
   * @return a list of TaskInfo objects that were launched on this offer
   */
   private TaskInfo doFirstFit( CcdpTaskRequest task, 
                                List<CcdpVMResource> resources )
   {
     this.logger.debug("Running First Fit");
     TaskInfo taskInfo = null;
     if( task.isSubmitted() )
     {
       this.logger.debug("Job already submitted, skipping it");
       return taskInfo;
     }
     
     // We always need to extract the resource info from the offer
     for( CcdpVMResource resource : resources )
     {
       // if the VM is not running then do not assign task to it
       ResourceStatus status = resource.getStatus();
       if( !ResourceStatus.RUNNING.equals(status) )
       {
         String msg = "VM " + resource.getInstanceId() + " not running " + status.toString(); 
         this.logger.info(msg);
         continue;
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
         
         return this.makeTask( resource.getAgentId(), task );
       }
     }// end of resources loop
     
     return null;
   }
  
   /**
    * Checks the amount of CPU and memory available in the offer and starts 
    * launching jobs that has not been submitted before and requires less 
    * resources than the ones available in the Offer.
    * 
    * @param offer the processing node available to execute the job
    * @param tasks as list of jobs or tasks to run
    * 
    * @return a list of TaskInfo objects that were launched on this offer
    */
    private TaskInfo doLeastUtilized( CcdpTaskRequest task, 
                                List<CcdpVMResource> resources )
    {
      this.logger.debug("Finding least used VM of the session");
      TaskInfo taskInfo = null;
      if( task.isSubmitted() )
      {
        this.logger.debug("Job already submitted, skipping it");
        return taskInfo;
      }
      List<CcdpVMResource> list = new ArrayList<>();
      
      // We always need to extract the resource info from the offer
      for( CcdpVMResource resource : resources )
        list.add(resource);
      
      CcdpVMResource vm = CcdpVMResource.leastUsed(list);
      if( vm != null )
      {
        return this.makeTask( vm.getAgentId(), task );
      }
      return null;
    }
    
  /**
   * Finds the VM that is assigned to this task.  This method is invoked when 
   * a task requires to be executed alone on a VM.
   * 
   * @param task the task to assign to the VM 
   * @param resources all the resources available for this session
   * 
   * @return a TaskInfo object associating the task with the VM
   */
  private TaskInfo getAssignedResource(CcdpTaskRequest task, 
                                List<CcdpVMResource> resources )
  {
    String tid = task.getTaskId();
    TaskInfo taskInfo = null;
    for( CcdpVMResource resource: resources )
    {
      if( tid != null && tid.equals( resource.getSingleTask() ) )
      {
        if( resource.getStatus().equals(ResourceStatus.RUNNING) )
        {
          String iid = resource.getInstanceId();
          this.logger.info("VM " + iid + " was assigned to " + tid);
          taskInfo = this.makeTask(resource.getAgentId(), task);
        }
        else
        {
          this.logger.info("Found assigned resource but is not RUNNING");
        }
      }
    }
    
    return taskInfo;
  }

  public void setExecutor( ExecutorInfo exec )
  {
    this.executor = exec;
  }
  /**
   * Generates a TaskInfo object that will be executed by the appropriate Mesos 
   * Agent.
   * 
   * @param targetSlave the Unique identifier of the Mesos Agent responsible for
   *        running this task
   * @param exec The mesos executor to use to execute the task
   * 
   * @return a TaskInfo object containing all the information required to run
   *         this job
   */
  public TaskInfo makeTask(String targetSlave, CcdpTaskRequest task)
  {
    this.logger.debug("Making Task at Slave " + targetSlave);
    TaskID id = TaskID.newBuilder().setValue(task.getTaskId()).build();
    
    Protos.TaskInfo.Builder bldr = TaskInfo.newBuilder();
    bldr.setName("task " + id.getValue());
    bldr.setTaskId(id);
    // Adding the CPU
    Protos.Resource.Builder resBldr = Resource.newBuilder();
    resBldr.setName("cpus");
    resBldr.setType(Value.Type.SCALAR);
    resBldr.setScalar(Value.Scalar.newBuilder().setValue(task.getCPU()));
    Resource cpuRes = resBldr.build();
    bldr.addResources(cpuRes);
    this.logger.debug("Adding CPU Resource " + cpuRes.toString());
    
    // Adding the Memory
    resBldr.setName("mem");
    resBldr.setType(Value.Type.SCALAR);
    resBldr.setScalar(Value.Scalar.newBuilder().setValue(task.getMEM()));
    Resource memRes = resBldr.build();
    bldr.addResources(memRes);
    this.logger.debug("Adding MEM Resource " + memRes.toString());
    
    Protos.CommandInfo.Builder cmdBldr = CommandInfo.newBuilder();
    StringJoiner joiner = new StringJoiner(" ");
    task.getCommand().forEach(joiner::add);
    
    String cmd = joiner.toString();
    cmdBldr.setValue(cmd);
    this.logger.debug("Running Command: " + cmd);
    
    Protos.SlaveID.Builder slvBldr = SlaveID.newBuilder();
    slvBldr.setValue(targetSlave);
    bldr.setSlaveId(slvBldr.build());
    
    bldr.setExecutor(this.executor);
    ObjectNode json = this.mapper.createObjectNode();
    
    task.getCommand();
    task.getConfiguration();
    Map<String, String> cfg = task.getConfiguration();
    // if there is a configuration, add it
    if( cfg != null )
    {
      JsonNode jsonNode = mapper.convertValue(cfg, JsonNode.class);
      json.set("cfg", jsonNode);
    }
    
    json.put("cmd", cmd);
    
    bldr.setData(ByteString.copyFrom(json.toString().getBytes()));
    return bldr.build();
  }
  
}
