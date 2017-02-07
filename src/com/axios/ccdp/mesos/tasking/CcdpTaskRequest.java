package com.axios.ccdp.mesos.tasking;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.apache.log4j.Logger;

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
  public enum CcdpTaskState { PENDING, STAGING, RUNNING, SUCCESSFUL, FAILED }
  /**
   * Stores the unique identifier for this task 
   */
  private String TaskId;
  /** 
   * The human readable name of the Task 
   **/
  private String Name;
  /** 
   * An optional description of this task 
   **/
  private String Description;
  /** 
   * Sets the current state of this task, required for controlling 
   **/
  private CcdpTaskState state = CcdpTaskState.PENDING;
  /** 
   * Stores the class name of a module to execute if needed
   **/
  private String ClassName;
  /** 
   * Indicates the node type where this task needs to run such as EMR, EC2, etc 
   **/
  private String NodeType;
  /** 
   * The destination or entity to notify this task has change state 
   **/
  private String ReplyTo = "";
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
  private double CPU = 0.000001;
  /**
   * The amount of memory this task requires to execute
   */
  private double MEM = 0.000001;
  /**
   * A list of arguments used to generate the command to be executed by the 
   * agent
   */
  private List<String> Command = new ArrayList<String>();
  /**
   * A map of configuration to be used by the agent
   */
  private Map<String, String> Configuration = new HashMap<String, String>();
  /**
   * A list of incoming data from the previous task
   */
  private List<CcdpPort> InputPorts = new ArrayList<CcdpPort>();
  /**
   * A list of ports to forward the data one is processed so they can be 
   * executed by the next task
   */
  private List<CcdpPort> OutputPorts = new ArrayList<CcdpPort>();
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
  public String getClassName()
  {
    return ClassName;
  }

  /**
   * @param className the className to set
   */
  public void setClassName(String className)
  {
    this.ClassName = className;
  }

  /**
   * @return the configuration
   */
  public Map<String, String> getConfiguration()
  {
    return Configuration;
  }

  /**
   * @param configuration the configuration to set
   */
  public void setConfiguration(Map<String, String> configuration)
  {
    this.Configuration = configuration;
  }

  /**
   * @return the inputPorts
   */
  public List<CcdpPort> getInputPorts()
  {
    return InputPorts;
  }

  /**
   * @param inputPorts the inputPorts to set
   */
  public void setInputPorts(List<CcdpPort> inputPorts)
  {
    this.InputPorts = inputPorts;
  }

  /**
   * @return the outputPorts
   */
  public List<CcdpPort> getOutputPorts()
  {
    return OutputPorts;
  }

  /**
   * @param outputPorts the outputPorts to set
   */
  public void setOutputPorts(List<CcdpPort> outputPorts)
  {
    this.OutputPorts = outputPorts;
  }

  public CcdpTaskState getTaskState()
  {
    return this.state;
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
    CPU = cPU;
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
    MEM = mEM;
  }

  /**
   * @return the command
   */
  public List<String> getCommand()
  {
    return Command;
  }

  /**
   * @param command the command to set
   */
  public void setCommand(List<String> command)
  {
    Command = command;
  }

  /**
   * @return the taskId
   */
  public String getTaskId()
  {
    return TaskId;
  }

  /**
   * @param taskId the taskId to set
   */
  public void setTaskId(String taskId)
  {
    TaskId = taskId;
  }

  /**
   * @return the sessionId
   */
  public String getSessionId()
  {
    return sessionId;
  }

  /**
   * @param sessionId the sessionId to set
   */
  public void setSessionId(String sessionId)
  {
    this.sessionId = sessionId;
  }

  /**
   * @return the name
   */
  public String getName()
  {
    return Name;
  }

  /**
   * @param name the name to set
   */
  public void setName(String name)
  {
    Name = name;
  }

  /**
   * @return the description
   */
  public String getDescription()
  {
    return Description;
  }

  /**
   * @param description the description to set
   */
  public void setDescription(String description)
  {
    Description = description;
  }

  /**
   * @return the nodeType
   */
  public String getNodeType()
  {
    return NodeType;
  }

  /**
   * @param nodeType the nodeType to set
   */
  public void setNodeType(String nodeType)
  {
    NodeType = nodeType;
  }

  /**
   * @return the replyTo
   */
  public String getReplyTo()
  {
    return ReplyTo;
  }

  /**
   * @param replyTo the replyTo to set
   */
  public void setReplyTo(String replyTo)
  {
    ReplyTo = replyTo;
  }

  /**
   * @return the state
   */
  public CcdpTaskState getState()
  {
    return state;
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
   * @return the retries
   */
  public int getRetries()
  {
    return retries;
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
    return submitted;
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
    }
  }
}
