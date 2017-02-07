package com.axios.ccdp.mesos.tasking;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.apache.log4j.Logger;

import com.axios.ccdp.mesos.tasking.CcdpTaskRequest.CcdpTaskState;

/**
 * Class used to store a list of associated tasks to be ran sequentially.  It
 * helps coordinating the launching of Tasks and to keep track of its execution
 * 
 * @author Oscar E. Ganteaume
 *
 */
public class CcdpThreadRequest 
{
  /**
   * Generates debug print statements based on the verbosity level.
   */
  private Logger logger = Logger.getLogger(CcdpThreadRequest.class);
  /**
   * Unique identifier for this thread
   */
  private String ThreadId;
  /**
   * The session this task belongs to
   */
  private String sessionId = null;
  /**
   * A human readable name to identify this thread
   */
  private String Name;
  /**
   * A description to store any help users want to provide
   */
  private String Description;
  /**
   * A way to communicate back to the sender events about tasking status change
   */
  private String ReplyTo = "";
  /**
   * A list of tasks to run in order to execute this processing job
   */
  private List<CcdpTaskRequest> Tasks = new ArrayList<CcdpTaskRequest>();
  
  /**
   * Instantiates a new object using a self-generated UUID as the ThreadID
   */
  public CcdpThreadRequest()
  {
    this( UUID.randomUUID().toString() );
  }

  /**
   * Instantiates a new object using the provided threadId as the unique 
   * identifier
   * 
   * @param threadId a unique identifier for this thread
   */
  public CcdpThreadRequest(String threadId)
  {
    this.ThreadId = threadId;
  }
  
  
  /**
   * Gets the next task to process or null if all the tasks have been processed
   * 
   * @return either the next CcdpTaskRequest object to process or null if none
   */
  public CcdpTaskRequest getNextTask()
  {
    this.logger.debug("Getting Next task for thread: " + this.ThreadId);
    Iterator<CcdpTaskRequest> tasks = this.Tasks.iterator();
    while( tasks.hasNext() )
    {
      CcdpTaskRequest task = tasks.next();
      if( !task.isSubmitted() )
        return task;
    }
    
    return null;
  }

  /**
   * Determines whether or not this processing thread is complete or not.  This
   * is determined checking that each of the tasks have a state of either 
   * SUCCESSFUL or FAILED.  If at least one of the tasks does not have its state
   * set to either one of the values mentioned above then it is considered 
   * pending
   * 
   * @return true if all of the tasks have a state of either SUCCESSFUL or 
   *         FAILED or false otherwise
   */
  public boolean threadRequestCompleted()
  {
    boolean done = true;
    Iterator<CcdpTaskRequest> tasks = this.Tasks.iterator();
    while( tasks.hasNext() )
    {
      CcdpTaskRequest task = tasks.next();
      CcdpTaskState state = task.getTaskState(); 
      if( state != CcdpTaskState.SUCCESSFUL ||  state != CcdpTaskState.FAILED)
      {
        done = false;
        break;
      }
    }
    
    return done;
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
   * Gets a string representation of this object using the form 
   *  'Field': 'Value'
   * 
   * @return a string representation of the object
   */
  public String toString()
  {
    StringBuffer buf = new StringBuffer();
    buf.append("Thread Id: "); buf.append(this.getThreadId()); buf.append("\n");
    buf.append("Name: "); buf.append(this.getName()); buf.append("\n");
    buf.append("Description: "); buf.append(this.getDescription()); buf.append("\n");
    buf.append("Reply To: "); buf.append(this.getReplyTo()); buf.append("\n");

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
