package com.axios.ccdp.mesos.fmwk;

import java.util.ArrayList;
import java.util.List;
import java.util.StringJoiner;
import java.util.UUID;

import org.apache.log4j.Logger;
import org.apache.mesos.Protos;
import org.apache.mesos.Protos.CommandInfo;
import org.apache.mesos.Protos.ExecutorInfo;
import org.apache.mesos.Protos.Resource;
import org.apache.mesos.Protos.SlaveID;
import org.apache.mesos.Protos.TaskID;
import org.apache.mesos.Protos.TaskInfo;
import org.apache.mesos.Protos.Value;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.protobuf.ByteString;

/**
 * Simple class used to store information about a Task to be executed.  The task
 * information includes items such as the amount of CPU and memory it requires
 * to run, the command to execute, and some optional configuration in case is
 * required.  Instances of this class are intended to be created by Mesos 
 * Scheduler objects.
 * 
 * @author Oscar E. Ganteaume
 *
 */
public class CcdpJob
{
  /**
   * Generates debug print statements based on the verbosity level.
   */
  private Logger logger = Logger.getLogger(CcdpJob.class.getName());
  /**
   * All the different states the job can be at any given time
   */
  public enum JobState { PENDING, STAGING, RUNNING, SUCCESSFUL, FAILED }
  /**
   * Creates all the ArrayNode and ObjectNode
   */
  private static ObjectMapper mapper = new ObjectMapper();
  /**
   * Stores the current status of the task
   */
  private JobState status;
  /**
   * A unique identifier for this task
   */
  private String id;
  /**
   * The unique identifier of the Mesos Agent responsible for the task
   */
  private SlaveID slaveId;
  /**
   * The number of times this task needs to be executed before set it as failed
   */
  private int retries = 0;
  /**
   * The amount of CPU required to run this task
   */
  private double cpus = 0.0;
  /**
   * The amount of memory required to run this task
   */
  private double mem = 0.0;
  /**
   * The command to execute
   */
  private String command;
  /**
   * Flag used to indicate whether or not this job has been submitted
   */
  private boolean submitted;
  /**
   * Stores the configuration for this job
   */
  private ObjectNode config;;
  
  /**
   * Instantiates a new object and sets its status to PENDING, and the number
   * of retries to 3.  The number of retries can be changed in the configuration
   * portion of the JSON command.  It uses a random UUID as the ID for this job
   * 
   */
  public CcdpJob( )
  {
    this( UUID.randomUUID().toString() );
  }
  
  /**
   * Instantiates a new object and sets its status to PENDING, and the number
   * of retries to 3.  The number of retries can be changed in the configuration
   * portion of the JSON command.  The UUID is assigned to this Job to identify
   * it.
   * 
   * @param uuid The unique identifier for this object
   */
  public CcdpJob( String uuid )
  {
    this.logger.debug("Setting up a new Job");
    this.status = JobState.PENDING;
    this.id = uuid;
    this.retries = 3;
    this.submitted = false;
    
    CcdpJob.mapper.enable(SerializationFeature.INDENT_OUTPUT);
  }
  
  /**
   * Gets the amount of CPU this job requires to run
   * 
   * @return the amount of CPU this job requires to run
   */
  public double getCpus()
  {
    return cpus;
  }
  
  /**
   * Sets the amount of CPU this job requires to run.  If the amount is less 
   * than zero then it throw an InvalidArgumentException.  The values of the 
   * CPU influence the behavior of the system as follow:
   * 
   *  - CPU = 0:        Let the Scheduler decide where to run it
   *  - 0 > CPU < 100:  Use the first VM with enough resources to run the task
   *  - CPU = 100:      Run this task by itself on a new VM
   * 
   * @param cpus the amount of CPU this job requires to run
   * 
   * @throws InvalidArgumentException an InvalidArgumentException is thrown if
   *         the value for this resource is less than zero
   */
  public void setCpus(double cpus) throws IllegalArgumentException
  {
    if( cpus < 0 )
      throw new IllegalArgumentException("The CPUS cannot be less than zero");
    this.cpus = cpus;
  }

  /**
   * Gets the amount of memory this job requires to run
   * 
   * @return the amount of memory this job requires to run
   */
  public double getMemory()
  {
    return mem;
  }

