package com.axios.ccdp.connections.intfs;

import java.util.List;

import com.axios.ccdp.resources.CcdpVMResource;
import com.fasterxml.jackson.databind.node.ObjectNode;

public interface CcdpDatabaseIntf
{
  public boolean storeVMInformation( CcdpVMResource vm );
  public void configure( ObjectNode config );
  public boolean connect();
  public boolean deleteVMInformation( String uniqueId );
  public CcdpVMResource getVMInformation( String uniqueId );
  public List<CcdpVMResource> getAllVMInformation();
  public List<CcdpVMResource> getAllVMInformationBySessionId( String sid );
  public boolean disconnect();
}
