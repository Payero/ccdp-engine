package com.axios.ccdp.test;

import java.util.Iterator;

import org.apache.log4j.Logger;
import org.bson.Document;

import com.axios.ccdp.utils.CcdpUtils;
import com.mongodb.MongoClient;
import com.mongodb.MongoCommandException;
import com.mongodb.MongoCredential;
import com.mongodb.client.FindIterable;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Updates;


public class MongoTest 
{
  /**
   * Generates debug print statements based on the verbosity level.
   */
  private Logger logger = Logger.getLogger(MongoTest.class.getName());
  
  
  public MongoTest()
  {
    this.logger.debug("Running Mongo Test");
    try
    {
      this.runTest();
    }
    catch( Exception e )
    {
      this.logger.error("Message: " + e.getMessage(), e);
    }
    
  }
  
  
  private void runTest() throws Exception
  {
    this.logger.debug("Running the Test");
    
 // Creating a Mongo client 
    MongoClient mongo = new MongoClient( "localhost" , 27017 ); 
 
    // Creating Credentials 
    MongoCredential credential; 
    credential = MongoCredential.createCredential("sampleUser", "myDb", 
       "password".toCharArray()); 
    this.logger.debug("Connected to the database successfully");  
    
    // Accessing the database 
    MongoDatabase database = mongo.getDatabase("myDb"); 
    this.logger.debug("Credentials ::"+ credential);
    
    MongoCollection<Document> collection = null;
    // Retrieving a collection
    try
    {
      //Creating a collection 
      database.createCollection("sampleCollection"); 
      this.logger.debug("Collection created successfully");

    }
    catch( MongoCommandException mce)
    {
      this.logger.error("Got an exception: " + mce.getMessage() );
    }
    collection = database.getCollection("sampleCollection"); 
    this.logger.debug("Collection sampleCollection selected successfully");
    
    Document document = new Document("title", "MongoDB") 
        .append("id", 1)
        .append("description", "database") 
        .append("likes", 100) 
        .append("url", "http://www.tutorialspoint.com/mongodb/") 
        .append("by", "tutorials point");  
        collection.insertOne(document); 
    
    this.logger.debug("Document inserted successfully");
        
    // Getting the iterable object 
    FindIterable<Document> iterDocSingle = collection.find(); 
    int i = 1; 

    // Getting the iterator 
    Iterator<Document> it = iterDocSingle.iterator(); 
  
    while (it.hasNext()) 
    {  
       this.logger.debug(it.next());  
    i++; 
    }
    collection.updateOne(Filters.eq("id", 1), Updates.set("likes", 150));       
    this.logger.debug("Document update successfully...");  
    
    // Retrieving the documents after updation 
    // Getting the iterable object
    FindIterable<Document> iterDoc = collection.find(); 
    int n = 1; 

    // Getting the iterator 
    Iterator<Document> itUp = iterDoc.iterator(); 

    while (itUp.hasNext()) {  
       this.logger.debug(itUp.next());  
       n++; 
    }
    
    
 // Deleting the documents 
    collection.deleteOne(Filters.eq("id", 1)); 
    this.logger.debug("Document deleted successfully...");  
    
    // Retrieving the documents after updation 
    // Getting the iterable object 
    FindIterable<Document> iterDocDel = collection.find(); 
    int y = 1; 

    // Getting the iterator 
    Iterator<Document> itDel = iterDocDel.iterator(); 

    while (itDel.hasNext()) {  
       System.out.println("Inserted Document: "+y);  
       System.out.println(itDel.next());  
       y++; 
    }       
    
    // Dropping a Collection 
    collection.drop();
    this.logger.debug("Collection dropped");
    
    this.logger.debug("Collection created successfully"); 
    database = mongo.getDatabase("test"); 
    for (String name : database.listCollectionNames()) 
    { 
       this.logger.debug("Collection Name: " + name); 
    } 
    
  }
  
  
  
  public static void main( String[] args ) throws Exception
  {
    String cfg_file = System.getProperty("ccdp.config.file");
    CcdpUtils.loadProperties(cfg_file);
    CcdpUtils.configLogger();
    
    new MongoTest();
  }

}



