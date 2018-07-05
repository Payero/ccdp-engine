package com.axios.ccdp.cloud.docker;

import java.io.File;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.Date;

import org.apache.log4j.Logger;

import com.axios.ccdp.connections.intfs.CcdpStorageControllerIntf;
import com.axios.ccdp.resources.CcdpImageInfo;
import com.axios.ccdp.utils.CcdpUtils;
import com.axios.ccdp.utils.CcdpUtils.CcdpNodeType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Class used to manage data being stored in AWS S3 buckets.  
 * 
 * @author Oscar E. Ganteaume
 *
 */
public class DockerStorageControllerImpl implements CcdpStorageControllerIntf
{
  /**
   * Generates debug print statements based on the verbosity level.
   */
  private Logger logger = 
          Logger.getLogger(DockerStorageControllerImpl.class.getName());

  /**
   * Formats the date in a specific way
   */
  private  SimpleDateFormat date_formatter = 
                                new SimpleDateFormat("MM-dd-yyyy::hh:mm:ss");
  /**
   * Creates all the ObjectNode and ArrayNode objects
   */
  private ObjectMapper mapper = new ObjectMapper();
  
  /**
   * Instantiates a new object
   */
  public DockerStorageControllerImpl()
  {
    this.logger.debug("Instantiating a new Storage class");
  }
  
  /**
   * Configures the running environment and/or connections required to perform
   * the operations.  The JSON Object contains all the different fields 
   * necessary to operate.  These fields might change on each actual 
   * implementation
   * 
   * @param config a JSON Object containing all the necessary fields required 
   *        to operate
   */
  public void configure( ObjectNode config )
  {
    if( config == null )
      throw new IllegalArgumentException("The configuration cannot be null");
    
    this.logger.debug("Configuring object using: " + config.toString());
   }
  
  /**
   * Creates a new bucket using the given argument.  If the bucket 
   * already exists this method does not have any impact.  It returns true
   * if the action was successful or if the storage already exists and is 
   * accessible
   * 
   * @param bucket the name of the bucket to create
   * @return true if the action was successful or if the storage already exists 
   *         and is accessible
   */
  @Override
  public boolean createStorage(String bucket)
  {
    this.logger.info("Creating Bucket: " + bucket);
    boolean created = false;
    
    return created;
  }
  
  /**
   * Creates a new file in the give bucket name.  If the file already 
   * already exists it will get overwritten.  It returns true if the action was
   * successful.  If the file name is of the form 'path/filename.txt' then it
   * will be stored in the appropriate location such as a sub-directory.
   * 
   * @param dir the name of the directory where the file will be stored
   * @param name the name of the file to store
   * @param file the actual file to store
   * 
   * @return true if the action was successful
   */
  @Override
  public boolean storeElement(String dir, String name, File file)
  {
    this.logger.info("Creating File " + name + " in directory: " + dir);
    return false;
  }
  
  /**
   * Retrieves the content of a file from the specified location.  The bucket 
   * indicates the path to begin the search and the filename is the path 
   * relative to the bucket name.  For instance a file named 
   * 'ccdp/path/filename.txt' needs to use 'ccdp' as the bucket name and 
   * 'path/filename.txt' as the filename
   * 
   * If the file is not found then the method returns null.
   * 
   * @param dir the initial path to begin the search for the file
   * @param filename the name of the file relative to the root
   * 
   * @return an InputStream object with the contents of the file or null if not
   *         found
   */
  @Override
  public InputStream getElement(String dir, String filename)
  {
    InputStream input = null;
    
    
    return input;
  }
  
  /**
   * Lists all the buckets created.  
   * 
   * The JSON Object is of the form:
   *  [{'name':'bucket-1', "creation-time':'01-28-2017::09:09:20'},
   *   {'name':'bucket-2', "creation-time':'01-28-2017::09:19:20'}
   *  ]
   *  
   * @return a list of dictionaries containing the name of the buckets and the
   *         creation time.
   */
  @Override
  public ArrayNode listAllStorages()
  {
    ArrayNode folders = this.mapper.createArrayNode();
    
    
    
    return folders;
  }
  
  /**
   * Lists all the files stored in ALL the buckets.  
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
  @Override
  public ArrayNode listAllElements(String root)
  {
    ArrayNode folders = this.mapper.createArrayNode();
    
    return folders;
  }
  
  /**
   * Lists only the files stored in the bucket whose filename starts with the 
   * given prefix.  In other words to retrieve all the files in the 'path' 
   * sub-directory under the 'ccdp' storage then the bucket should be 'ccdp' 
   * and the prefix 'path'.  
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
  @Override
  public ArrayNode listAllElementsWithPrefix( String folder, String prefix)
  {
    String msg = "Getting all file from bucket " + folder + 
                 " starting with " + prefix;
    this.logger.debug(msg);
    ArrayNode files = this.mapper.createArrayNode();
    
    
    return files;
  }
  
  /**
   * Deletes a given bucket and all its contents.  It returns true if the
   * operation was successful or false otherwise
   * 
   * @param folder the name of the bucket to delete
   * @return true if the operation was successful or false otherwise
   */
  @Override
  public boolean deleteStorage(String folder)
  {
    this.logger.info("Deleting Bucket " + folder);
    return false;
  }
  
  /**
   * Deletes a file from the specified bucket.  The filename is 
   * relative to the given bucket name.  It returns true if the operation was 
   * successful or false otherwise
   * 
   * @param folder the name of the folder where the file resides
   * @param filename the name of the file to to delete
   * @return true if the operation was successful or false otherwise
   */
  @Override
  public boolean deleteElement(String folder, String filename)
  {
    this.logger.info("Deleting file " + filename + " from folder " +  folder);
    return false;
  }
  
}
