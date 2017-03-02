package com.axios.ccdp.mesos.tasking;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.apache.log4j.Logger;

import com.fasterxml.jackson.annotation.JsonGetter;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * {"task-id": "cycles_selector",
   "name": "Cycles Selector",
   "class-name": "tasks.emr_demo.CyclesSelector",
   "ccdp-type": "ec2",
   "max-instances": 1,
   "min-instances": 1,          
   "configuration": { "number-cycles": "10000", 
                      "wait-time": "5" },
   "input-ports": [],
   "output-ports": [ 
                    {"port-id": "cycles_selector-1",
                     "to": [ "pi_estimator_input-1" ]
                    }
                   ]
 }
             
 * @author Oscar E. Ganteaume
 *
 */
public class CcdpTaskRequest
{
  /**
   * Generates debug print statements based on the verbosity level.
   */
  private Logger logger = Logger.getLogger(CcdpTaskRequest.class.getName());
  /**
   * All the different states the job can be at any given time
   */
  public enum CcdpTaskState { PENDING, ASSIGNED, STAGING, RUNNING, SUCCESSFUL, FAILED }
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
  private String nodeType;
  /** 
   * The destination or entity to notify this task has change state 
   **/
  private String replyTo = "";
  /** 
   * The unique identifier of the Agent responsible for the task
   */
  private String agentId;
  /**
   * The number of times this task needs to be executed before set it as failed
   */
  private int retries = 0;
  /**
   * Indicates whether or not this task has been submitted for processing
   */
  private boolean submitted = false;
  /**
   * The amount of CPU this task requires to execute
   */
  private double cpu = 0.000001;
  /**
   * The amount of memory this task requires to execute
   */
  private double mem = 0.000001;
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
    this.logger.debug("Creating a new CCDP Task Request");
    this.setTaskId(taskId);
    this.setState(CcdpTaskState.PENDING);
    
    this.retries = 3;
    this.submitted = false;
  }

  /**
   * @return the className
   */
  @JsonGetter("classname")
  public String getClassName()
  {
    return className;
  }

  /**
   * @param className the className to set
   */
  @JsonSetter("classname")
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
  @JsonSetter("putput-ports")
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
   * @param cPU the cPU to set
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
   * @param mEM the mEM to set
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
  public String getNodeType()
  {
    return this.nodeType;
  }

  /**
   * @param nodeType the nodeType to set
   */
  @JsonSetter("node-type")
  public void setNodeType(String nodeType)
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
   * @return the agentId
   */
  @JsonGetter("agent-id")
  public String getAgentId()
  {
    return this.agentId;
  }

  /**
   * @param agentId the agentId to set
   */
  @JsonSetter("agent-id")
  public void setAgentId(String agentId)
  {
    this.logger.debug("\n\nSetting Agent id to " + agentId + "\n\n");
    this.agentId = agentId.trim();
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
  public boolean isSubmitted()
  {
    return this.submitted;
  }

  /**
   * @param submitted the submitted to set
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
   * Indicates that this job started and therefore it sets its status to RUNNING 
   */
  public void started() 
  {
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
    }
  }
  
  /**
   * Gets the string representation using the JSON style.
   * 
   * @return A string representation of the object using JSON nomenclature
   */
  public String toString()
  {
    ObjectMapper mapper = new ObjectMapper();
    ObjectNode task = mapper.createObjectNode();
    task.put("task-id",       this.taskId);
    task.put("name",          this.name);
    task.put("description",   this.description);
    task.put("state",         this.state.toString());
    task.put("classname",     this.className);
    task.put("node-type",     this.nodeType);
    task.put("reply-to",      this.replyTo);
    task.put("agent-id",      this.agentId);
    task.put("session-id",    this.sessionId);
    task.put("retries",       this.retries);
    task.put("submitted",     this.submitted);
    task.put("cpu",           this.cpu);
    task.put("mem",           this.mem);
    task.put("command",       this.command.toString());
    task.put("configuration", this.configuration.toString());
    task.put("input-ports",   this.inputPorts.toString());
    task.put("output-ports",  this.outputPorts.toString());
    
    
    return task.toString();
  }
}
