package com.axios.ccdp.resources;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.fusesource.hawtbuf.ByteArrayInputStream;

import com.amazonaws.services.route53.model.InvalidArgumentException;
import com.axios.ccdp.tasking.CcdpTaskRequest;
import com.axios.ccdp.utils.CcdpUtils.CcdpNodeType;
import com.fasterxml.jackson.annotation.JsonGetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Class used to represent a resource that can be used for data processing.  The
 * structure of this class looks as follow:
 * 
 * {
 *  "instance-id" : "8716229f-1f00-4132-853a-a5ab37d61d50",
 *  "agent-id" : "d1e90c35-e0c4-482a-9c4f-cb610407caa1",
 *  "session-id" : "user-session-2",
 *  "cpu" : 4.0,
 *  "assigned-cpu" : 0.25,
 *  "mem" : 8000.0,
 *  "assigned-mem" : 2048.0,
 *  "disk" : 2000.0,
 *  "disk-mem" : 500.0,
 *  "status" : "RUNNING",
 *  "tasks" : [ 
 *  
 *      {
 *        "task-id" : "csv_reader",
 *        "name" : "Csv File Reader",
 *        "description" : null,
 *        "state" : "PENDING",
 *        "class-name" : "tasks.csv_demo.CsvReader",
 *        "node-type" : "ec2",
 *        "reply-to" : "The Sender",
 *        "agent-id" : null,
 *        "session-id" : null,
 *        "retries" : 3,
 *        "submitted" : false,
 *        "launched-time" : 0,
 *        "cpu" : 10.0,
 *        "mem" : 128.0,
 *        "command" : "[python, /opt/modules/CsvReader.python]",
 *        "configuration" : "{filename=${CCDP_HOME}/data/csv_test_file.csv}",
 *        "input-ports" : [ {
 *            "port-id" : "from-exterior",
 *            "input-ports" : [ "source-1", "source-2" ],
 *            "output-ports" : [ "dest-1", "dest-2" ]
 *          } ],
 *          "output-ports" : [ ]
 *      }
 *    ]
 * }
 * 
 * @author Oscar E. Ganteaume
 *
 */
@JsonIgnoreProperties({"free"})
public class CcdpVMResource implements Serializable
{
  /**
   * Randomly generated version id used during serialization
   */
  private static final long serialVersionUID = -705689459501099746L;
  /**
   * All the different states a resource can have
   */
  public enum ResourceStatus { OFFLINE, LAUNCHED, INITIALIZING, REASSIGNED, 
                      RUNNING, STOPPED, TERMINATED, SHUTTING_DOWN, FAILED }
  /**
   * Generates all the JSON objects
   */
  private ObjectMapper mapper = new ObjectMapper();
  /**
   * The unique identifier to distinguish this VM
   */
  private String instanceId = null;
  /**
   * Stores what type of resource is running in this VM
   */
  private CcdpNodeType nodeType = CcdpNodeType.DEFAULT;
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
  private double totalCPU = 0;
  private double cpuLoad = 0;
  private double memLoad = 0;
  
  /**
   * The total amount of MEM available for this resource to use
   */
  private double totalMEM = 0;
  private double freeMEM = 0;
  
  /**
   * The total amount of Disk space available for this resource to use
   */
  private double totalDisk = 0;
  private double freeDisk = 0;
  
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
  private long last_assignment = System.currentTimeMillis();
  /**
   * Whether or not this resource was allocated to run a single task
   */
  private boolean isSingleTasked = false;
  /**
   * Stores all the tags assigned to this resource
   */
  private Map<String, String> tags = new HashMap<>();
  
