package com.axios.ccdp.cloud.mock;

import java.util.List;

import org.apache.log4j.Logger;

import com.axios.ccdp.connections.intfs.CcdpVMControllerIntf;
import com.axios.ccdp.resources.CcdpVMResource;
import com.axios.ccdp.resources.CcdpVMResource.ResourceStatus;
import com.axios.ccdp.utils.CcdpImageInfo;
import com.fasterxml.jackson.databind.node.ObjectNode;

public class MockCcdpVMControllerImpl implements CcdpVMControllerIntf
{

  /**
   * Generates debug print statements based on the verbosity level.
   */
  private Logger logger = Logger
      .getLogger(MockCcdpVMControllerImpl.class.getName());

  public MockCcdpVMControllerImpl()
  {

  }

  @Override
  public void configure(ObjectNode config)
  {
    // TODO Auto-generated method stub

  }

  @Override
  public List<String> startInstances(CcdpImageInfo imgCfg)
  {
    // TODO Auto-generated method stub
    return null;
  }

  @Override
  public boolean stopInstances(List<String> instIDs)
  {
    // TODO Auto-generated method stub
    return false;
  }

  @Override
  public boolean terminateInstances(List<String> instIDs)
  {
    // TODO Auto-generated method stub
    return false;
  }

  @Override
  public List<CcdpVMResource> getAllInstanceStatus()
  {
    // TODO Auto-generated method stub
    return null;
  }

  @Override
  public ResourceStatus getInstanceState(String id)
  {
    // TODO Auto-generated method stub
    return null;
  }

  @Override
  public List<CcdpVMResource> getStatusFilteredByTags(ObjectNode filter)
  {
    // TODO Auto-generated method stub
    return null;
  }

  @Override
  public CcdpVMResource getStatusFilteredById(String uuid)
  {
    // TODO Auto-generated method stub
    return null;
  }
}
