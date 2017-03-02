package com.axios.ccdp.mesos.connections.intfs;

import java.util.List;

import org.apache.mesos.Protos.ExecutorInfo;

import com.axios.ccdp.mesos.resources.CcdpVMResource;
import com.axios.ccdp.mesos.tasking.CcdpTaskRequest;
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
public interface CcdpTaskingControllerIntf<T>
{

  public  void configure( ObjectNode config );
  
  public  List<T> assignTasks(List<CcdpTaskRequest> tasks, 
                              List<CcdpVMResource> resources);
  
  public  boolean needResourceAllocation(List<CcdpVMResource> resources);
  
  public  List<CcdpVMResource> deallocateResource(List<CcdpVMResource> resources);
  
  public void setExecutor(ExecutorInfo exec);
}
