package com.axios.ccdp.mesos.connections.intfs;

import java.util.List;
import java.util.Map;

import com.google.gson.JsonObject;

public interface CcdpVMControllerIntf
{
  /**
   * Configures the running environment and/or connections required to perform
   * the operations.  The JSON Object contains all the different fields 
   * necessary to operate.  These fields might change on each actual 
   * implementation
   * 
   * @param config a JSON Object containing all the necessary fields required 
   *        to operate
   */
  public void configure( JsonObject config );
  
  /**
   * Starts one or more VM instances using the defined Image ID as given by the
   * imageId argument.  The number of instances are determined by the min and 
   * max arguments.  If the tags is not null then they are set and the new 
   * Virtual Machine will contain them.
   * 
   * If an user_data argument is provided then is executed as bash commands.  
   * The String needs to reflect a bash script such as new lines needs to be
   * added between commands.
   * 
   * @param imageId the image to use to create new Virtual Machines
   * @param min the minimum number of Virtual Machines to create
   * @param max the maximum number of Virtual Machines to create
   * @param tags optional map containing key-value pairs to set
   * @param user_data a string with the bash commands to run
   * 
   * @return a list of unique Virtual Machine identifiers
   */
  public List<String> startInstances(String imageId, int min, int max, 
                                   Map<String, String> tags, String user_data);
  
  /**
   * Stops each one of the Virtual Machines whose unique identifier matches the
   * ones given in the argument
   * 
   * @param instIDs a list of unique identifiers used to determine which Virtual
   *        Machine needs to be stopped
   *        
   * @return true if the request was submitted successfully or false otherwise
   */
  public boolean stopInstances(List<String> instIDs);
  
  /**
   * Terminates each one of the Virtual Machines whose unique identifier matches
   * the ones given in the argument
   * 
   * @param instIDs a list of unique identifiers used to determine which Virtual
   *        Machine needs to be terminated
   *        
   * @return true if the request was submitted successfully or false otherwise
   */
  public boolean terminateInstances(List<String> instIDs);
  
  /**
   * Gets all the instances status that are currently assigned to the user
   * 
   * The result is a JSON Object whose key is the Virtual Machine identifier and
   * the value is detailed information of the VM.
   * 
   * @return an object containing details of each of the Virtual Machines 
   *         assigned to the user
   */
  public JsonObject getAllInstanceStatus();
  
  /**
   * Returns information about all instances matching the set of filters given
   * by the filter JSON object.  In other words, if the instance contains a tag
   * matching ALL the names and values of the given in the filter then is 
   * flagged as a valid result.
   * 
   * The result is a JSON Object whose key is the Virtual Machine identifier and
   * the value is detailed information of the VM.
   * 
   * @param filter a JSON object containing the criteria to filter the Virtual
   *        Machines
   *        
   * @return A JSON Object containing all the Virtual Machines matching the 
   *         criteria
   */
  public JsonObject getStatusFilteredByTags( JsonObject filter );
  
}