  /**
   * Sets the amount of memory this job requires to run.  If the amount is less
   * than zero then it throw an InvalidArgumentException
   * 
   * @param mem the amount of memory this job requires to run
   * 
   * @throws InvalidArgumentException an InvalidArgumentException is thrown if
   *         the value for this resource is less than zero
   */
  public void setMemory(double mem) throws IllegalArgumentException
  {
    if( mem < 0 )
      throw new IllegalArgumentException("The memory cannot be less than zero");
    this.mem = mem;
  }

  /**
   * Gets the command to be executed for this task
   * 
   * @return the command to be executed for this task
   */
  public String getCommand()
  {
    return command;
  }

  /**
   * Sets the command to be executed by this task.  If the command is null then 
   * it throw an InvalidArgumentException
   * 
   * @param command the command to be executed by this task
   * 
   * @throws InvalidArgumentException an InvalidArgumentException is thrown if
   *         the command is null
   */
  public void setCommand(String command) throws IllegalArgumentException
  {
    if( command == null )
      throw new IllegalArgumentException("The command cannot be null");
    this.command = command;
  }

  /**
   * Gets the optional configuration to pass to the Mesos Executor
   * 
   * @return the optional configuration to pass to the Mesos Executor
   */
  public ObjectNode getConfig()
  {
    return config;
  }

  /**
   * Sets the optional configuration to be executed by this task.  
   * 
   */
  public void setConfig(ObjectNode config)
  {
    this.config = config;
  }

  /**
   * Gets the unique identifier for this task
   * 
   * @return the unique identifier for this task
   */
  public String getId()
  {
    return this.id;
  }
  
  /**
   * Gets the current status of the job
   * 
   * @return the current status of the job
   */
  public JobState getStatus()
  {
    return this.status;
  }
  
