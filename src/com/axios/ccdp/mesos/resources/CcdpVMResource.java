package com.axios.ccdp.mesos.resources;

import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;

import org.apache.log4j.Logger;

import com.amazonaws.services.route53.model.InvalidArgumentException;
import com.axios.ccdp.mesos.tasking.CcdpTaskRequest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

public class CcdpVMResource
{
  /**
   * Generates debug print statements based on the verbosity level.
   */
  private Logger logger = Logger.getLogger(CcdpVMResource.class.getName());
  /**
   * All the different states a resource can have
   */
  public enum ResourceStatus { OFFLINE, LAUNCHED, INITIALIZING, RUNNING, 
                               STOPPED, TERMINATED, SHUTTING_DOWN, FAILED }
  /**
   * The unique identifier to distinguish this VM
   */
  private String instanceId = null;
  /**
   * A unique identifier to distinguish the agent executing the tasks
   */
  private String agentId = null;
  /**
   * The user session this resource was assigned to run tasks
   */
  private String assignedSession = null;
  /**
   * The total amount of CPU assigned so far to running tasks
   */
  private double assignedCPU = 0;
  /**
   * The total amount of MEM assigned so far to running tasks
   */
  private double assignedMEM = 0;
  /**
   * The total amount of Disk space assigned so far to running tasks
   */
  private double assignedDisk = 0;
  /**
   * The total amount of CPU available for this resource to use
   */
  private double CPU = 0;
  /**
   * The total amount of MEM available for this resource to use
   */
  private double MEM = 0;
  /**
   * The total amount of Disk space availabe for this resource to use
   */
  private double disk = 0;
  /**
   * Stores the current state of the resource
   */
  private ResourceStatus status = ResourceStatus.OFFLINE;
  /**
   * Stores the resource's hostname
   */
  private String hostname = null;
  /**
   * Stores the taskId to run alone on this resource
   */
  private String singleTask = null;
  /**
   * Stores all the tasks assigned to this resource
   */
  private List<CcdpTaskRequest> tasks = new ArrayList<>();
  /**
   * Stores the last time this resource was tasked
   */
  private long last_assignment = 0;
  /**
   * Whether or not this resource was allocated to run a single task
   */
  private boolean isSingleTasked = false;
  
  /**
   * Instantiates a new CcdpVMResource and sets the unique identifier
   * 
   * @param iid the unique identifier to distinguish this VM
   */
  public CcdpVMResource(String iid)
  {
    this.setInstanceId(iid);
  }

  /**
   * Updates the resource status based on the the incoming ObjectNode object.
   * The JSON object has the following fields:
   *  
   *  status: A string containing the current resource status
   *  details: a dictionary with details of the running node
   *  
   * Example: {"status":"running","details":{"reachability":"passed"}}
   * 
   *    Valid status: running, stopped, shutting-down, terminated
   *    Valid reachability: passed or initializing
   *    
   * @param stats a JSON object containing the information described above
   */
  public void updateStatus( ObjectNode stats )
  {
    this.logger.debug("Updating Status: " + stats.toString());
    String status = stats.get("status").asText();
    
    switch( status )
    {
      case "running":
        JsonNode dets = stats.get("details");
        if( dets.has("reachability") )
        {
          JsonNode reach = dets.get("reachability");
          if( reach != null)
          {
            String txt = reach.asText();
            if( txt.equals("passed") )
            {
              this.logger.debug("Instance fully functional, done here");
              this.setStatus(ResourceStatus.RUNNING);
            }
            else if( txt.equals("initializing") )
            {
              this.logger.debug("Reachability is not passed is " + txt);
              this.setStatus(ResourceStatus.INITIALIZING);
            }
          }
        }
      break;
      case "stopped":
        this.setStatus(ResourceStatus.STOPPED);
      break;
      case "shutting-down":
        this.setStatus(ResourceStatus.SHUTTING_DOWN);
        break;
      case "terminated":
        this.setStatus(ResourceStatus.TERMINATED);
        break;
      default:
        this.setStatus(ResourceStatus.OFFLINE);
        break;
    }
  }
  
  /**
   * @return the instanceId
   */
  public String getInstanceId()
  {
    return instanceId;
  }

  /**
   * Sets the instance id, if is null it throws an InvalidArgumentException
   * 
   * @param instanceId the instanceId to set
   * 
   * @throws InvalidArgumentException an InvalidArgumentException exception is
   *         thrown if the instance id is null
   */
  public void setInstanceId(String instanceId)
  {
    if( instanceId == null )
      throw new InvalidArgumentException("The instance id cannot be null");
    
    this.instanceId = instanceId;
  }

  /**
   * @return the assignedCPU
   */
  public double getAssignedCPU()
  {
    return assignedCPU;
  }