  public CcdpVMResource()
  {
    
  }
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
              this.setStatus(ResourceStatus.RUNNING);
            }
            else if( txt.equals("initializing") )
            {
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
  @JsonGetter("instance-id")
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
  @JsonSetter("instance-id")
  public void setInstanceId(String instanceId)
  {
    if( instanceId == null )
      throw new InvalidArgumentException("The instance id cannot be null");
    
    this.instanceId = instanceId;
  }

  /**
   * @return the assignedCPU
   */
  @JsonGetter("assigned-cpu")
  public double getAssignedCPU()
  {
    return assignedCPU;
  }

  /**
   * @param assignedCPU the assignedCPU to set
   */
  @JsonSetter("assigned-cpu")
  public void setAssignedCPU(double assignedCPU)
  {
    if( assignedCPU < 0)
      throw new IllegalArgumentException("The assigned CPU needs to be >= 0");
    
    this.assignedCPU = assignedCPU;
  }

  /**
   * @return the assignedMEM
   */
  @JsonGetter("assigned-mem")
  public double getAssignedMemory()
  {
    return assignedMEM;
  }

  /**
   * @param assignedMEM the assignedMEM to set
   */
  @JsonSetter("assigned-mem")
  public void setAssignedMEM(double assignedMEM)
  {
    if( assignedMEM < 0)
      throw new IllegalArgumentException("The assigned MEM needs to be >= 0");
    this.assignedMEM = assignedMEM;
  }

  /**
   * @return the cPU
   */
  @JsonGetter("total-cpu")
  public double getCPU()
  {
    return totalCPU;
  }

  /**
   * @param cPU the cPU to set
   */
  @JsonSetter("total-cpu")
  public void setCPU(double cPU)
  {
    if( cPU < 0)
      throw new IllegalArgumentException("The CPU needs to be > 0");
    
    totalCPU = cPU;
  }

  /**
   * @return the cPU
   */
  @JsonGetter("system-cpu-load")
  public double getCPULoad()
  {
    return this.cpuLoad;
  }

  /**
   * @param cPU the cPU to set
   */
  @JsonSetter("system-cpu-load")
  public void setCPULoad(double cPU)
  {
    if( cPU < 0)
      throw new IllegalArgumentException("The CPU needs to be > 0");
    
    this.cpuLoad = cPU;
  }
  
  /**
   * @return the mem
   */
  @JsonGetter("system-mem-load")
  public double getMemLoad()
  {
    return this.memLoad;
  }

  /**
   * @param mem the mem to set
   */
  @JsonSetter("system-mem-load")
  public void setMemLoad(double mem)
  {
    if( mem < 0)
      throw new IllegalArgumentException("The Mem needs to be > 0");
    
    this.memLoad = mem;
  }
  
  
  /**
   * @return the assignedDisk
   */
  @JsonGetter("assigned-disk")
  public double getAssignedDisk()
  {
    return assignedDisk;
  }

  /**
   * @param assignedDisk the assignedDisk to set
   */
  @JsonSetter("assigned-disk")
  public void setAssignedDisk(double assignedDisk)
  {
    this.assignedDisk = assignedDisk;
  }

  /**
   * @return the disk
   */
  @JsonGetter("total-disk-space")
  public double getDisk()
  {
    return totalDisk;
  }

  /**
   * @param disk the disk to set
   */
  @JsonSetter("total-disk-space")
  public void setDisk(double disk)
  {
    if( disk < 0)
      throw new IllegalArgumentException("The Disk needs to be > 0");
    
    this.totalDisk = disk;
  }

  /**
   * @return the disk
   */
  @JsonGetter("free-disk-space")
  public double getFreeDiskspace()
  {
    return freeDisk;
  }

  /**
   * @param disk the disk to set
   */
  @JsonSetter("free-disk-space")
  public void setFreeDiskSpace(double disk)
  {
    if( disk < 0)
      throw new IllegalArgumentException("The Disk needs to be > 0");
    
    this.freeDisk = disk;
  }
  
  /**
   * @return the mEM
   */
  @JsonGetter("total-mem")
  public double getTotalMemory()
  {
    return totalMEM;
  }

  /**
   * @param mEM the mEM to set
   */
  @JsonSetter("total-mem")
  public void setTotalMemory(double mEM)
  {
    if( mEM < 0)
      throw new IllegalArgumentException("The total memory needs to be > 0");
    totalMEM = mEM;
  }

  /**
   * @return the mEM
   */
  @JsonGetter("free-mem")
  public double getFreeMemory()
  {
    return freeMEM;
  }

  /**
   * @param mEM the mEM to set
   */
  @JsonSetter("free-mem")
  public void setFreeMemory(double mEM)
  {
    if( mEM < 0)
      throw new IllegalArgumentException("The free memory needs to be > 0");
    freeMEM = mEM;
  }
  
  /**
   * Sets all the tags assigned to the resource 
   * 
   * @return all the tags assigned to the resource
   */
  public Map<String, String> getTags()
  {
    return tags;
  }

  /**
   * Gets all the tags assigned to the resource 
   * 
   * @param tags all the tags assigned to the resource
   */
  public void setTags(Map<String, String> tags)
  {
    if( tags != null )
      this.tags = tags;
  }

  /**
   * @return the assignedSession
   */
  @JsonGetter("session-id")
  public String getAssignedSession()
  {
    return assignedSession;
  }

  /**
   * @param assignedSession the assignedSession to set
   */
  @JsonSetter("session-id")
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
  @JsonGetter("agent-id")
  public String getAgentId()
  {
    return agentId;
  }

  /**
   * @param agentId the agentId to set
   */
  @JsonSetter("agent-id")
  public void setAgentId(String agentId)
  {
    this.agentId = agentId;
  }

  /**
   * @return the nodeType
   */
  @JsonGetter("node-type")
  public String getNodeTypeAsString()
  {
    return this.nodeType.name();
  }

  /**
   * @return the nodeType
   */
  public CcdpNodeType getNodeType()
  {
    return this.nodeType;
  }
  
  /**
   * @param nodeType the nodeType to set
   */
  @JsonSetter("node-type")
  public void setNodeType(String nodeType)
  {
    this.nodeType = CcdpNodeType.valueOf(nodeType);
  }
  
  /**
   * @param nodeType the nodeType to set
   */
  public void setNodeType(CcdpNodeType nodeType)
  {
    this.nodeType = nodeType;
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
  @JsonGetter("single-task")
  public String getSingleTask()
  {
    return singleTask;
  }

  /**
   * @param singleTask the singleTask to set
   */
  @JsonSetter("single-task")
  public void setSingleTask(String singleTask)
  {
    this.singleTask = singleTask;
    if( singleTask != null )
      this.isSingleTasked = true;
    else
      this.isSingleTasked = false;
  }

  /**
   * Gets whether or not this resource was allocated to a single task.
   * 
   * @return whether or not this resource was allocated to a single task.
   */
  @JsonGetter("is-single-tasked")
  public boolean isSingleTasked()
  {
    return this.isSingleTasked;
  }
  
  
  /**
   * Returns true if there are no tasks running on this VM or false otherwise
   * 
   * @return true if there are no tasks running on this VM or false otherwise
   */
  public boolean isFree()
  {
    return this.tasks.isEmpty();
  }

  
  /**
   * Sets whether or not this resource was allocated to a single task.
   * 
   * @param singleTasked whether or not this resource was allocated to a single 
   *        task.
   */
  @JsonSetter("is-single-tasked")
  public void isSingleTasked(boolean singleTasked)
  {
    this.isSingleTasked = singleTasked;
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
  @JsonGetter("tasks")
  public List<CcdpTaskRequest> getTasks()
  {
    return this.tasks;
  }
  
  /**
   * Gets the total number of tasks assigned to this resource
   * 
   * @return the total number of tasks assigned to this resource
   */
  @JsonIgnore
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
  public boolean removeTasks( List<CcdpTaskRequest> tasks )
  {
    return this.tasks.removeAll(tasks);
  }
  
  /**
   * Removes all the tasks stored in this resource
   * 
   */
  public void removeAllTasks()
  {
    this.tasks = new ArrayList<CcdpTaskRequest>();
  }
  
  /**
   * Gets the last time a task was added to this resource.  If no task has 
   * been assigned then the time represents when this object was created.
   * 
   * @return the last time a task was added to this resource or the time 
   *         this thread was created
   */
  @JsonGetter("last-assignment")
  public long getLastAssignmentTime()
  {
    return this.last_assignment;
  }
  
  /**
   * Sets the last time a task was added to this resource.  If no task has 
   * been assigned then the time represents when this object was created.
   * 
   * @param assignmentTime the last time a task was added to this resource or  
   *        the time this thread was created
   */
  @JsonSetter("last-assignment")
  public void setLastAssignmentTime(long assignmentTime)
  {
    this.last_assignment = assignmentTime;
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
   * Returns a JSON like string containing information about this object.  The
   * structure of this class looks as follow:
   * 
   * {
   *  "instance-id" : "8716229f-1f00-4132-853a-a5ab37d61d50",
   *  "agent-id" : "d1e90c35-e0c4-482a-9c4f-cb610407caa1",
   *  "session-id" : "user-session-2",
   *  "cpu" : 4.0,
   *  "assigned-cpu" : 0.25,
   *  "mem" : 8000.0,
   *  "assigned-mem" : 2048.0,
   *  "disk" : 2000.0,
   *  "disk-mem" : 500.0,
   *  "status" : "RUNNING",
   *  "tasks" : [ 
   *  
   *      {
   *        "task-id" : "csv_reader",
   *        "name" : "Csv File Reader",
   *        "description" : null,
   *        "state" : "PENDING",
   *        "class-name" : "tasks.csv_demo.CsvReader",
   *        "node-type" : "ec2",
   *        "reply-to" : "The Sender",
   *        "agent-id" : null,
   *        "session-id" : null,
   *        "retries" : 3,
   *        "submitted" : false,
   *        "launched-time" : 0,
   *        "cpu" : 10.0,
   *        "mem" : 128.0,
   *        "command" : "[python, /opt/modules/CsvReader.python]",
   *        "configuration" : "{filename=${CCDP_HOME}/data/csv_test_file.csv}",
   *        "input-ports" : [ {
   *            "port-id" : "from-exterior",
   *            "input-ports" : [ "source-1", "source-2" ],
   *            "output-ports" : [ "dest-1", "dest-2" ]
   *          } ],
   *          "output-ports" : [ ]
   *      }
   *    ]
   * }
   * 
   * 
   * @return a JSON like string containing information about this object
   */
  public String toString()
  {
    String str = null;
    
    try
    {
      str = mapper.writeValueAsString(this);
    }
    catch( Exception e )
    {
      throw new RuntimeException("Could not write Json " + e.getMessage() );
    }
    
    return str;
  }

  /**
   * Prints the contents of the object using a more human readable form.
   * 
   * @return a String representation of the object using a more human friendly
   *         formatting
   */
  public String toPrettyPrint()
  {
    String str = null;
    
    try
    {
      ObjectMapper mapper = new ObjectMapper();
      mapper.enable(SerializationFeature.INDENT_OUTPUT);
      str = mapper.writeValueAsString(this);
    }
    catch( Exception e )
    {
      throw new RuntimeException("Could not write Json " + e.getMessage() );
    }
    
    return str;
  }
  
  /**
   * Generates a JSON representation of this object.  the structure of this 
   * class looks as follow:
   * 
   * {
   *  "instance-id" : "8716229f-1f00-4132-853a-a5ab37d61d50",
   *  "agent-id" : "d1e90c35-e0c4-482a-9c4f-cb610407caa1",
   *  "session-id" : "user-session-2",
   *  "cpu" : 4.0,
   *  "assigned-cpu" : 0.25,
   *  "mem" : 8000.0,
   *  "assigned-mem" : 2048.0,
   *  "disk" : 2000.0,
   *  "disk-mem" : 500.0,
   *  "status" : "RUNNING",
   *  "tasks" : [ 
   *  
   *      {
   *        "task-id" : "csv_reader",
   *        "name" : "Csv File Reader",
   *        "description" : null,
   *        "state" : "PENDING",
   *        "class-name" : "tasks.csv_demo.CsvReader",
   *        "node-type" : "ec2",
   *        "reply-to" : "The Sender",
   *        "agent-id" : null,
   *        "session-id" : null,
   *        "retries" : 3,
   *        "submitted" : false,
   *        "launched-time" : 0,
   *        "cpu" : 10.0,
   *        "mem" : 128.0,
   *        "command" : "[python, /opt/modules/CsvReader.python]",
   *        "configuration" : "{filename=${CCDP_HOME}/data/csv_test_file.csv}",
   *        "input-ports" : [ {
   *            "port-id" : "from-exterior",
   *            "input-ports" : [ "source-1", "source-2" ],
   *            "output-ports" : [ "dest-1", "dest-2" ]
   *          } ],
   *          "output-ports" : [ ]
   *      }
   *    ]
   * }
   * 
   * 
   * @return a JSON object representing this object
   */
  public ObjectNode toJSON()
  {
    return this.mapper.convertValue( this, ObjectNode.class );
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
        double currMem = least.getTotalMemory() - least.getAssignedMemory();
        double mem = res.getTotalMemory() - res.getAssignedMemory();
        if( currMem < mem )
          least = res;
        else if( least.getAssignedMemory() == res.getAssignedMemory() )
        {
          if( res.getNumberTasks() < least.getNumberTasks() )
          least = res;
        }
      }
    }
    
    return least;
  }
  
  /** 
   * Generates a String representing this object serialized.
   * 
   * @return a String representation of this object serialized
   * @throws IOException an IOException is thrown if there is a problem during
   *         the serialization of the object
   */
  public String toSerializedString( ) throws IOException 
  {
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    ObjectOutputStream oos = new ObjectOutputStream( baos );
    oos.writeObject( this );
    oos.close();
    return Base64.getEncoder().encodeToString(baos.toByteArray()); 
  }  
  
  /** Reads a serialized string object and generates a CcdpVMResource object
   * 
   * @param s the string representing the serialized version of the object
   * @return a CcdpVMResource object that was serialized previously
   * 
   * @throws IOException is thrown if the object cannot be de-serialized
   * @throws ClassNotFoundException is thrown if the stream cannot be read into
   *         an object
   */
  public static CcdpVMResource fromSerializedString( String s ) 
                                  throws IOException, ClassNotFoundException 
  {
     byte [] data = Base64.getDecoder().decode( s );
     ObjectInputStream ois = new ObjectInputStream( 
                                     new ByteArrayInputStream(  data ) );
     Object o  = ois.readObject();
     ois.close();
     return (CcdpVMResource)o;
  }
   
}
