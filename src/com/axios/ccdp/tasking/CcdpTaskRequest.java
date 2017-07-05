package com.axios.ccdp.tasking;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.fusesource.hawtbuf.ByteArrayInputStream;

import com.axios.ccdp.utils.CcdpUtils.CcdpNodeType;
import com.fasterxml.jackson.annotation.JsonGetter;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * 
 * Simple class used to store information of a single task or job to execute.
 * The following is an example of the JSON structure used to defined this 
 * object:
 * 
 * {
 *   "task-id" : "csv_reader",
 *   "name" : "Csv File Reader",
 *   "description" : null,
 *   "state" : "PENDING",
 *   "class-name" : "tasks.csv_demo.CsvReader",
 *   "node-type" : "ec2",
 *   "reply-to" : "The Sender",
 *   "agent-id" : null,
 *   "session-id" : null,
 *   "retries" : 3,
 *   "submitted" : false,
 *   "launched-time" : 0,
 *   "cpu" : 10.0,
 *   "mem" : 128.0,
 *   "command" : "[python, /opt/modules/CsvReader.python]",
 *   "configuration" : "{filename=${CCDP_HOME}/data/csv_test_file.csv}",
 *   "input-ports" : [ {
 *     "port-id" : "from-exterior",
 *     "input-ports" : [ "source-1", "source-2" ],
 *     "output-ports" : [ "dest-1", "dest-2" ]
 *   } ],
 *   "output-ports" : [ ]
 * }
 * 
 * @author Oscar E. Ganteaume
 *
 */
public class CcdpTaskRequest implements Serializable
{
  /**
   * Random generated version id for serialization
   */
  private static final long serialVersionUID = -8881962810179967763L;
  /**
   * The minimum amount of CPU that can be allocated by Mesos.  Value less than
   * this causes the mesos-mater to fail (bug 7382)
   */
  public static final double MIN_CPU_REQ = 0.01;
  /**
   * The minimum amount of memory that can be allocated by Mesos. Value less
   * than this causes the mesos-mater to fail (bug 7382)
   */
  public static final double MIN_MEM_REQ = 32;
  
  /**
   * Generates all the JSON objects for this class
   */
  private ObjectMapper mapper = new ObjectMapper();
  
  /**
   * All the different states the job can be at any given time
   */
  public enum CcdpTaskState { PENDING, ASSIGNED, STAGING, RUNNING, SUCCESSFUL, 
                              FAILED, KILLED }
  /**
   * Stores the unique identifier for this task 
   */
  private String taskId;
  /** 
   * The human readable name of the Task 
   **/
  private String name;
  /** 
   * An optional description of this task 
   **/
  private String description;
  /** 
   * Sets the current state of this task, required for controlling 
   **/
  private CcdpTaskState state = CcdpTaskState.PENDING;
  /** 
   * Stores the class name of a module to execute if needed
   **/
  private String className;
  /** 
   * Indicates the node type where this task needs to run such as EMR, EC2, etc 
   **/
  private CcdpNodeType nodeType = CcdpNodeType.DEFAULT;
  /** 
   * The destination or entity to notify this task has change state 
   **/
  private String replyTo = "";
  /** 
   * The unique identifier of the Agent responsible for the task
   */
  private String hostId;
  /**
   * The number of times this task needs to be executed before set it as failed
   */
  private int retries = 0;
  /**
   * Indicates whether or not this task has been submitted for processing
   */
  private boolean submitted = false;
  /**
   * The amount of CPU this task requires to execute.  DO NOT MAKE THIS VALUE
   * LESS THAN 0.01 as it will cause the mesos-master to crash
   */
  private double cpu = 0.0;
  /**
   * The amount of memory this task requires to execute.  DO NOT MAKE THIS VALUE
   * LESS THAN 32 as it will cause the mesos-master to crash
   */
  private double mem = 0.0;
  /**
   * A list of arguments used to generate the command to be executed by the 
   * agent
   */
  private List<String> command = new ArrayList<String>();
  /**
   * A map of configuration to be used by the agent
   */
  private Map<String, String> configuration = new HashMap<String, String>();
  /**
   * A list of incoming data from the previous task
   */
  private List<CcdpPort> inputPorts = new ArrayList<CcdpPort>();
  /**
   * A list of ports to forward the data one is processed so they can be 
   * executed by the next task
   */
  private List<CcdpPort> outputPorts = new ArrayList<CcdpPort>();
  /**
   * The session this task belongs to
   */
  private String sessionId = null;
  /**
   * The time this task was launched
   */
  private long launchedTime = 0;
  
  /**
   * Instantiates a new Task using default values such as:
   * 
   *    TaskID:     Random UUID
   *    retries:    3
   *    submitted:  false
   *    TaskState:  PENDING
   */
  public CcdpTaskRequest()
  {
    this(UUID.randomUUID().toString() );
  }
  