  /**
   * @param assignedCPU the assignedCPU to set
   */
  public void setAssignedCPU(double assignedCPU)
  {
    if( assignedCPU < 0)
      throw new IllegalArgumentException("The assigned CPU needs to be >= 0");
    
    this.assignedCPU = assignedCPU;
  }

  /**
   * @return the assignedMEM
   */
  public double getAssignedMEM()
  {
    return assignedMEM;
  }

  /**
   * @param assignedMEM the assignedMEM to set
   */
  public void setAssignedMEM(double assignedMEM)
  {
    if( assignedMEM < 0)
      throw new IllegalArgumentException("The assigned MEM needs to be >= 0");
    this.assignedMEM = assignedMEM;
  }

  /**
   * @return the cPU
   */
  public double getCPU()
  {
    return CPU;
  }

  /**
   * @param cPU the cPU to set
   */
  public void setCPU(double cPU)
  {
    if( cPU < 0)
      throw new IllegalArgumentException("The CPU needs to be > 0");
    
    CPU = cPU;
  }

  /**
   * @return the assignedDisk
   */
  public double getAssignedDisk()
  {
    return assignedDisk;
  }

  /**
   * @param assignedDisk the assignedDisk to set
   */
  public void setAssignedDisk(double assignedDisk)
  {
    this.assignedDisk = assignedDisk;
  }

  /**
   * @return the disk
   */
  public double getDisk()
  {
    return disk;
  }

  /**
   * @param disk the disk to set
   */
  public void setDisk(double disk)
  {
    if( disk < 0)
      throw new IllegalArgumentException("The Disk needs to be > 0");
    
    this.disk = disk;
  }

  /**
   * @return the mEM
   */
  public double getMEM()
  {
    return MEM;
  }

  /**
   * @param mEM the mEM to set
   */
  public void setMEM(double mEM)
  {
    if( mEM <= 0)
      throw new IllegalArgumentException("The MEM needs to be > 0");
    MEM = mEM;
  }

  /**
   * @return the assignedSession
   */
  public String getAssignedSession()
  {
    return assignedSession;
  }

  /**
   * @param assignedSession the assignedSession to set
   */
  public void setAssignedSession(String assignedSession)
  {
    this.assignedSession = assignedSession;
  }
  
  /**
   * @return the status
   */
  public ResourceStatus getStatus()
  {
    return status;
  }

  /**
   * @param status the status to set
   */
  public void setStatus(ResourceStatus status)
  {
    this.status = status;
  }

  /**
   * @return the agentId
   */
  public String getAgentId()
  {
    return agentId;
  }

  /**
   * @param agentId the agentId to set
   */
  public void setAgentId(String agentId)
  {
    this.agentId = agentId;
  }

  /**
   * Gets the resource's hostname
   * 
   * @return the resource's hostname
   */
  public String getHostname()
  {
    return hostname;
  }

  /**
   * Sets the resource's hostname
   * 
   * @param hostname the resource's hostname
   */
  public void setHostname(String hostname)
  {
    this.hostname = hostname;
  }

  /**
   * @return the singleTask
   */
  public String getSingleTask()
  {
    return singleTask;
  }

  /**
   * @param singleTask the singleTask to set
   */
  public void setSingleTask(String singleTask)
  {
    if( singleTask != null )
    {
      this.isSingleTasked = true;
      this.singleTask = singleTask;
    }
  }

  /**
   * Gets whether or not this resource was allocated to a single task.
   * 
   * @return whether or not this resource was allocated to a single task.
   */
  public boolean isSingleTasked()
  {
    return this.isSingleTasked;
  }
  
  /**
   * Adds the given task to the list of tasks assigned to this VM Resource
   * 
   * @param task the task to add
   */
  public void addTask(CcdpTaskRequest task)
  {
    this.last_assignment = System.currentTimeMillis();
    this.tasks.add(task);
  }
  
  /**
   * Gets all the tasks assigned to this resource
   * 
   * @return all the tasks assigned to this resource
   */
  public List<CcdpTaskRequest> getTasks()
  {
    return this.tasks;
  }
  
  /**
   * Gets the total number of tasks assigned to this resource
   * 
   * @return the total number of tasks assigned to this resource
   */
  public int getNumberTasks()
  {
    return this.tasks.size();
  }
  
  /**
   * Removes the first task in the VM Resource list matching the given task's 
   * ID.  If the task is found it returns true otherwise it returns false
   * 
   * @param task the task to remove from the list
   * @return true if the task is found or false otherwise
   * 
   */
  public boolean removeTask( CcdpTaskRequest task )
  {
    return this.tasks.remove(task);
  }
  
  /**
   * Removes the first task in the VM Resource list matching the given task's 
   * ID.  If the task is found it returns true otherwise it returns false
   * 
   * @param tasks the tasks to remove from the list
   * @return true if the task is found or false otherwise
   * 
   */
  public boolean removeAllTasks( List<CcdpTaskRequest> tasks )
  {
    return this.tasks.removeAll(tasks);
  }
  
