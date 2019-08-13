package com.axios.ccdp.intfs;

import java.io.File;
import java.io.InputStream;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.JsonNode;


/**
 * Interface used to manage data being stored in a specific location.  This is
 * used to isolate storage implementations from the system.  For instance, the
 * storage location could be a file system or AWS S3 buckets.  In any case the
 * system does not need to know the actual implementation it just uses this
 * interface to store and retrieve data.
 * 
 * @author Oscar E. Ganteaume
 *
 */
public interface CcdpStorageControllerIntf
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
  public void configure( JsonNode config );
  
  /**
   * Creates a new storage location using the given argument.  If the storage 
   * already exists this method should not have any impact.  It returns true
   * if the action was successful or if the storage already exists and is 
   * accessible
   * 
   * @param where the name of the storage to create
   * @return true if the action was successful or if the storage already exists 
   *         and is accessible
   */
  public boolean createStorage(String where);
  
  /**
   * Creates a new element in the give storage location.  If the element  
   * already already exists it will get overwritten.  It returns true if the 
   * action was successful.  If the element name is of the form 'path/element'
   * then it will be stored in the appropriate location such as a sub-
   * directory.
   * 
   * @param where the name of the storage where the element will be stored
   * @param target the name of the element to store
   * @param source the actual element to store
   * 
   * @return true if the action was successful
   */
  public boolean storeElement(String where, String target, File source);
  
  /**
   * Retrieves the content of an element from the specified location.  The  
   * where indicates the path to begin the search and the element is the path 
   * relative to the where.  For instance an element named 'ccdp/path/element' 
   * will use 'ccdp' as the root and 'path/element' as the element name
   * 
   * If the element is not found then the method returns null.
   * 
   * @param where the initial path to begin the search for the element
   * @param what the name of the element relative to where
   * 
   * @return an InputStream object with the contents of the element or null 
   *         if not found
   */
  public InputStream getElement(String where, String what);
  
  /**
   * Lists all the storage locations created.  For instance it will return all 
   * the buckets created on AWS S3 or directories in a file system 
   * implementation.
   * 
   * The JSON Object is of the form:
   *  [{'name':'dir-1', "creation-time':'01-28-2017::09:09:20'},
   *   {'name':'dir-2', "creation-time':'01-28-2017::09:19:20'}
   *  ]
   *  
   * @return a list of dictionaries containing the name of the storage and the
   *         creation time.
   */
  public ArrayNode listAllStorages();
  
  /**
   * Lists all the elements stored in ALL the storage locations.  For instance it 
   * will return all the objects created in S3 buckets or file in directories 
   * on a file system implementation.
   * 
   * The JSON Object is of the form:
   *  [{'storage':'dir-1', 
   *    'creation-time':'01-28-2017::09:09:20',
   *    'files':[{'name':'file1.txt', "creation-time':'01-28-2017::09:09:20'},
   *             {'name':'file2.txt', "creation-time':'01-28-2017::09:09:20'}
   *            ]
   *   {'storage':dir-2', 
   *    'creation-time':'01-28-2017::09:09:20',
   *    'files':[{'name':'file1.txt', "creation-time':'01-28-2017::09:09:20'},
   *             {'name':'file2.txt', "creation-time':'01-28-2017::09:09:20'}
   *            ]
   *  ]
   * @param where the location to retrieve all elements from
   *  
   * @return a list of dictionaries containing the name of the storage and the
   *         creation time.
   */
  public ArrayNode listAllElements(String where);
  
  /**
   * Lists only the elements stored in ALL the storage locations whose name 
   * starts with the give prefix.  For instance it will return all the objects 
   * created in S3 buckets or files in directories on a file system 
   * implementation matching the criteria. In other words to retrieve all the 
   * files in the 'path' sub-directory under the 'ccdp' storage then the root
   * should be 'ccdp' and the prefix 'path'.  
   * 
   * 
   * The JSON Object is of the form:
   *  [{'name':'file1.txt', "creation-time':'01-28-2017::09:09:20'},
   *   {'name':'file2.txt', "creation-time':'01-28-2017::09:09:20'}
   *  ]
   * 
   * @param where the location to retrieve all elements from
   * @param prefix the string to search for matching elements
   *  
   * @return a list of dictionaries containing the name of the files matching 
   *         the criteria and the creation time
   */
  public ArrayNode listAllElementsWithPrefix( String where, String prefix);
  
  /**
   * Deletes the storage location and all its contents.  It returns true if the
   * operation was successful or false otherwise
   * 
   * @param where the name of the storage location to delete
   * @return true if the operation was successful or false otherwise
   */
  public boolean deleteStorage(String where);
  
  /**
   * Deletes an element from the specified storage location.  The name is  
   * relative to the given storage name.  It returns true if the operation was 
   * successful or false otherwise
   * 
   * @param where the name of the storage location where the element resides
   * @param what the name of the element to to delete
   * @return true if the operation was successful or false otherwise
   */
  public boolean deleteElement(String where, String what);
}
