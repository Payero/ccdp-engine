package com.axios.ccdp.test.unittest;



import java.util.HashMap;
import java.util.Map;

import org.apache.log4j.Logger;
import org.junit.Test;

import com.axios.ccdp.connections.amq.AmqReceiver;
import com.axios.ccdp.connections.amq.AmqSender;
import com.axios.ccdp.connections.intfs.CcdpEventConsumerIntf;
import com.axios.ccdp.utils.CcdpUtils;
import com.axios.ccdp.utils.ThreadController;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import junit.framework.TestCase;

public class AmqConnectionsUnitTest extends TestCase implements CcdpEventConsumerIntf
{

  /**
   * Generates debug print statements based on the verbosity level.
   */
  private Logger logger = Logger
      .getLogger(AmqConnectionsUnitTest.class.getName());

  private AmqSender sender = null;
  private AmqReceiver receiver = null;
  private String broker = null;
  
  private ThreadController block = new ThreadController();
  private JsonNode latest = null;
  private ObjectMapper mapper = new ObjectMapper();
  
  public AmqConnectionsUnitTest()
  {

  }

  public void setUp()
  {
    String cfg_file = System.getProperty(CcdpUtils.CFG_KEY_CFG_FILE);
    try
    {
      CcdpUtils.loadProperties(cfg_file);
      CcdpUtils.configLogger();
    }
    catch( Exception e )
    {
      System.err.println("Could not setup environment");
    }
    
    Map<String, String> map = CcdpUtils.getKeysByFilter("taskingIntf");
    this.broker = map.get(CcdpUtils.CFG_KEY_BROKER_CONNECTION);
    this.logger.debug("Connection to " + broker);
    
    this.receiver = new AmqReceiver(this);
    this.sender = new AmqSender();
    
//    this.receiver.connect(broker,  this.channel);
//    this.sender.connect(broker,  this.channel);
  }
  
  public void tearDown()
  {
    if( this.sender != null )
      this.sender.disconnect();
    
    if( this.receiver != null )
      this.receiver.disconnect();
    
    this.block.clear();
    this.latest = null;
  }
  
  public void onEvent( Object event )
  {
    String msg = event.toString();
    this.logger.debug("Got a new Event: " + msg);
    try
    {
      this.latest = this.mapper.readTree(msg);
    }
    catch( Exception e )
    {
      this.logger.error("Message: " + e.getMessage(), e);
    }
    
    this.block.set();
  }
  
  @Test
  public void testSimpleMessage()
  {
    this.logger.debug("Testing Simple Message");
    assertNotNull(this.sender);
    assertNotNull(this.receiver);

    String channel = "AMQ-testsimpleMessage";
    this.receiver.connect(broker,  channel);
    this.sender.connect(broker,  channel);
    
    String msg = "This is just a test message";
    this.sender.sendMessage(null, msg, 500);
    while( !this.block.isSet() )
      this.block.doWait();
    assertNotNull(this.latest);
    this.logger.debug("Latest " + this.latest.get("body"));
    
    assertEquals(msg, this.latest.get("body").asText() );
  }
  
  @Test
  public void testMessageWithOptions()
  {
    this.logger.debug("Testing Message with Options");
    assertNotNull(this.sender);
    assertNotNull(this.receiver);
    String channel = "AMQ-testsimpleMessageWithOptions";
    this.receiver.connect(broker,  channel);
    this.sender.connect(broker,  channel);

    ObjectNode node = this.mapper.createObjectNode();
    String msg = "This is just a test message with options";
    node.put("body",  msg);
    
    Map<String, String> props = new HashMap<>();
    props.put("key-1", "value-1");
    props.put("key-2", "value-2");
    props.put("key-3", "value-3");
    JsonNode cfg = this.mapper.convertValue(props,  JsonNode.class);
    node.set("config", cfg);
    
    this.sender.sendMessage(props, msg, 500);
    this.logger.debug("Message Sent");
    while( !this.block.isSet() )
      this.block.doWait();
    assertNotNull(this.latest);
    
    this.logger.debug("Got a new Message " + this.latest.toString());
    
    assertNotNull(this.latest);
    this.logger.debug("Got a latest " + latest.toString() );
    assertEquals(node.get("config"), this.latest.get("config"));
    assertEquals(node.get("body"), this.latest.get("body"));
  }
  
}
