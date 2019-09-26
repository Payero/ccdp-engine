package com.axios.ccdp.test;

import java.util.Arrays;

import org.apache.log4j.Logger;
import org.bson.Document;

import com.axios.ccdp.utils.CcdpUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mongodb.client.MongoClient;
import com.mongodb.Block;
import com.mongodb.MongoClientSettings;
import com.mongodb.ServerAddress;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Aggregates;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.changestream.ChangeStreamDocument;
import com.mongodb.client.model.changestream.FullDocument;

public class CCDPTest 
{
  /**
   * Generates debug print statements based on the verbosity level.
   */
  private Logger logger = Logger.getLogger(CCDPTest.class.getName());
  
  private String last = "";
  
  ObjectMapper mapper = new ObjectMapper();
  
  public CCDPTest() throws Exception
  {    
    this.logger.debug("Start Test");
    
    ServerAddress sa = new ServerAddress("ax-ccdp.com", 27017);
    MongoClient client = MongoClients.create(
        MongoClientSettings.builder()
        .applyToClusterSettings(builder ->
                builder.hosts(Arrays.asList(sa)))
        .build());
    
    MongoDatabase db = client.getDatabase("CCDP");
    MongoCollection<Document> statusColl = db.getCollection("srb-test");
    this.logger.debug("Set up collection");
    Block<ChangeStreamDocument<Document>> printBlock = new Block<>() {
      @Override
      public void apply(final ChangeStreamDocument<Document> changeStreamDocument) {
          String id = changeStreamDocument.getFullDocument().get("_id").toString();
          if ( last.equals(id) )
            System.out.println("Duplicate");
          else
          {
            System.out.println(id);
            last = id;
          }
      }
    };
    statusColl.watch(Arrays.asList(Aggregates.match(Filters.in("operationType", Arrays.asList("insert", "update", "replace")))))
    .fullDocument(FullDocument.UPDATE_LOOKUP).forEach(printBlock);
        
  }
  
  public static void main( String[] args ) throws Exception
  {
    String cfg_file = System.getProperty("ccdp.config.file");
        
    // Uses the cfg file to configure all CcdpUtils for use in the next service
    CcdpUtils.loadProperties(cfg_file);
    CcdpUtils.configLogger();
    
    new CCDPTest();
  }

}



