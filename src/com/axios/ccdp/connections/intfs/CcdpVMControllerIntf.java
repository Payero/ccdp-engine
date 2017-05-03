package com.axios.ccdp.connections.intfs;

import java.util.Collection;
import java.util.List;
import java.util.Map;

import com.axios.ccdp.resources.CcdpVMResource;
import com.fasterxml.jackson.databind.node.ObjectNode;


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
  public void configure( ObjectNode config );
  
  /**
   * Starts one or more VM instances using the defined Image ID as given by the
   * imageId argument.  The number of instances are determined by the min and 
   * max arguments.  If the tags is not null then they are set and the new 
   * Virtual Machine will contain them.
   * 
   * @param imageId the image to use to create new Virtual Machines
   * @param min the minimum number of Virtual Machines to create
   * @param max the maximum number of Virtual Machines to create
   * @param tags optional map containing key-value pairs to set
   * 
   * @return a list of unique Virtual Machine identifiers
   */
  public List<String> startInstances(String imageId, int min, int max, 
                                   Map<String, String> tags);
  
  /**
   * Starts one or more VM instances using the defined Image ID as given by the
   * imageId argument.  The number of instances are determined by the min and 
   * max arguments.  If the tags is not null then they are set and the new 
   * Virtual Machine will contain them.
   * 
   * @param imageId the image to use to create new Virtual Machines
   * @param min the minimum number of Virtual Machines to create
   * @param max the maximum number of Virtual Machines to create
   * @param session_id the unique session id assigned to this VM
   * @param tags optional map containing key-value pairs to set
   * 
   * @return a list of unique Virtual Machine identifiers
   */
  public List<String> startInstances(String imageId, int min, int max, 
                                   String session_id, Map<String, String> tags);
  
  /**
   * Starts one or more VM instances using the defined Image ID as given by the
   * imageId argument.  The number of instances are determined by the min and 
   * max arguments.  If the tags is not null then they are set and the new 
   * Virtual Machine will contain them.
   * 
   * The resources are launched the image id, user data, and tags specified in
   * the configuration file
   * 
   * @param min the minimum number of Virtual Machines to create
   * @param max the maximum number of Virtual Machines to create
   * 
   * @return a list of unique Virtual Machine identifiers
   */
  public List<String> startInstances(int min, int max);

  /**
   * Starts one or more VM instances using the defined Image ID as given by the
   * imageId argument.  The number of instances are determined by the min and 
   * max arguments.  If the tags is not null then they are set and the new 
   * Virtual Machine will contain them.
   * 
   * The resources are launched the image id, user data, and tags specified in
   * the configuration file
   * 
   * @param min the minimum number of Virtual Machines to create
   * @param max the maximum number of Virtual Machines to create
   * @param session_id the unique session id assigned to this VM
   * 
   * @return a list of unique Virtual Machine identifiers
   */
  public List<String> startInstances(int min, int max, String session_id);
  
  
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
   * Gets all the instances status that are currently 'available' on different
   * states
   * 
   * @return a list with all the instances status that are currently 
   *         'available' on different state
   */
  public List<CcdpVMResource> getAllInstanceStatus();
  
  /**
   * Returns information about all instances matching the set of filters given
   * by the filter JSON object.  In other words, if the instance contains a tag
   * matching ALL the names and values of the given in the filter then is 
   * flagged as a valid result.
   * 
   * 
   * @param filter a JSON object containing the criteria to filter the Virtual
   *        Machines
   *        
   * @return a List containing all the Virtual Machines matching the criteria
   */
  public List<CcdpVMResource>  getStatusFilteredByTags( ObjectNode filter );
  
  /**
   * Returns information about the instance matching the unique id given as 
   * argument.  If the object is not found it returns null 
   * 
   * 
   * @param uuid the unique identifier used to select the appropriate resource
   *        
   * @return the resource whose unique identifier matches the given argument
   */
  public CcdpVMResource  getStatusFilteredById( String uuid );
  
}
