package com.axios.ccdp.impl.cloud.aws;

import java.io.File;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.Date;

import org.apache.log4j.Logger;

import com.amazonaws.AmazonClientException;
import com.amazonaws.AmazonServiceException;
import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.services.s3.AmazonS3Client;
import com.amazonaws.services.s3.model.Bucket;
import com.amazonaws.services.s3.model.GetObjectRequest;
import com.amazonaws.services.s3.model.ListObjectsRequest;
import com.amazonaws.services.s3.model.ListObjectsV2Request;
import com.amazonaws.services.s3.model.ListObjectsV2Result;
import com.amazonaws.services.s3.model.ObjectListing;
import com.amazonaws.services.s3.model.PutObjectRequest;
import com.amazonaws.services.s3.model.S3Object;
import com.amazonaws.services.s3.model.S3ObjectSummary;
import com.axios.ccdp.intfs.CcdpStorageControllerIntf;
import com.axios.ccdp.utils.CcdpUtils;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Class used to manage data being stored in AWS S3 buckets.  
 * 
 * @author Oscar E. Ganteaume
 *
 */
public class AWSCcdpStorageControllerImpl implements CcdpStorageControllerIntf
{
  /**
   * Generates debug print statements based on the verbosity level.
   */
  private Logger logger = 
          Logger.getLogger(AWSCcdpStorageControllerImpl.class.getName());

  /**
   * Stores the object responsible for accessing the S3 resources
   */
  private AmazonS3Client s3 = null;
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
  public AWSCcdpStorageControllerImpl()
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
  public void configure( JsonNode config )
  {
    if( config == null )
      throw new IllegalArgumentException("The configuration cannot be null");
    
    this.logger.debug("Configuring object using: " + config.toString());
    try
    {
      JsonNode awsCreds = CcdpUtils.getCredentials().get("AWS");
      AWSCredentials credentials = AWSUtils.getAWSCredentials(
          awsCreds.get(CcdpUtils.CFG_KEY_CREDENTIALS_FILE).asText(),
          awsCreds.get(CcdpUtils.CFG_KEY_CREDENTIALS_PROFILE).asText());
      
      if( credentials != null )
      {
        this.s3 = new AmazonS3Client(credentials);
      }
    }
    catch( Exception e)
    {
      this.printException(e, true);
    }
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
    try
    {
      this.s3.createBucket(bucket);
      created = true;
    }
    catch( Exception e)
    {
      this.printException(e, true);
    }
    
    return created;
  }
  
