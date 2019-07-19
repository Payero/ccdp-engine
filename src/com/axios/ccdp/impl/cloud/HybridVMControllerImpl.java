// Scott Bennett, scott.bennett@caci.com
// An adaptation to Oscar's VM controllers, allowing both styles of VMs
// to be spawned from a VM controller perspective

package com.axios.ccdp.impl.cloud;

import java.util.ArrayList;
import java.util.List;

import org.apache.log4j.Logger;

import com.axios.ccdp.impl.cloud.aws.AWSCcdpVMControllerImpl;
import com.axios.ccdp.impl.cloud.docker.DockerVMControllerImpl;
import com.axios.ccdp.intfs.CcdpVMControllerIntf;
import com.axios.ccdp.resources.CcdpImageInfo;
import com.axios.ccdp.resources.CcdpVMResource;
import com.axios.ccdp.resources.CcdpVMResource.ResourceStatus;
import com.fasterxml.jackson.databind.JsonNode;

public class HybridVMControllerImpl implements CcdpVMControllerIntf
{

  /**
   * Generates debug print statements based on the verbosity level.
   */
  private Logger logger = Logger.getLogger(HybridVMControllerImpl.class.getName());

  // A Docker and an AWS Controller as member
  
  private CcdpVMControllerIntf DockerController = null, AWSController = null;
  
  public HybridVMControllerImpl()
  {
    // Make new instances of a Docker Controller and AWS Controller
    logger.debug("Creating a new Hybrid VM Controller");
    DockerController = new DockerVMControllerImpl();
    AWSController = new AWSCcdpVMControllerImpl();
  }

  @Override
  public void configure(JsonNode config)
  {
    logger.debug("Configuring ResourceController using: " + config);

    DockerController.configure(config);
    AWSController.configure(config);
  }

  /*
   * Determine type to be spawned, use that controller
   * 
   * @params imgCfg A VM image configuration to be used
   * 
   * @return A list of the ID of spawned VMs
   */
  @Override
  public List<String> startInstances(CcdpImageInfo imgCfg)
  {
     List<String> launched = new ArrayList<>();
     
     if ("DOCKER".equals(imgCfg.getNodeType()))
       launched = DockerController.startInstances(imgCfg);
     else if ( "EC2".equals(imgCfg.getNodeType()) || "DEFAULT".equals(imgCfg.getNodeType()) )
       launched = AWSController.startInstances(imgCfg);
     else
       logger.error("Bad node type, nothing launched.");
     
    return launched;
  }

  /*
   * Determine each type in the list, then call appropriate controller method
   * 
   * @params instIDs A list of VM IDs to be stopped
   * 
   * @return true if VMs were stopped, false otherwise
   */
  @Override
  public boolean stopInstances(List<String> instIDs)
  {
    boolean stopped = false;
    List<String> singleID = new ArrayList<>();
    
    for ( String id : instIDs )
    {
      singleID.add(id);
      if ( id.charAt(0) == 'i') 
      {
        stopped = AWSController.stopInstances(singleID);
      }
      else
      {
        stopped = DockerController.stopInstances(singleID);
      }
      singleID.clear();
    }
    return stopped;
  }

  /*
   * Determine the type of each ID, then terminate them with their respective controllers
   *
   * @params instIDs A list of ID of VMs to be terminated
   * 
   * @return True if all the VMs were deleted, false otherwise
   */
  @Override
  public boolean terminateInstances(List<String> instIDs)
  {
    boolean terminated = false;
    List<String> singleID = new ArrayList<>();
    
    for ( String id : instIDs )
    {
      singleID.add(id);
      if ( id.charAt(0) == 'i') 
      {
        terminated = AWSController.terminateInstances(singleID);
      }
      else
      {
        terminated = DockerController.terminateInstances(singleID);
      }
      singleID.clear();
    }
    return terminated;
  }

  /*
   * Get all instance statuses from both controllers
   * 
   * @return A list of all VMs
   */
  @Override
  public List<CcdpVMResource> getAllInstanceStatus()
  {
    List<CcdpVMResource> resources = new ArrayList<>();
    
    resources.addAll(AWSController.getAllInstanceStatus());
    resources.addAll(DockerController.getAllInstanceStatus());
    return resources;
  }

  /*
   * Get the instance state of the specified id
   * 
   * @param id The ID of the queried VM
   * 
   * @return The state of the queried VM
   */
  @Override
  public ResourceStatus getInstanceState(String id)
  {
    if ( id.charAt(0) == 'i')
      return AWSController.getInstanceState(id);
    else
      return DockerController.getInstanceState(id);
  }

  /*
   * Get filtered status using the provided filter
   * 
   * @param filter The filter to use when searching
   * 
   * @return A list of VMs matching the filter
   */
  @Override
  public List<CcdpVMResource> getStatusFilteredByTags(JsonNode filter)
  {
    List<CcdpVMResource> resources = new ArrayList<>();
    resources.addAll(AWSController.getStatusFilteredByTags(filter));
    resources.addAll(DockerController.getStatusFilteredByTags(filter));
    return resources;
  }

  /*
   * THIS LOOKS WRONG, SHOULD BE A LIST?
   * NOT IMPLEMENTED FOR DOCKER ANYWAY
   * 
   * Get all VMs who satisfy the ID filter
   * 
   * @param uuid A unique universal identifier
   * 
   * @return A list of the VMs satisfying the filter
   */
  @Override
  public CcdpVMResource getStatusFilteredById(String uuid)
  {
    List<CcdpVMResource> resources = new ArrayList<>();
    resources.add(AWSController.getStatusFilteredById(uuid));
    // resources.addAll(Docker)
    return null;
  }
}
