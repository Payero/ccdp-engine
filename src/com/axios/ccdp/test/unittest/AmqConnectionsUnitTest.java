package com.axios.ccdp.test.unittest;



import java.util.HashMap;
import java.util.Map;

import org.apache.log4j.Logger;
import org.junit.Test;

import com.axios.ccdp.impl.connections.amq.AmqReceiver;
import com.axios.ccdp.impl.connections.amq.AmqSender;
import com.axios.ccdp.intfs.CcdpMessageConsumerIntf;
import com.axios.ccdp.messages.CcdpMessage;
import com.axios.ccdp.messages.UndefinedMessage;
import com.axios.ccdp.utils.CcdpUtils;
import com.axios.ccdp.utils.ThreadController;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import junit.framework.TestCase;

public class AmqConnectionsUnitTest extends TestCase implements CcdpMessageConsumerIntf
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
  private CcdpMessage latest = null;
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
    
    JsonNode conn_cfg = CcdpUtils.getConnnectionIntfCfg();
    this.broker = conn_cfg.get(CcdpUtils.CFG_KEY_BROKER_CONNECTION).asText();    
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
  
  public void onCcdpMessage( CcdpMessage msg )
  {
    this.logger.debug("Got a new Event: " + msg.toString() );
    this.latest = msg;
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
    UndefinedMessage undMsg = new UndefinedMessage();
    undMsg.setPayload(msg);
    this.sender.sendMessage(null, undMsg, 500);
    while( !this.block.isSet() )
      this.block.doWait();
    assertNotNull(this.latest);
    if( this.latest instanceof UndefinedMessage )
    {
      UndefinedMessage tstMsg = (UndefinedMessage)this.latest;
      String load = tstMsg.getPayload().toString();
      this.logger.debug("Latest " + load );
      assertEquals(msg, load );
    }
    else
    {
      fail("The CcdpMessage was not UndefinedMessage");
    }
  }
  
  //@Test
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
    UndefinedMessage tstMsg = new UndefinedMessage();
    tstMsg.setPayload(msg);
    this.sender.sendMessage(props, tstMsg, 500);
    this.logger.debug("Message Sent");
    while( !this.block.isSet() )
      this.block.doWait();
    assertNotNull(this.latest);
    
    this.logger.debug("Got a new Message " + this.latest.toString());
    
    assertNotNull(this.latest);
    
    if( this.latest instanceof UndefinedMessage )
    {
      UndefinedMessage undMsg = (UndefinedMessage)this.latest;
      String load = undMsg.getPayload().toString();
      Map<String, String> map = undMsg.getConfiguration();
      
      this.logger.debug("Got a latest " + load );
      assertEquals(props, map);
      assertEquals(msg, load);
    }
    else
    {
      fail("The CcdpMessage was not UndefinedMessage");
    }
  }
  
}
