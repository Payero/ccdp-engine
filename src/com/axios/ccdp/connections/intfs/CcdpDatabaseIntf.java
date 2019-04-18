package com.axios.ccdp.connections.intfs;

import java.util.List;

import com.axios.ccdp.resources.CcdpVMResource;
import com.fasterxml.jackson.databind.node.ObjectNode;

public interface CcdpDatabaseIntf
{
  /**
   * Gets all the settings required to connect from the given parameter
   * 
   * @param config the JSON representation of the DB connection arguments
   */
  public void configure( ObjectNode config );
  
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
   * Gets the first object whose instance-id matches the given uniqueId.  If 
   * the object is not found it returns null
   * 
   * @param uniqueId the object's unique identifier
   * 
   * @return returns the object if found or null otherwise
   */
  public CcdpVMResource getVMInformation( String uniqueId );
  
  /**
   * Gets a list of all the resources stored in the database.
   * 
   * @return a list of the resources stored in the database
   */
  public List<CcdpVMResource> getAllVMInformation();
  
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
   * Disconnects the client from the database
   */
  public void disconnect();
}
