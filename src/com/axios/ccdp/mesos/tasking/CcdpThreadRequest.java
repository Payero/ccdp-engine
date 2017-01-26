package com.axios.ccdp.mesos.tasking;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * {"thread-id": "thread-1",
    "name": "PI Estmator",
    "starting-task": "cycles_selector",
    "short-description": "Estimates the value of PI",
    "tasks":{}
   }
         
 * @author Oscar E. Ganteaume
 *
 */
public class CcdpThreadRequest 
{
  private String ThreadId;
  private String Name;
  private String Description;
  
  private String StartingTask;
  private List<CcdpTaskRequest> Tasks = new ArrayList<CcdpTaskRequest>();
  

  /**
   * @return the startingTask
   */
  public String getStartingTask()
  {
    return StartingTask;
  }

  /**
   * @param startingTask the startingTask to set
   */
  public void setStartingTask(String startingTask)
  {
    this.StartingTask = startingTask;
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
    this.Description = description;
  }

  /**
   * @return the tasks
   */
  public List<CcdpTaskRequest> getTasks()
  {
    return Tasks;
  }

  /**
   * @param tasks the tasks to set
   */
  public void setTasks(List<CcdpTaskRequest> tasks)
  {
    this.Tasks = tasks;
  }

  /**
   * @return the threadId
   */
  public String getThreadId()
  {
    return ThreadId;
  }

  /**
   * @param threadId the threadId to set
   */
  public void setThreadId(String threadId)
  {
    ThreadId = threadId;
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
  
  public String toString()
  {
    StringBuffer buf = new StringBuffer();
    buf.append("Thread Id: "); buf.append(this.getThreadId()); buf.append("\n");
    buf.append("Name: "); buf.append(this.getName()); buf.append("\n");
    buf.append("Description: "); buf.append(this.getDescription()); buf.append("\n");
    buf.append("Start Task: "); buf.append(this.getStartingTask()); buf.append("\n");

    List<CcdpTaskRequest> tasks = this.getTasks();
    
    buf.append("Tasks:\n");
    Iterator<CcdpTaskRequest> items = tasks.iterator();
    while( items.hasNext() )
    {
      CcdpTaskRequest task = items.next();
      
      buf.append("\tTask Id: "); buf.append(task.getTaskId()); buf.append("\n");
      buf.append("\tTask Name: "); buf.append(task.getName()); buf.append("\n");
      buf.append("\tClass Name: "); buf.append(task.getClassName()); buf.append("\n");
      buf.append("\tReply To: "); buf.append(task.getReplyTo()); buf.append("\n");
      buf.append("\tNodeType: "); buf.append(task.getNodeType()); buf.append("\n");
      
      buf.append("\tCPU: "); buf.append(task.getCPU()); buf.append("\n");
      buf.append("\tMEM: "); buf.append(task.getMEM()); buf.append("\n");
      buf.append("\tCommand: "); buf.append("\n");
      Iterator<String> args = task.getCommand().iterator();
      buf.append("\t\t");
      while( args.hasNext() )
      {
        String arg = args.next();
        buf.append(arg);
        buf.append(" ");
      }
      buf.append("\n");
      
      Map<String, String> config = task.getConfiguration();
      Iterator<String> keys = config.keySet().iterator();
      buf.append("\tTask Configuration:\n");
      while( keys.hasNext() )
      {
        String key = keys.next();
        buf.append("\t\tConfig["); buf.append(key); buf.append("] = "); 
        buf.append(config.get(key)); buf.append("\n");
      }

      Iterator<CcdpPort> inPorts = task.getInputPorts().iterator();
      buf.append("\tTask Input Ports:\n");
      while( inPorts.hasNext() )
      {
        CcdpPort port = inPorts.next();
        Iterator<String> from = port.getFromPort().iterator();
        Iterator<String> to = port.getToPort().iterator();
        buf.append("\t\tPortId: "); buf.append(port.getPortId()); buf.append("\n");
        while( from.hasNext() )
        {
          buf.append("\t\t\tFrom: "); buf.append(from.next()); buf.append("\n");
        }
        while( to.hasNext() )
        {
          buf.append("\t\t\tTo: "); buf.append(to.next()); buf.append("\n");
        }
      }


      Iterator<CcdpPort> outPorts = task.getOutputPorts().iterator();
      buf.append("\tTask Output Ports:\n");
      while( outPorts.hasNext() )
      {
        CcdpPort port = outPorts.next();
        Iterator<String> from = port.getFromPort().iterator();
        Iterator<String> to = port.getToPort().iterator();
        buf.append("\t\tPortId: "); buf.append(port.getPortId()); buf.append("\n");
        while( from.hasNext() )
        {
          buf.append("\t\t\tFrom: "); buf.append(from.next()); buf.append("\n");
        }
        while( to.hasNext() )
        {
          buf.append("\t\t\tTo: "); buf.append(to.next()); buf.append("\n");
        }
      }
    }
    
    return buf.toString();
  }
  
}
