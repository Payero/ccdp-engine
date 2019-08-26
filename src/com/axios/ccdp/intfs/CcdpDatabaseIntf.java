package com.axios.ccdp.intfs;

import java.util.List;

import com.axios.ccdp.resources.CcdpResourceAbs;
import com.axios.ccdp.resources.CcdpServerlessResource;
import com.axios.ccdp.resources.CcdpVMResource;
import com.fasterxml.jackson.databind.JsonNode;

public interface CcdpDatabaseIntf
{
  /**
   * Gets all the settings required to connect from the given parameter
   * 
   * @param config the JSON representation of the DB connection arguments
   */
  public void configure( JsonNode config );
  
  /**
   * Connects to the database using the settings passed to the configure() 
   * method.  Returns true if was able to connect or false otherwise
   * 
   * @return true if was able to connect or false otherwise
   */
  public boolean connect();
  
  /**
   * Stores the information from the Resource object into the database.
   * Returns true if was able to store the information or false otherwise
   * 
   * @param vm the object with the information to store
   * 
   * @return true if was able to store the information or false otherwise
   */
  public boolean storeVMInformation( CcdpVMResource vm );

  /**
   * Stores the information from the Resource object into the database.
   * Returns true if was able to store the information or false otherwise
   *
   * @param controller the object with the information to store
   *
   * @return true if was able to store the information or false otherwise
   */
  public boolean storeServerlessInformation( CcdpServerlessResource controller );

  /**
   * Gets the first object whose instance-id matches the given uniqueId.  If 
   * the object is not found it returns null
   * 
   * @param uniqueId the object's unique identifier
   * 
   * @return returns the object if found or null otherwise
   */
  public CcdpVMResource getVMInformation( String uniqueId );
  
  /**
   * Gets the first object whose node type matches the given controller type.  If 
   * the object is not found it returns null
   * 
   * @param nodeType the object's controller Type
   * 
   * @return returns the object if found or null otherwise
   */
  public CcdpServerlessResource getServerlessInformation( String nodeType );
  
  /**
   * Gets a list of all the resources stored in the database.
   * 
   * @return a list of the resources stored in the database
   */
  public List<CcdpResourceAbs> getAllInformation();
  
  /**
   * Gets a list of all the VM resources stored in the database.
   * 
   * @return a list of the VM resources stored in the database
   */
  public List<CcdpVMResource> getAllVMInformation();
  
  /**
   * Gets a list of all the resources stored in the database of the specified type.
   * 
   * @param node_type the type of node to filter the return list for
   * @return a list of the resources stored in the database of the specified type
   */
  public List<CcdpVMResource> getAllVMInformationOfType( String node_type );
  
  /**
   * Gets a list of all the resources stored in the database whose session id
   * matches the given one 'sid'
   * 
   * @param sid the session-id to query the database
   * 
   * @return a list of the resources stored in the database whose session id
   *         matches the given one 'sid'
   */
  public List<CcdpVMResource> getAllVMInformationBySessionId( String sid );
  
  /*
   * Get a list of resources that match node type and session id
   * 
   * @param SID the session search parameter
   * @param  node_type the node type search paramter
   * 
   * @return a list of resource that match both the node type and the session id
   */
  public List<CcdpVMResource> getAllVMInformationBySessionIdAndNodeType(
      String SID, String node_type);
  
  /**
   * Gets the total number of VMs stored in the database
   * 
   * @return the total number of VMs stored in the database
   */
  public int getTotalVMInformationCount();

  /**
   * Gets the total number of VMs stored in the database assigned to a specific
   * session
   * 
   * @param sid the session id of interest
   * 
   * @return the total number of VMs stored in the database assigned to a
   *         specific session
   */
  public int getVMInformationCount(String sid);
  
  /**
   * Deletes all the entries whose instance-id matches the given one.
   * 
   * @param uniqueId the object's unique identifier
   * 
   * @return returns the  number of entries that were removed 
   */
  public long deleteVMInformation( String uniqueId );
  
  /**
   * Deletes all the entries whose serverless controller type matches the given one.
   * 
   * @param nodeType the object's controller type
   * 
   * @return returns the  number of entries that were removed 
   */
  public long deleteServerlessInformation( String nodeType );
  
  /**
   * Disconnects the client from the database
   */
  public void disconnect();

}
