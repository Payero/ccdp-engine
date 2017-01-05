package com.axios.ccdp.mesos.factory;

import java.io.File;
import java.io.InputStream;

import org.json.JSONArray;
import org.json.JSONObject;

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
  public void configure( JSONObject config );
  
  /**
   * Creates a new storage location using the given argument.  If the storage 
   * already exists this method should not have any impact.  It returns true
   * if the action was successful or if the storage already exists and is 
   * accessible
   * 
   * @param root the name of the storage to create
   * @return true if the action was successful or if the storage already exists 
   *         and is accessible
   */
  public boolean createStorage(String root);
  /**
   * Creates a new file in the give storage location.  If the file already 
   * already exists it will get overwritten.  It returns true if the action was
   * successful.  If the file name is of the form 'path/filename.txt' then it
   * will be stored in the appropriate location such as a sub-directory.
   * 
   * @param root the name of the storage where the file will be stored
   * @param name the name of the file to store
   * @param file the actual file to store
   * 
   * @return true if the action was successful
   */
  public boolean storeFile(String root, String name, File file);
  /**
   * Retrieves the content of a file from the specified location.  The root 
   * indicates the path to begin the search and the filename is the path 
   * relative to the root.  For instance a file named 'ccdp/path/filename.txt' 
   * will use 'ccdp' as the root and 'path/filename.txt' as the filename
   * 
   * If the file is not found then the method returns null.
   * 
   * @param root the initial path to begin the search for the file
   * @param filename the name of the file relative to the root
   * 
   * @return an InputStream object with the contents of the file or null if not
   *         found
   */
  public InputStream getFile(String root, String filename);
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
  public JSONArray listAllStorages();
  /**
   * Lists all the files stored in ALL the storage locations.  For instance it 
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
   *  
   * @return a list of dictionaries containing the name of the storage and the
   *         creation time.
   */
  public JSONArray listAllFiles(String root);
  /**
   * Lists only the files stored in ALL the storage locations whose filename 
   * starts with the give prefix.  For instance it will return all the objects 
   * created in S3 buckets or file in directories on a file system 
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
   * @return a list of dictionaries containing the name of the files matching 
   *         the criteria and the creation time
   */
  public JSONArray listAllFilesWithPrefix( String root, String prefix);
  /**
   * Deletes the storage location and all its contents.  It returns true if the
   * operation was successful or false otherwise
   * 
   * @param name the name of the storage location to delete
   * @return true if the operation was successful or false otherwise
   */
  public boolean deleteStoreage(String name);
  /**
   * Deletes a file from the specified storage location.  The filename is 
   * relative to the given storage name.  It returns true if the operation was 
   * successful or false otherwise
   * 
   * @param root the name of the storage location where the file resides
   * @param filename the name of the file to to delete
   * @return true if the operation was successful or false otherwise
   */
  public boolean deleteFile(String root, String filename);
}
