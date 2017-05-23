package com.axios.ccdp.connections.intfs;

import java.util.List;

import com.axios.ccdp.resources.CcdpVMResource;
import com.axios.ccdp.tasking.CcdpTaskRequest;
import com.axios.ccdp.tasking.CcdpThreadRequest;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Interface used to isolate decision making on when to launch new resources
 * from the mesos scheduler.  The actual implementation would the be object 
 * with the actual knowledge on when new resources need to be launched.
 * 
 * Tried to avoid any dependency to Mesos, but in order to generate the 
 * necessary tasking information about the 
 * 
 * @author Oscar E. Ganteaume
 *
 */
public interface CcdpTaskingControllerIntf
{
  /**
   * Sets all the parameters required for this object to determine resource
   * allocation and deallocation.
   * 
   * @param config the object containing all the configuration parameters
   */
  public  void configure( ObjectNode config );

  /**
   * Assigns all the tasks in the given list to the target VM based on 
   * resources availability and other conditions.
   * 
   * @param tasks a list of tasks to consider running in the intended VM
   * @param target the VM candidate to run the tasks
   * @param considering all available resources that could be potentially
   *        used to run this tasks
   * 
   * @return a list of tasks that could be assigned to the target resource
   * 
   */
  public  List<CcdpTaskRequest> assignTasks(List<CcdpTaskRequest> tasks, 
                                            CcdpVMResource target,
                                            List<CcdpVMResource> considering);
  
  /**
   * Determines whether or not additional resources are needed based on
   * the utilization level of the given resources.  If the resources combined
   * reaches the threshold then it returns true otherwise it returns false.
   * 
   * @param resources the list of resources to test for need of additional 
   *        resources
   * 
   * @return true if more resources need to be allocated or false otherwise
   */
  public  boolean needResourceAllocation(List<CcdpVMResource> resources );
  
  /**
   * Determines whether or not VM resources need to be terminated due to 
   * low utilization or need.  If one or more of the current RUNNING resources
   * falls below the threshold then is added to the list. 
   * 
   * @param resources the list of resources to test for need to deallocate
   * 
   * @return a list of resources that need to be terminated
   */
  public  List<CcdpVMResource> deallocateResource(List<CcdpVMResource> resources);

}