  /**
   * Instantiates a new Task using the provided UUID and setting the default 
   * values such as:
   * 
   *    retries:    3
   *    submitted:  false
   *    TaskState:  PENDING
   *    
   * @param taskId the UUID used to identify this task
   */
  public CcdpTaskRequest(String taskId)
  {
    this.setTaskId(taskId);
    this.setState(CcdpTaskState.PENDING);
    
    this.retries = 3;
    this.submitted = false;
  }

  /**
   * @return the className
   */
  @JsonGetter("class-name")
  public String getClassName()
  {
    return className;
  }

  /**
   * @param className the className to set
   */
  @JsonSetter("class-name")
  public void setClassName(String className)
  {
    this.className = className;
  }

  /**
   * @return the configuration
   */
  public Map<String, String> getConfiguration()
  {
    return this.configuration;
  }

  /**
   * @param configuration the configuration to set
   */
  public void setConfiguration(Map<String, String> configuration)
  {
    this.configuration = configuration;
  }

  /**
   * @return the inputPorts
   */
  @JsonGetter("input-ports")
  public List<CcdpPort> getInputPorts()
  {
    return this.inputPorts;
  }

  /**
   * @param inputPorts the inputPorts to set
   */
  @JsonSetter("input-ports")
  public void setInputPorts(List<CcdpPort> inputPorts)
  {
    this.inputPorts = inputPorts;
  }

  /**
   * @return the outputPorts
   */
  @JsonGetter("output-ports")
  public List<CcdpPort> getOutputPorts()
  {
    return this.outputPorts;
  }

  /**
   * @param outputPorts the outputPorts to set
   */
  @JsonSetter("output-ports")
  public void setOutputPorts(List<CcdpPort> outputPorts)
  {
    this.outputPorts = outputPorts;
  }

//  @JsonGetter("task-state")
//  public CcdpTaskState getTaskState()
//  {
//    return this.state;
//  }
  
  /**
   * @return the cPU
   */
  @JsonGetter("cpu")
  public double getCPU()
  {
    return this.cpu;
  }

  /**
   * @param cpu the cPU to set
   */
  @JsonSetter("cpu")
  public void setCPU(double cpu)
  {
    this.cpu = cpu;
  }

  /**
   * @return the mEM
   */
  @JsonGetter("mem")
  public double getMEM()
  {
    return this.mem;
  }

  /**
   * @param mem the mEM to set
   */
  @JsonSetter("mem")
  public void setMEM(double mem)
  {
    this.mem = mem;
  }

  /**
   * @return the command
   */
  public List<String> getCommand()
  {
    return this.command;
  }

  /**
   * @param command the command to set
   */
  public void setCommand(List<String> command)
  {
    this.command = command;
  }

  /**
   * @return the taskId
   */
  @JsonGetter("task-id")
  public String getTaskId()
  {
    return this.taskId;
  }

  /**
   * @param taskId the taskId to set
   */
  @JsonSetter("task-id")
  public void setTaskId(String taskId)
  {
    this.taskId = taskId;
  }

  /**
   * @return the sessionId
   */
  @JsonGetter("session-id")
  public String getSessionId()
  {
    return this.sessionId;
  }

  /**
   * @param sessionId the sessionId to set
   */
  @JsonSetter("session-id")
  public void setSessionId(String sessionId)
  {
    this.sessionId = sessionId;
  }

  /**
   * @return the name
   */
  public String getName()
  {
    return this.name;
  }

  /**
   * @param name the name to set
   */
  public void setName(String name)
  {
    this.name = name;
  }

  /**
   * @return the description
   */
  public String getDescription()
  {
    return this.description;
  }

  /**
   * @param description the description to set
   */
  public void setDescription(String description)
  {
    this.description = description;
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
   * @return the replyTo
   */
  @JsonGetter("reply-to")
  public String getReplyTo()
  {
    return this.replyTo;
  }

  /**
   * @param replyTo the replyTo to set
   */
  @JsonSetter("reply-to")
  public void setReplyTo(String replyTo)
  {
    this.replyTo = replyTo;
  }

  /**
   * @return the state
   */
  public CcdpTaskState getState()
  {
    return this.state;
  }

  /**
   * @param state the state to set
   */
  public void setState(CcdpTaskState state)
  {
    this.state = state;
  }

  /**
   * @return the hostId
   */
  @JsonGetter("host-id")
  public String getHostId()
  {
    return this.hostId;
  }

  /**
   * @param hostId the hostId to set
   */
  @JsonSetter("host-id")
  public void setHostId(String hostId)
  {
    this.hostId = hostId;
  }

  /**
   * @return the retries
   */
  public int getRetries()
  {
    return this.retries;
  }

  /**
   * @param retries the retries to set
   */
  public void setRetries(int retries)
  {
    this.retries = retries;
  }

  /**
   * @return the submitted
   */
  @JsonGetter("submitted")
  public boolean isSubmitted()
  {
    return this.submitted;
  }

  /**
   * Gets the time when this task was launched in milliseconds.
   * 
   * @return the time when this task was launched in milliseconds.
   */
  @JsonGetter("launched-time")
  public long getLaunchedTimeMillis()
  {
    return this.launchedTime;
  }

  /**
   * Sets the time when this task was launched in milliseconds.
   * 
   * @param time the time when this task was launched in milliseconds.
   */
  @JsonSetter("launched-time")
  public void setLaunchedTimeMillis(long time)
  {
    this.launchedTime = time;
  }
  
  /**
   * @param submitted the submitted to set
   */
  @JsonSetter("submitted")
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
    this.state = CcdpTaskState.STAGING;
  }

