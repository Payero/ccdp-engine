package com.axios.ccdp.resources;

import com.amazonaws.services.route53.model.InvalidArgumentException;
import com.axios.ccdp.tasking.CcdpTaskRequest;
import com.fasterxml.jackson.annotation.JsonGetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ObjectNode;

import javax.xml.bind.DatatypeConverter;
import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/*
 *Class used to represent serverless resources that can be used for data processing
 *
 * @author Scott Bennett, scott.bennett@caci.com
 *
 */
@JsonIgnoreProperties(
{ "free" })
public class CcdpServerlessResource extends CcdpResourceAbs
    implements Serializable
{
  /**
   * Randomly generated version id used during serialization
   */
  private static final long serialVersionUID = -705689459501099746L;

  /* Use to distinguish between VMs and serverless controllers during
   * querying
   */
  private final boolean isServerless = true;
     

  public CcdpServerlessResource()
  {
  }

  /*
   * Instantiates a new CcdpServerlessResource and sets the serverless type
   */
  public CcdpServerlessResource(String nodeType)
  {
    this.setNodeType(nodeType);
  }

  /*
   * Returns the serverless status of the resource
   *
   * @return returns whether the resource is serverless or not
   */
  
  public boolean getIsServerless(){ return this.isServerless; }
   /**
    * @return the the type of serverless resource
    */
  
   @JsonGetter("node-type")
   public String getNodeType() { return this.nodeType; }
    
   /*
    * 
    * Sets the instance id, if is null it throws an InvalidArgumentException
    *
    * @param type
    *          the instanceId to set
    *
    * @throws InvalidArgumentException
    *           an InvalidArgumentException exception is thrown if the
    *           instance id is null
    */
  
   @JsonSetter("node-type") 
   public void setNodeType(String type) 
   {
     if( type == null ) 
       throw new InvalidArgumentException("The node type cannot be null");
    
     this.nodeType = type; 
   }
    
   /*
    * Sets all the tags assigned to the resource
    *
    * @return all the tags assigned to the resource
    */
  
   public Map<String, String> getTags() { return tags; }
    
   /*
    * Gets all the tags assigned to the resource
    *
    * @param tags
    *          all the tags assigned to the resource
    */
  
  public void setTags(Map<String, String> tags) 
  { 
    if( tags != null )
      this.tags = tags; 
  }
    
   /**
    * Adds the given task to the list of tasks assigned to this VM Resource
    *
    * @param task
    *          the task to add
    */
  
  public void addTask(CcdpTaskRequest task) {
    
  this.last_assignment = System.currentTimeMillis(); this.tasks.add(task); }
    
   /**
    * Gets all the tasks assigned to this resource
    *
    * @return all the tasks assigned to this resource
    */
  
  @JsonGetter("tasks")
  public List<CcdpTaskRequest> getTasks() { return this.tasks; }
    
   /**
    * Gets the total number of tasks assigned to this resource
    *
    * @return the total number of tasks assigned to this resource
    */
  
  @JsonIgnore 
  public int getNumberTasks() { return this.tasks.size(); }
    
   /**
      * Removes the first task in the VM Resource list matching the given task's
      * ID. If the task is found it returns true otherwise it returns false
      *
      * @param task
      *          the task to remove from the list
      * @return true if the task is found or false otherwise
      *
      */
  
    public boolean removeTask( CcdpTaskRequest task ) { this.last_assignment =
    System.currentTimeMillis(); return this.tasks.remove(task); }
    
   /**
    * Removes the first task in the VM Resource list matching the given task's
    * ID. If the task is found it returns true otherwise it returns false
    *
    * @param tasks
    *          the tasks to remove from the list
    * @return true if the task is found or false otherwise
    *
    */
  
    public boolean removeTasks( List<CcdpTaskRequest> tasks ) 
    {
      this.last_assignment = System.currentTimeMillis(); 
      return this.tasks.removeAll(tasks); 
    }
    
   /**
    * Removes all the tasks stored in this resource
    *
    */
  
    public void removeAllTasks() { this.tasks = new ArrayList<CcdpTaskRequest>(); }
    
   /**
    * Updates the state of the task stored in this resource
    *
    * @param task
    *          the resource's task to be updated
    *
    */
  public void updateTaskState(CcdpTaskRequest task) 
  { 
    // Needs to compare host ID's in order to update it 
    for ( CcdpTaskRequest reTask : this.tasks ) 
    {
      if ( task.getTaskId().equals(reTask.getTaskId()) ) 
      {
        reTask.setState(task.getState()); 
      } 
    } 
  }
    
  /**
  * Gets the last time a task was added to this resource. If no task has
  * been assigned then the time represents when this object was created.
  *
  * @return the last time a task was added to this resource or the time this
  *         thread was created
  */

  @JsonGetter("last-assignment")
  public long getLastAssignmentTime() { return this.last_assignment; }
  
  /**
    * Sets the last time a task was added to this resource. If no task has
    * been assigned then the time represents when this object was created.
    *
    * @param assignmentTime
    *          the last time a task was added to this resource or the time
    *          this thread was created
    */
  
  @JsonSetter("last-assignment")
  public void setLastAssignmentTime (long assignmentTime) { this.last_assignment = assignmentTime; }
    
  /*  
   * Returns a Json representation of the serverless resource
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
   * @return a String representation of the object using a more human
   *         friendly formatting
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
    
  /*
   * Creates a Json representation of the object
   *
   * @return a JSON object representing this object
   */ 
  public ObjectNode toJSON() { return this.mapper.convertValue( this, ObjectNode.class ); }
    
  /**
   * Generates a String representing this object serialized.
   *
   * @return a String representation of this object serialized
   * @throws IOException
   *           an IOException is thrown if there is a problem during the
   *           serialization of the object
   */
  
  public String toSerializedString( ) throws IOException 
  { 
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    ObjectOutputStream oos = new ObjectOutputStream( baos );
    oos.writeObject(this );
    oos.close();
    
    return DatatypeConverter.printBase64Binary(baos.toByteArray());
  }
    
  /**
   * Reads a serialized string object and generates a CcdpServerlessResource
   * object
   *
   * @param s
   *          the string representing the serialized version of the object
   * @return a CcdpServerlessResource object that was serialized previously
   *
   * @throws IOException
   *           is thrown if the object cannot be de-serialized
   * @throws ClassNotFoundException
   *           is thrown if the stream cannot be read into an object
   */
  public static CcdpServerlessResource fromSerializedString( String s ) 
      throws IOException, ClassNotFoundException 
  { 
    //byte [] data = Base64.getDecoder().decode( s ); 
    byte[] data = DatatypeConverter.parseBase64Binary(s); 
    ObjectInputStream ois = new ObjectInputStream( 
        new org.fusesource.hawtbuf.ByteArrayInputStream( data ) ); 
    Object o = ois.readObject(); ois.close(); 
    
    return (CcdpServerlessResource) o; }       
}