  /**
   * Gets the last time a task was added to this resource.  If no task has 
   * been assigned then the time represents when this object was created.
   * 
   * @return the last time a task was added to this resource or the time 
   *         this thread was created
   */
  public long getLastAssignmentTime()
  {
    return this.last_assignment;
  }
  
  /**
   * Compares this object with the one provided as argument. The result is as
   * follow:
   *   
   *    - If the given resource is null it returns false.
   *    - If the given resource is not null and its instance id are the same 
   *      it returns true otherwise it returns false
   *       
   * @param resource the resource to compare
   * 
   * @return true if the given resource is not null and its id is the same as
   *         the one from this object
   */
  public boolean equals( CcdpVMResource resource )
  {
    if( resource == null )
      return false;
    
    if( resource.getInstanceId().equals(this.getInstanceId()) )
      return true;
    else
      return false;
  }
  
  
  /**
   * Returns a JSON like string containing information about this object
   * 
   * @return a JSON like string containing information about this object
   */
  public String toString()
  {
    ObjectMapper mapper = new ObjectMapper();
    mapper.enable(SerializationFeature.INDENT_OUTPUT);
    ObjectNode node = mapper.createObjectNode();
    node.put("instance-id", this.instanceId);
    node.put("agent-id", this.agentId);
    node.put("session-id", this.assignedSession);
    
    node.put("cpu", this.CPU);
    node.put("assigned-cpu", this.assignedCPU);
    node.put("mem", this.MEM);
    node.put("assigned-mem", this.assignedMEM);
    node.put("disk", this.disk);
    node.put("disk-mem", this.assignedDisk);
    node.put("status", this.status.toString());
    
    ArrayNode tasks = mapper.createArrayNode();
    
    for( CcdpTaskRequest task : this.tasks )
      tasks.add(task.toObjectNode());
    
    node.set("tasks", tasks);
    
    StringWriter sw = new StringWriter();
    try
    {
      mapper.writeValue(sw, node);
    }
    catch( Exception e )
    {
      this.logger.error("Message: " + e.getMessage(), e);
    }
    
    return sw.toString();
  }
  
  /**
   * Compares all the resources in the list and determines what is the least
   * utilized.  The comparison is done by checking the difference between the
   * CPU and the assigned CPU values.  If the values are the same then compares
   * the memory the same way.  If the memory is also the same then it uses 
   * the number of tasks as the differentiator
   * 
   * This method invokes the CcdpVMResource.leastUsed( list, true ) method to
   * indicate that only take into account resources that are running and 
   * available
   *  
   * @param resources a list of resources to compare
   * 
   * @return the resource that is used the least
   */
  public static CcdpVMResource leastUsed( List<CcdpVMResource> resources)
  {
    return CcdpVMResource.leastUsed(resources, true);
  }
  
  
  /**
   * Compares all the resources in the list and determines what is the least
   * utilized.  The comparison is done by checking the difference between the
   * CPU and the assigned CPU values.  If the values are the same then compares
   * the memory the same way. If the memory is also the same then it uses 
   * the number of tasks as the differentiator
   * 
   * If the onlyRunning flag is set it only considers those resources that are
   * ready (the status is set to RUNNING) to process tasks.
   * 
   * @param resources a list of resources to compare
   * @param onlyRunning flag indicating to consider only resources that are 
   *        running
   * 
   * @return the resource that is used the least
   */
  public static CcdpVMResource leastUsed( List<CcdpVMResource> resources, 
                                          boolean onlyRunning )
  {
    boolean first = true;
    CcdpVMResource least = null;
    
    // comparing all the resources
    for( CcdpVMResource res : resources )
    {
      // consider only running VMs
      if( onlyRunning && !ResourceStatus.RUNNING.equals(res.getStatus()) )
        continue;
      
      // if is just the first one, then just set it as the least one
      if( first )
      {
        least = res;
        first = false;
        continue;
      }
      // get the difference of what it has minus what has been assigned
      double currCPU = least.getCPU() - least.getAssignedCPU();
      double cpu = res.getCPU() - res.getAssignedCPU();
      
      // if the current least is less than the new one means the new one has
      // more unused resources.  If they are the same then let's check the 
      // memory and finally the number of tasks running
      //
      if( currCPU < cpu )
        least = res;
      else if( least.getAssignedCPU() == res.getAssignedCPU() )
      {
        double currMem = least.getMEM() - least.getAssignedMEM();
        double mem = res.getMEM() - res.getAssignedMEM();
        if( currMem < mem )
          least = res;
        else if( least.getAssignedMEM() == res.getAssignedMEM() )
        {
          if( res.getNumberTasks() < least.getNumberTasks() )
          least = res;
        }
      }
    }
    
    return least;
  }
}