  /**
   * Indicates that this job has been assigned and therefore it sets its status
   * to ASSIGNED
   */
  public void assigned() 
  {
    this.state = CcdpTaskState.ASSIGNED;
  }
  
  /**
   * Indicates that this job has been killed and therefore it sets its status
   * to KILLED
   */
  public void killed() 
  {
    this.state = CcdpTaskState.KILLED;
  }
  
  /**
   * Indicates that this job started and therefore it sets its status to RUNNING 
   */
  public void started() 
  {
    this.launchedTime = System.currentTimeMillis();
    this.state = CcdpTaskState.RUNNING;
  }

  /**
   * Indicates that this job has finished successfully and therefore it sets its 
   * status to SUCCESSFUL
   */
  public void succeed() 
  {
    this.state = CcdpTaskState.SUCCESSFUL;
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
      this.state = CcdpTaskState.FAILED;
    } 
    else 
    {
      this.retries--;
      this.state = CcdpTaskState.PENDING;
      this.submitted = false;
      this.hostId = null;
    }
  }
  
  /**
   * Gets the string representation using the JSON style.  The following is an 
   * example of the JSON structure used to defined this object:
   * 
   * {
   *   "task-id" : "csv_reader",
   *   "name" : "Csv File Reader",
   *   "description" : null,
   *   "state" : "PENDING",
   *   "class-name" : "tasks.csv_demo.CsvReader",
   *   "node-type" : "ec2",
   *   "reply-to" : "The Sender",
   *   "agent-id" : null,
   *   "session-id" : null,
   *   "retries" : 3,
   *   "submitted" : false,
   *   "launched-time" : 0,
   *   "cpu" : 10.0,
   *   "mem" : 128.0,
   *   "command" : "[python, /opt/modules/CsvReader.python]",
   *   "configuration" : "{filename=${CCDP_HOME}/data/csv_test_file.csv}",
   *   "input-ports" : [ {
   *     "port-id" : "from-exterior",
   *     "input-ports" : [ "source-1", "source-2" ],
   *     "output-ports" : [ "dest-1", "dest-2" ]
   *   } ],
   *   "output-ports" : [ ]
   * }
   * 
   * @return A string representation of the object using JSON nomenclature
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
   * Gets a JSON representation of this task.  The following is an example of 
   * the JSON structure used to defined this object:
   * 
   * {
   *   "task-id" : "csv_reader",
   *   "name" : "Csv File Reader",
   *   "description" : null,
   *   "state" : "PENDING",
   *   "class-name" : "tasks.csv_demo.CsvReader",
   *   "node-type" : "ec2",
   *   "reply-to" : "The Sender",
   *   "agent-id" : null,
   *   "session-id" : null,
   *   "retries" : 3,
   *   "submitted" : false,
   *   "launched-time" : 0,
   *   "cpu" : 10.0,
   *   "mem" : 128.0,
   *   "command" : "[python, /opt/modules/CsvReader.python]",
   *   "configuration" : "{filename=${CCDP_HOME}/data/csv_test_file.csv}",
   *   "input-ports" : [ {
   *     "port-id" : "from-exterior",
   *     "input-ports" : [ "source-1", "source-2" ],
   *     "output-ports" : [ "dest-1", "dest-2" ]
   *   } ],
   *   "output-ports" : [ ]
   * }
   * 
   * 
   * @return a JSON object representing this task
   */
  public ObjectNode toJSON()
  {
    return this.mapper.convertValue( this, ObjectNode.class );
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
  
  /** Reads a serialized string object and generates a CcdpTaskRequest object
   * 
   * @param s the string representing the serialized version of the object
   * @return a CcdpTaskRequest object that was serialized previously
   * 
   * @throws IOException is thrown if the object cannot be de-serialized
   * @throws ClassNotFoundException is thrown if the stream cannot be read into
   *         an object
   */
  public static CcdpTaskRequest fromSerializedString( String s ) 
                                  throws IOException, ClassNotFoundException 
  {
     byte [] data = Base64.getDecoder().decode( s );
     ObjectInputStream ois = new ObjectInputStream( 
                                     new ByteArrayInputStream(  data ) );
     Object o  = ois.readObject();
     ois.close();
     return (CcdpTaskRequest)o;
  }
  
}
