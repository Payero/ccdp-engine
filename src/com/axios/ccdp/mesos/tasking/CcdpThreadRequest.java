package com.axios.ccdp.mesos.tasking;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.apache.log4j.Logger;

import com.axios.ccdp.mesos.tasking.CcdpTaskRequest.CcdpTaskState;
import com.fasterxml.jackson.annotation.JsonGetter;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

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
   * Determines whether to run all the tasks in this thread in parallel or in 
   * sequence mode.
   */
  public enum TasksRunningMode { PARALLEL, SEQUENCE }
  /**
   * Generates debug print statements based on the verbosity level.
   */
  private Logger logger = Logger.getLogger(CcdpThreadRequest.class);
  /**
   * Unique identifier for this thread
   */
  private String threadId;
  /**
   * The session this task belongs to
   */
  private String sessionId = null;
  /**
   * A human readable name to identify this thread
   */
  private String name;
  /**
   * A description to store any help users want to provide
   */
  private String description;
  /**
   * A way to communicate back to the sender events about tasking status change
   */
  private String replyTo = "";
  /**
   * A list of tasks to run in order to execute this processing job
   */
  private List<CcdpTaskRequest> tasks = new ArrayList<CcdpTaskRequest>();
  /**
   * Indicates how to run all the tasks, one at the time or all of them
   */
  private TasksRunningMode runningMode = TasksRunningMode.PARALLEL;
  /**
   * Indicates whether or not all the tasks have been submitted for this thread
   */
  private boolean tasksSubmitted = false;
  
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
    this.threadId = threadId;
  }
  
  /**
   * Gets the next task to process or null if all the tasks have been processed
   * 
   * @return either the next CcdpTaskRequest object to process or null if none
   */
  public CcdpTaskRequest getNextTask()
  {
    this.logger.debug("Getting Next task for thread: " + this.threadId);
    Iterator<CcdpTaskRequest> tasks = this.tasks.iterator();
    while( tasks.hasNext() )
    {
      CcdpTaskRequest task = tasks.next();
      if( !task.isSubmitted() )
        return task;
    }
    
    this.logger.debug("All the Tasks have been submitted");
    this.setTasksSubmitted(true);
    
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
    Iterator<CcdpTaskRequest> tasks = this.tasks.iterator();
    while( tasks.hasNext() )
    {
      CcdpTaskRequest task = tasks.next();
      CcdpTaskState state = task.getState(); 
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
   * @return the tasks
   */
  public List<CcdpTaskRequest> getTasks()
  {
    return this.tasks;
  }

  /**
   * @param tasks the tasks to set
   */
  public void setTasks(List<CcdpTaskRequest> tasks)
  {
    this.tasks = tasks;
  }

  /**
   * @return the threadId
   */
  @JsonGetter("thread-id")
  public String getThreadId()
  {
    return this.threadId;
  }

  /**
   * @param threadId the threadId to set
   */
  @JsonSetter("thread-id")
  public void setThreadId(String threadId)
  {
    this.threadId = threadId;
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
   * @return the tasks running mode
   */
  public TasksRunningMode getTasksRunningMode()
  {
    return this.runningMode;
  }

  /**
   * @param mode the tasks running mode
   */
  public void setTasksRunningMode( TasksRunningMode mode )
  {
    this.runningMode = mode;
  }

  /**
   * @return the tasksSubmitted
   */
  public boolean isTasksSubmitted()
  {
    return tasksSubmitted;
  }

  /**
   * @param tasksSubmitted the tasksSubmitted to set
   */
  public void setTasksSubmitted(boolean tasksSubmitted)
  {
    this.tasksSubmitted = tasksSubmitted;
  }


  /**
   * Removes a Task from the Thread Request.  If the task is found it returns
   * true otherwise it returns false
   * 
   * @param tasks the tasks to remove from the request
   * 
   * @return true if the task is found and was removed successfully or false 
   *         otherwise
   */
  public boolean removeAllTasks( List<CcdpTaskRequest> tasks )
  {
    return this.tasks.removeAll(tasks);
  }
  
  
  /**
   * Removes a Task from the Thread Request.  If the task is found it returns
   * true otherwise it returns false
   * 
   * @param task the task to remove from the request
   * 
   * @return true if the task is found and was removed successfully or false 
   *         otherwise
   */
  public boolean removeTask( CcdpTaskRequest task )
  {
    return this.tasks.remove(task);
  }
  
  /**
   * Returns true if all the tasks have been removed from the list.  The task
   * is removed once it either finishes running or fails to run.
   * 
   * @return  true if all the tasks have been removed from the list or false 
   *          otherwise
   */
  public boolean isDone()
  {
    return this.tasks.isEmpty();
  }
  
  /**
   * Gets a string representation of this object using the form 
   *  'Field': 'Value'
   * 
   * @return a string representation of the object
   */
  public String toString()
  {
    ObjectMapper mapper = new ObjectMapper();
    ObjectNode thread = mapper.createObjectNode();
    ArrayNode tasks = mapper.createArrayNode();
    
    thread.put("thread-id",           this.threadId);
    thread.put("name",                this.name);
    thread.put("description",         this.description);
    thread.put("reply-to",            this.replyTo);
    thread.put("tasks-running-mode",  this.runningMode.toString());
    thread.put("tasks-submitted",     this.tasksSubmitted);
    for( CcdpTaskRequest task : this.tasks )
      tasks.add(task.toObjectNode());
    
    thread.set("tasks",               tasks);
    
    String str = thread.toString();
    try
    {
      str = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(thread);
    }
    catch( JsonProcessingException e )
    {
      this.logger.error("Message: " + e.getMessage(), e);
    }
    return str;
  }
  
  /**
   * Returns the total number of Tasks that have not been submitted 
   * 
   * @return the total number of Tasks that have not been submitted
   */
  public int getPendingTasks()
  {
    int total = 0;
    
    for( CcdpTaskRequest task : this.tasks )
    {
      if( !task.isSubmitted() )
        total++;
    }
    
    return total;
  }
}
