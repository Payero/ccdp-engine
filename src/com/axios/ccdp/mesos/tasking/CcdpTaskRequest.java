package com.axios.ccdp.mesos.tasking;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
  private String TaskId;
  private String Name;
  private String Description;
  
  private String ClassName;
  private String NodeType;
  private String ReplyTo = "";
  
  private boolean ShareNode = true;
  private double CPU = 0.000001;
  private double MEM = 0.000001;
  private List<String> Command = new ArrayList<String>();
  private Map<String, String> Configuration = new HashMap<String, String>();
  private List<CcdpPort> InputPorts = new ArrayList<CcdpPort>(); 
  private List<CcdpPort> OutputPorts = new ArrayList<CcdpPort>();
  

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

  /**
   * @return the shareNode
   */
  public boolean isShareNode()
  {
    return ShareNode;
  }

  /**
   * @param shareNode the shareNode to set
   */
  public void setShareNode(boolean shareNode)
  {
    ShareNode = shareNode;
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
}