  /**
   * Creates a new file in the give bucket name.  If the file already 
   * already exists it will get overwritten.  It returns true if the action was
   * successful.  If the file name is of the form 'path/filename.txt' then it
   * will be stored in the appropriate location such as a sub-directory.
   * 
   * @param bucket the name of the bucket where the file will be stored
   * @param name the name of the file to store
   * @param file the actual file to store
   * 
   * @return true if the action was successful
   */
  @Override
  public boolean storeElement(String bucket, String name, File file)
  {
    this.logger.info("Creating File " + name + " in bucket: " + bucket);
    try
    {
      if( !file.isFile() )
      {
        String e = String.format("File (%s) cannot be found", file.getName());
        throw new IllegalArgumentException(e);
      }
      
      PutObjectRequest request = new PutObjectRequest(bucket, name, file);
      this.s3.putObject(request);
      return true;
    }
    catch( Exception e)
    {
      this.printException(e, true);
      return false;
    }
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
   * @param bucket the initial path to begin the search for the file
   * @param filename the name of the file relative to the root
   * 
   * @return an InputStream object with the contents of the file or null if not
   *         found
   */
  @Override
  public InputStream getElement(String bucket, String filename)
  {
    InputStream input = null;
    try
    {
      this.logger.debug("Retrieving file " + filename + " from bucket " + bucket);
      GetObjectRequest request = new GetObjectRequest(bucket, filename);
      S3Object object = this.s3.getObject(request);
      String ct = object.getObjectMetadata().getContentType();
      this.logger.debug("Content-Type: "  + ct);
      input = object.getObjectContent();
    }
    catch( Exception e)
    {
      this.printException(e, true);
    }
    
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
    ArrayNode buckets = this.mapper.createArrayNode();
    
    this.logger.debug("Listing buckets");
    try
    {
      for (Bucket bucket : s3.listBuckets()) 
      {
        String name = bucket.getName();
        Date date = bucket.getCreationDate();
        ObjectNode bkt = this.mapper.createObjectNode();
        bkt.put("name", name);
        bkt.put("creation-time", this.date_formatter.format(date));
        buckets.add(bkt);
      }
    }
    catch( Exception e)
    {
      this.printException(e, true);
    }
    
    return buckets;
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
    ArrayNode buckets = this.mapper.createArrayNode();
    try
    {
      this.logger.debug("Listing buckets");
      for (Bucket bucket : s3.listBuckets()) 
      {
        ObjectNode bkt = this.mapper.createObjectNode();
        String name = bucket.getName();
        Date date = bucket.getCreationDate();
        
        bkt.put("storage", name);
        bkt.put("creation-time", this.date_formatter.format(date));
        
        ArrayNode files = this.mapper.createArrayNode();
        final ListObjectsV2Request req = 
                  new ListObjectsV2Request().withBucketName(name);
        
        ListObjectsV2Result result;
        do 
        {               
           result = this.s3.listObjectsV2(req);
           
           for (S3ObjectSummary obj : result.getObjectSummaries()) 
           {
             ObjectNode json = this.mapper.createObjectNode();
             
             String key = obj.getKey();
             Date fileDate = obj.getLastModified();
             json.put("storage", key);
             json.put("creation-time", this.date_formatter.format(fileDate));
             files.add(json);
           }
           req.setContinuationToken(result.getNextContinuationToken());
        } while(result.isTruncated() == true ); 
        bkt.set("files", files);
        buckets.add(bkt);
      }
    }
    catch( Exception e)
    {
      this.printException(e, true);
    }
    return buckets;
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
  public ArrayNode listAllElementsWithPrefix( String bucket, String prefix)
  {
    String msg = "Getting all file from bucket " + bucket + 
                 " starting with " + prefix;
    this.logger.debug(msg);
    ArrayNode files = this.mapper.createArrayNode();
    
    try
    {
      ListObjectsRequest req = new ListObjectsRequest()
                                            .withBucketName(bucket)
                                            .withPrefix(prefix);
                                            
      ObjectListing objectListing = this.s3.listObjects(req);
      
      for (S3ObjectSummary obj : objectListing.getObjectSummaries()) 
      {
        String name = obj.getKey();
        Date date = obj.getLastModified();
        ObjectNode file = this.mapper.createObjectNode();
        file.put("name", name);
        file.put("creation-time", this.date_formatter.format(date));
        files.add(file);
      }
    }
    catch( Exception e)
    {
      this.printException(e, true);
    }
    
    return files;
  }
  
  /**
   * Deletes a given bucket and all its contents.  It returns true if the
   * operation was successful or false otherwise
   * 
   * @param bucket the name of the bucket to delete
   * @return true if the operation was successful or false otherwise
   */
  @Override
  public boolean deleteStorage(String bucket)
  {
    this.logger.info("Deleting Bucket " + bucket);
    try
    {
      this.s3.deleteBucket(bucket);
      return true;
    }
    catch( Exception e)
    {
      this.printException(e, true);
      return false;
    }
  }
  
  /**
   * Deletes a file from the specified bucket.  The filename is 
   * relative to the given bucket name.  It returns true if the operation was 
   * successful or false otherwise
   * 
   * @param bucket the name of the bucket where the file resides
   * @param filename the name of the file to to delete
   * @return true if the operation was successful or false otherwise
   */
  @Override
  public boolean deleteElement(String bucket, String filename)
  {
    this.logger.info("Deleting file " + filename + " from bucket " +  bucket);
    try
    {
      this.s3.deleteObject(bucket, filename);
      return true;
    }
    catch( Exception e)
    {
      this.printException(e, true);
      return false;
    }
  }
  
  /**
   * Prints a descriptive error message based on the type of exception (Service
   * or Client).
   * 
   * @param e the exception generated by the code
   * @param add_stacktrace flag indicating whether to print the stacktrace or 
   *        not
   */
  private void printException(Exception e, boolean add_stacktrace)
  {
    if( e instanceof AmazonServiceException )
    {
      AmazonServiceException ase = (AmazonServiceException)e;
      String err = "Caught an AmazonServiceException, which means your "
          + "request made it to Amazon S3, but was rejected with an error "
          + "response for some reason.";
      this.logger.error(err);
      this.logger.error("Error Message:    " + ase.getMessage());
      this.logger.error("HTTP Status Code: " + ase.getStatusCode());
      this.logger.error("AWS Error Code:   " + ase.getErrorCode());
      this.logger.error("Error Type:       " + ase.getErrorType());
      this.logger.error("Request ID:       " + ase.getRequestId());
    }
    else if( e instanceof AmazonClientException )
    {
      AmazonClientException ace = (AmazonClientException)e;
      String err = "Caught an AmazonClientException, which means the client "
          + "encountered a serious internal problem while trying to "
          + "communicate with S3, such as not being able to access the "
          + "network.";
      this.logger.error(err);
      this.logger.error("Error Message: " + ace.getMessage());
    }
    else
    {
      String err = "Caught an exception so the operation might have failed. " +
                   "The actual error message is: " + e.getMessage();
      this.logger.error(err);
    }
    
    if( add_stacktrace )
      e.printStackTrace();
  }
}
