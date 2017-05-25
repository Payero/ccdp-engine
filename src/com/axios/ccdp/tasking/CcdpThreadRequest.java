package com.axios.ccdp.tasking;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

import com.axios.ccdp.tasking.CcdpTaskRequest.CcdpTaskState;
import com.axios.ccdp.utils.CcdpUtils.CcdpNodeType;
import com.fasterxml.jackson.annotation.JsonGetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Class used to store a list of associated tasks to be ran sequentially.  It
 * helps coordinating the launching of Tasks and to keep track of its 
 * execution.  The following is an example of the JSON structure:
 * 
 * {
 *   "thread-id" : "fcd6dc6d-3eaa-4caa-9e8a-632a85403552",
 *   "session-id" : null,
 *   "name" : null,
 *   "description" : null,
 *   "reply-to" : "",
 *   "tasks-running-mode" : "PARALLEL",
 *   "tasks-submitted" : false,
 *   "use-single-node" : false,
 *   "tasks" : [ {
 *     "task-id" : "csv_reader",
 *     "name" : "Csv File Reader",
 *     "description" : null,
 *     "state" : "PENDING",
 *     "class-name" : "tasks.csv_demo.CsvReader",
 *     "node-type" : "ec2",
 *     "reply-to" : "The Sender",
 *     "agent-id" : null,
 *     "session-id" : null,
 *     "retries" : 3,
 *     "submitted" : false,
 *     "launched-time" : 0,
 *     "cpu" : 10.0,
 *     "mem" : 128.0,
 *     "command" : "[python, /opt/modules/CsvReader.python]",
 *     "configuration" : "{filename=${CCDP_HOME}/data/csv_test_file.csv}",
 *     "input-ports" : [ {
 *       "port-id" : "from-exterior",
 *       "input-ports" : [ "source-1", "source-2" ],
 *       "output-ports" : [ "dest-1", "dest-2" ]
 *     } ],
 *     "output-ports" : [ ]
 *   } ]
 * }
 * 
 * @author Oscar E. Ganteaume
 *
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class CcdpThreadRequest implements Serializable
{
  /**
   * Randomly generated version id used by the serialization
   */
  private static final long serialVersionUID = 359027303587612779L;
  /**
   * Determines whether to run all the tasks in this thread in parallel or in 
   * sequence mode.
   */
  public enum TasksRunningMode { PARALLEL, SEQUENCE, SEQUENTIAL }
  /**
   * Generates all the JSON objects for this thread
   */
  private ObjectMapper mapper = new ObjectMapper();
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
   * Indicates the node type where this task needs to run such as EMR, EC2, etc 
   **/
  private CcdpNodeType nodeType = CcdpNodeType.DEFAULT;
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
   * Determines whether or not all the tasks need to run on a single processing 
   * node
   */
  private boolean runSingleNode = false;
  
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
    Iterator<CcdpTaskRequest> tasks = this.tasks.iterator();
    while( tasks.hasNext() )
    {
      CcdpTaskRequest task = tasks.next();
      if( !task.isSubmitted() )
        return task;
    }
    
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
   * @return the tasks running mode
   */
  @JsonGetter("tasks-running-mode")
  public TasksRunningMode getTasksRunningMode()
  {
    return this.runningMode;
  }

  /**
   * @param mode the tasks running mode
   */
  @JsonSetter("tasks-running-mode")
  public void setTasksRunningMode( String mode )
  {
    this.setTasksRunningMode(TasksRunningMode.valueOf(mode));
  }

  /**
   * @param mode the tasks running mode
   */
  public void setTasksRunningMode( TasksRunningMode mode )
  {
    this.runningMode = mode;
  }
  
  /**
   * @return whether or not need to run in a single processing node
   */
  @JsonGetter("use-single-node")
  public boolean runSingleNode()
  {
    return this.runSingleNode;
  }

  /**
   * @param single_node whether or not need to run in a single processing node
   */
  @JsonSetter("use-single-node")
  public void runSingleNode(boolean single_node)
  {
    this.runSingleNode = single_node;
  }
  
  /**
   * @return the tasksSubmitted
   */
  @JsonGetter("tasks-submitted")
  public boolean isTasksSubmitted()
  {
    return tasksSubmitted;
  }

  /**
   * @param tasksSubmitted the tasksSubmitted to set
   */
  @JsonSetter("tasks-submitted")
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
  @JsonIgnore
  public boolean isDone()
  {
    return this.tasks.isEmpty();
  }
  
  /**
   * Gets a string representation of this object using JSON notation.  The 
   * following is an example of the JSON structure:
   * 
   * {
   *   "thread-id" : "fcd6dc6d-3eaa-4caa-9e8a-632a85403552",
   *   "session-id" : null,
   *   "name" : null,
   *   "description" : null,
   *   "reply-to" : "",
   *   "tasks-running-mode" : "PARALLEL",
   *   "tasks-submitted" : false,
   *   "use-single-node" : false,
   *   "tasks" : [ {
   *     "task-id" : "csv_reader",
   *     "name" : "Csv File Reader",
   *     "description" : null,
   *     "state" : "PENDING",
   *     "class-name" : "tasks.csv_demo.CsvReader",
   *     "node-type" : "ec2",
   *     "reply-to" : "The Sender",
   *     "agent-id" : null,
   *     "session-id" : null,
   *     "retries" : 3,
   *     "submitted" : false,
   *     "launched-time" : 0,
   *     "cpu" : 10.0,
   *     "mem" : 128.0,
   *     "command" : "[python, /opt/modules/CsvReader.python]",
   *     "configuration" : "{filename=${CCDP_HOME}/data/csv_test_file.csv}",
   *     "input-ports" : [ {
   *       "port-id" : "from-exterior",
   *       "input-ports" : [ "source-1", "source-2" ],
   *       "output-ports" : [ "dest-1", "dest-2" ]
   *     } ],
   *     "output-ports" : [ ]
   *   } ]
   * }
   * 
   * @return a string representation of the object
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
   * Gets a JSON representation of this thread.  The following is an example 
   * of the JSON structure:
   * 
   * {
   *   "thread-id" : "fcd6dc6d-3eaa-4caa-9e8a-632a85403552",
   *   "session-id" : null,
   *   "name" : null,
   *   "description" : null,
   *   "reply-to" : "",
   *   "tasks-running-mode" : "PARALLEL",
   *   "tasks-submitted" : false,
   *   "use-single-node" : false,
   *   "tasks" : [ {
   *     "task-id" : "csv_reader",
   *     "name" : "Csv File Reader",
   *     "description" : null,
   *     "state" : "PENDING",
   *     "class-name" : "tasks.csv_demo.CsvReader",
   *     "node-type" : "ec2",
   *     "reply-to" : "The Sender",
   *     "agent-id" : null,
   *     "session-id" : null,
   *     "retries" : 3,
   *     "submitted" : false,
   *     "launched-time" : 0,
   *     "cpu" : 10.0,
   *     "mem" : 128.0,
   *     "command" : "[python, /opt/modules/CsvReader.python]",
   *     "configuration" : "{filename=${CCDP_HOME}/data/csv_test_file.csv}",
   *     "input-ports" : [ {
   *       "port-id" : "from-exterior",
   *       "input-ports" : [ "source-1", "source-2" ],
   *       "output-ports" : [ "dest-1", "dest-2" ]
   *     } ],
   *     "output-ports" : [ ]
   *   } ]
   * }
   * 
   * @return a JSON object representing this thread
   */
  public ObjectNode toJSON()
  {
    return this.mapper.convertValue( this, ObjectNode.class );
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