  /**
   * Gets the unique identifier of the Mesos Agent responsible for running this
   * job
   * 
   * @return the unique identifier of the Mesos Agent responsible for running
   *         this job
   */
  public SlaveID getSlaveId()
  {
    return this.slaveId;
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
  public TaskInfo makeTask(SlaveID targetSlave, ExecutorInfo exec)
  {
    this.logger.debug("Making Task at Slave " + targetSlave.getValue());
    TaskID id = TaskID.newBuilder().setValue(this.id).build();
    
    Protos.TaskInfo.Builder bldr = TaskInfo.newBuilder();
    bldr.setName("task " + id.getValue());
    bldr.setTaskId(id);
    // Adding the CPU
    Protos.Resource.Builder resBldr = Resource.newBuilder();
    resBldr.setName("cpus");
    resBldr.setType(Value.Type.SCALAR);
    resBldr.setScalar(Value.Scalar.newBuilder().setValue(this.cpus));
    Resource cpuRes = resBldr.build();
    bldr.addResources(cpuRes);
    this.logger.debug("Adding CPU Resource " + cpuRes.toString());
    
    // Adding the Memory
    resBldr.setName("mem");
    resBldr.setType(Value.Type.SCALAR);
    resBldr.setScalar(Value.Scalar.newBuilder().setValue(this.mem));
    Resource memRes = resBldr.build();
    bldr.addResources(memRes);
    this.logger.debug("Adding MEM Resource " + memRes.toString());
    
    Protos.CommandInfo.Builder cmdBldr = CommandInfo.newBuilder();
    cmdBldr.setValue(this.command);
    this.logger.debug("Running Command: " + command);
    
    bldr.setSlaveId(targetSlave);
    this.slaveId = targetSlave;
    
    bldr.setExecutor(exec);
    ObjectNode json = CcdpJob.mapper.createObjectNode();
    
    json.put("cmd", this.command);
    // if there is a configuration, add it
    if( this.config != null )
      json.set("cfg", this.config);
    
    bldr.setData(ByteString.copyFrom(json.toString().getBytes()));
    return bldr.build();
  }

  /**
   * Creates a new Job using the information from a JSON object.  If there is a
   * problem parsing the object then it throws a JSONException 
   * 
   * @param obj the JSON object containing details of the Job to execute
   * @return an instance of this class with all the fields appropriate set
   * 
   * @throws JSONException a JSONException is thrown if there is a problem 
   *         parsing the JSON object
   */
  public static CcdpJob fromJSON( JsonNode obj) 
  {
    CcdpJob job = new CcdpJob();
    if( obj.has("cpu") )
      job.cpus = obj.get("cpu").asDouble();
    else
      job.cpus = 0.01;
    if( obj.has("mem") )
      job.mem = obj.get("mem").asDouble();
    else
      job.mem = 0.01;
    if( obj.has("command") )
    {
      ArrayNode args = (ArrayNode)obj.get("command");
      List<String> list = new ArrayList<String>();
      for( JsonNode arg : args )
      {
        String cmd = arg.asText();
        list.add(cmd);
      }
      StringJoiner joiner = new StringJoiner(" ");
      list.forEach(joiner::add);
      job.command = joiner.toString();
    }
    else
      System.err.println("The command field is required");
    
    if( obj.has("cfg") )
      job.setConfig(obj.get("cfg").deepCopy());
      
    return job;
  }
  
  /**
   * Saves the state of the job.  If there is a problem generating the JSON 
   * object it throws a JSONException
   * 
   * @throws JSONException a JSONException is thrown if there is a problem 
   *         generating the JSON object
   */
  private void saveState() 
  {
    //TODO Is this function really needed?????
    
    this.logger.info("Saving State");
    ObjectNode obj = CcdpJob.mapper.createObjectNode();
    
    obj.put("id", this.id);
    if( this.status == JobState.STAGING )
      obj.put("status", JobState.RUNNING.toString() );
    else
      obj.put("status", this.status.toString() );
    
    // storing all other fields
    obj.put("cpus", this.cpus);
    obj.put("mem", this.mem);
    obj.put("command", this.command);
    obj.put("retries", this.retries);
    obj.put("submitted", this.submitted);
    obj.put("slave-id", this.slaveId.toString());
    
  }
  
  /**
   * Gets a flag indicating whether or not this job has been already submitted 
   * or not
   * 
   * @return true if this job has been already submitted or false otherwise
   */
  public boolean isSubmitted()
  {
    return this.submitted;
  }
  
  /**
   * Sets the flag indicating whether or not this job has been already submitted 
   * or not
   * 
   * @param submitted flag indicating whether or not this job has been already 
   *        submitted or not
   */
  public void setSubmitted(boolean submitted)
  {
    this.submitted = submitted;
  }
  
  /**
   * Indicates that this job as been submitted to be executed and therefore it 
   * sets its status to STAGING 
   */
  public void launch() 
  {
    this.status = JobState.STAGING;
//    try
//    {
//      this.saveState();
//    }
//    catch( Exception e)
//    {
//      this.logger.error("Message: " + e.getMessage(), e);
//    }
  }

  /**
   * Indicates that this job started and therefore it sets its status to RUNNING 
   */
  public void started() 
  {
    this.status = JobState.RUNNING;
//    try
//    {
//      this.saveState();
//    }
//    catch( Exception e)
//    {
//      this.logger.error("Message: " + e.getMessage(), e);
//    }
  }

  /**
   * Indicates that this job has finished successfully and therefore it sets its 
   * status to SUCCESSFUL
   */
  public void succeed() 
  {
    this.status = JobState.SUCCESSFUL;
//    try
//    {
//      this.saveState();
//    }
//    catch( Exception e)
//    {
//      this.logger.error("Message: " + e.getMessage(), e);
//    }
  }

  /**
   * If the job has not reached the maximum number of retries it sets the status
   * to PENDING otherwise it decreases the number of retries and sets the status
   * to FAILED.
   */
  public void fail() 
  {
    if (this.retries == 0) 
    {
      this.status = JobState.FAILED;
    } 
    else 
    {
      this.retries--;
      this.status = JobState.PENDING;
    }
    
//    try
//    {
//      this.saveState();
//    }
//    catch( Exception e)
//    {
//      this.logger.error("Message: " + e.getMessage(), e);
//    }
  }
  
  public String toString()
  {
    ObjectNode node = CcdpJob.mapper.createObjectNode();
    node.put("id", this.id);
    if( this.slaveId != null )
      node.put("slave-id", this.slaveId.getValue());
    node.put("cpus", this.cpus);
    node.put("mem", this.mem);
    node.put("retries", this.retries);
    node.put("status", this.status.toString());
    node.put("command", this.command);
    node.set("config", this.config);
    
    return node.toString();
  }
}
