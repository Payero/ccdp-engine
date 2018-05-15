package com.axios.ccdp.test;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.LinkedList;
import java.util.Map;
import java.util.Queue;
import java.util.Random;
import java.util.UUID;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.log4j.Logger;

import com.amazonaws.services.devicefarm.model.ArgumentException;
import com.axios.ccdp.connections.amq.AmqSender;
import com.axios.ccdp.messages.ResourceUpdateMessage;
import com.axios.ccdp.resources.CcdpVMResource;
import com.axios.ccdp.resources.CcdpVMResource.ResourceStatus;
import com.axios.ccdp.tasking.CcdpTaskRequest;
import com.axios.ccdp.utils.CcdpUtils;
import com.axios.ccdp.utils.TaskEventIntf;
import com.axios.ccdp.utils.ThreadController;
import com.axios.ccdp.utils.ThreadedTimerTask;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;


public class CcdpStatusSender implements TaskEventIntf
{
  /**
   * Generates debug print statements based on the verbosity level.
   */
  private Logger logger = Logger.getLogger(CcdpStatusSender.class.getName());
  /**
   * Parses and prints all the options or arguments used by this application
   */
  private static HelpFormatter formatter = new HelpFormatter();
  /**
   * How long before generating more messages to send and add to the queue
   */
  private static long EVENT_CYCLE = 10000;
  /**
   * Stores all the options that can be used by this application
   */
  private static Options options = new Options();
  /**
   * Generates all the JSON Objects
   */
  private ObjectMapper mapper = new ObjectMapper();
  /**
   * Sends JMS Tasking Messages to the framework
   */
  private AmqSender sender = null;
  /**
   * Stores all the resource configuration for the simulation
   */
  private ObjectNode resource = null;
  /**
   * Calls the onEvent method every so often
   */
  private ThreadedTimerTask timer = null;
  /**
   * Determines when to stop the application from running
   */
  private ThreadController controller = new ThreadController();
  /**
   * When this application started running so it can be stopped
   */
  private long started;
  /**
   * Stores all the messages that need to be send
   */
  private Queue<ResourceUpdateMessage> messages = new LinkedList<>();
  /**
   * Stores the name of the queue to send data to the engine
   */
  private String toEngine = null;
  
  /**
   * Instantiates a new Message Sender and performs different operations
   * 
   * @param data a String representation of a JSON object containing all the 
   *        required information to send
   */
  public CcdpStatusSender( String data )
  {
    this.sender = new AmqSender();
    
    Map<String, String> map = 
        CcdpUtils.getKeysByFilter(CcdpUtils.CFG_KEY_CONN_INTF);
    
    String broker = map.get(CcdpUtils.CFG_KEY_BROKER_CONNECTION);
    String channel = CcdpUtils.getProperty(CcdpUtils.CFG_KEY_MAIN_CHANNEL);
    
    this.toEngine = channel.trim();
    
    this.logger.info("Sending Tasking to " + broker + ":" + channel);
    this.sender.connect(broker, channel);
    try
    {
      if( data != null )
      {
        this.logger.info("Parsing data: " + data);
        JsonNode json = this.mapper.readTree( data );
        // makes sure all the configurations are set
        this.normalizeEntries(json);
        // starts calling the onEvent method
        this.timer = new ThreadedTimerTask(this, EVENT_CYCLE, EVENT_CYCLE);
        this.started = System.currentTimeMillis();
        // runs until it either times out or is killed
        this.runMain();
      }
    }
    catch(Exception e)
    {
      this.logger.error("Got an error: " + e.getMessage());
    }
    finally
    {
      this.logger.info("Done, disconnecting quiting now");
      this.sender.disconnect();
    }
  }  
  
  /**
   * Runs the main process where it will send messages in the queue for as long
   * as the thread controller is not set.
   * 
   * @throws JsonProcessingException a JsonProcessingException is thrown if the
   *         method cannot parse the given json file
   */
  private void runMain() throws JsonProcessingException
  {
    ObjectWriter writer = this.mapper.writerWithDefaultPrettyPrinter();
    String prettyJson = writer.writeValueAsString(this.resource);
    
    this.logger.info("Running Main Using: " + prettyJson);
    int max = this.resource.get("max-random-send-secs").asInt();
    Random random = new Random();
    // runs until thread controller is set due to timeout
    while( !this.controller.isSet() )
    {
      // as long as there are messages in the queue
      while( !this.messages.isEmpty() )
      {
        ResourceUpdateMessage msg = this.messages.poll();
        if( msg != null )
        {
          int wait = random.nextInt(max);
          this.logger.debug("Waiting " + wait + " before sending the next msg");
          this.controller.doWait(wait);
          this.sender.sendMessage(null, msg);
        }
      }
      // if the queue is empty, just wait a little
      this.controller.doWait(0.25);
    }
    
    this.sender.disconnect();
  }

  /**
   * Generates messages to send based on the resources file given during the
   * object initialization.  It checks to make sure that if the timeout was 
   * check then it will terminate when the time expires.  It generates one 
   * message per node per session with just the basic information set
   */
  @Override
  public void onEvent()
  {
    // as long as there is no timeout...
    if( !this.timeout() )
    {
      // start cranking messages per session and per nodes
      for(JsonNode session: this.resource.get("sessions") )
      {
        String sid = session.get("session-name").asText();
        this.logger.debug("Generating messages for " + sid );
        double cpu = this.getRandom(session.get("cpu") );
        double mem = this.getRandom(session.get("mem") );
        double tasks = this.getRandom(session.get("tasks") );
        // generate one message per node on each session
        for(JsonNode vm : session.get("nodes") )
        {
          CcdpVMResource res = new CcdpVMResource(vm.asText());
          res.setStatus(ResourceStatus.RUNNING);
          
          res.setAssignedSession(sid);
          this.logger.debug("Setting CPU to " + cpu);
          this.logger.debug("Setting MEM to " + mem);
          this.logger.debug("Setting TSK to " + tasks);
          res.setCPULoad(cpu);
          res.setMemLoad(mem);
          for( int i = 0; i < tasks; i++ )
            res.addTask( new CcdpTaskRequest() );
          
          ResourceUpdateMessage msg = new ResourceUpdateMessage();
          msg.setCcdpVMResource(res);
          this.messages.add(msg);
        }
      }
    }
  }

  /**
   * Generates a random number based on the min and max defined in the JsonNode.
   * If the node is missing at least one of the fields then it throws an 
   * exception.
   * 
   * @param node the object containing the min and max values
   * @return a double with a random generated value between the given numbers
   * @throws ArgumentException an ArgumentException is thrown if the node does 
   *         not contain both elements
   */
  private double getRandom(JsonNode node)
  {
    if( !node.has("min") || !node.has("min") )
      throw new ArgumentException("The JSON object needs a min and a max");
    
    double min = node.get("min").asDouble();
    double max = node.get("max").asDouble();
    this.logger.debug("Min " + min + " Max " +  max);
    double res = (Math.random() * (max - min)) + min;
    this.logger.debug("Returning " + res);
    return res;
  }
  
  /**
   * Determines whether or not the processing time has expired.  If the 
   * 'how-long-mins' field is set with a number greater than zero then it
   * calculates when the process started and the current time.  If the 
   * difference between the two is greater or equal to that value the process 
   * quits.
   *  
   * @return true if and only if the processing time was set and it has expired
   */
  private boolean timeout()
  {
    long time = this.resource.get("how-long-mins").asLong();
    if( time > 0 )
    {
      time = time * 60 * 1000;
      long now = System.currentTimeMillis();
      long diff = now - this.started;
      this.logger.debug("Checking for termination time: " + diff);
      
      if( diff >= time )
      {
        this.logger.info("Time Expired, exiting! ");
        this.controller.set();
        return true;
      }
    }
    return false;
  }
  
  /**
   * It makes sure all the values are set to avoid checking if the fields exists
   * all over the place.  If one of the fields is missing, then is populated 
   * with a default value
   * 
   * @param node the node to set all its defaults
   */
  private void normalizeEntries( JsonNode node)
  {
    this.resource = node.deepCopy();
    
    if( !this.resource.has("how-long-mins") )
      this.resource.put("how-long-mins", -1);
    if( !this.resource.has("max-random-send-secs") )
      this.resource.put("max-random-send-secs", 2);
      
    JsonNode sessions = this.resource.get("sessions");
    ArrayNode nodes = this.mapper.createArrayNode();
    
    if( sessions.isArray() )
    {
      this.logger.debug("Sessions is an array");
      for( JsonNode temp : sessions )
      {
        ObjectNode session = temp.deepCopy();
        if( !session.has("session-name") )
          session.put("session-name", "DEFAULT");
        if( !session.has("cpu") )
        {
          ObjectNode cpu = this.mapper.createObjectNode();
          cpu.put("min", 0);
          cpu.put("max", 100);
          session.set("cpu", cpu);
        }
        if( !session.has("mem") )
        {
          ObjectNode mem = this.mapper.createObjectNode();
          mem.put("min", 1024);
          mem.put("max", 8192);
          session.set("mem", mem);
        }
        if( !session.has("tasks") )
        {
          ObjectNode tasks = this.mapper.createObjectNode();
          tasks.put("min", 0);
          tasks.put("max", 100);
          session.set("tasks", tasks);
        }
        int num = 5;
        if( session.has("nodes") )
          num = session.get("nodes").asInt();
        
        ArrayNode vms = this.mapper.createArrayNode();
        for(int i = 0; i < num; i++ )
          vms.add(UUID.randomUUID().toString());
        session.set("nodes", vms);
        
        nodes.add(session);
      }// end of the for loop
    }// sessions isArray
    else
    {
      this.logger.debug("Sessions is not an array");
      ObjectNode obj = this.mapper.createObjectNode();
      obj.put("session-name", "DEFAULT");
      obj.put("nodes", 10);
      
      ObjectNode cpu = this.mapper.createObjectNode();
      cpu.put("min", 0);
      cpu.put("max", 100);
      obj.set("cpu", cpu);
    
      ObjectNode mem = this.mapper.createObjectNode();
      mem.put("min", 1024);
      mem.put("max", 8192);
      obj.set("mem", mem);
    
      ObjectNode tasks = this.mapper.createObjectNode();
      tasks.put("min", 0);
      tasks.put("max", 100);
      obj.set("tasks", tasks);
      nodes.add(obj);
    }
    
    this.resource.set("sessions", nodes);
    
  }
  
  
  /**
   * Prints a message indicating how to use this framework and then quits
   * 
   * @param msg the message to display on the screen along with the usage
   */
  private static void usage(String msg) 
  {
    if( msg != null )
      System.err.println(msg);
    
    formatter.printHelp(CcdpStatusSender.class.toString(), options);
    System.exit(1);
  }
  
  /**
   * Runs the show...
   * 
   * @param args all the different command line arguments
   * @throws Exception an exception is thrown if the instance cannot send 
   *         status
   */
  public static void main(String[] args) throws Exception
  {
    
    // building all the options available
    String txt = "Path to the configuration file.  This can also be set using "
        + "the System Property 'ccdp.config.file'";
    Option config = new Option("c", "config-file", true, txt);
    config.setRequired(false);
    options.addOption(config);
    
    // The help option
    Option help = new Option("h", "help", false, "Shows this message");
    help.setRequired(false);
    options.addOption(help);

    // the jobs option as a file
    Option res_file = new Option("f", "file", true, 
        "The JSON file with the configuration to use when running");
    res_file.setRequired(true);
    options.addOption(res_file);
    
    
    CommandLineParser parser = new DefaultParser();
    CommandLine cmd;

    try 
    {
      cmd = parser.parse(options, args);
    } 
    catch (ParseException e) 
    {
      System.out.println(e.getMessage());
      formatter.printHelp("utility-name", options);

      System.exit(1);
      return;
    }
    
    // if help is requested, print it an quit
    if( cmd.hasOption('h') )
    {
      formatter.printHelp(CcdpStatusSender.class.toString(), options);
      System.exit(0);
    }
    
    String cfg_file = null;
    String filename = null;
    
    String key = CcdpUtils.CFG_KEY_CFG_FILE;
    
    // do we have a configuration file? if not search for the System Property
    if( cmd.hasOption('c') )
    {
      cfg_file = CcdpUtils.expandVars(cmd.getOptionValue('c'));
    }
    else if( System.getProperty( key ) != null )
    {
      String fname = CcdpUtils.expandVars(System.getProperty(key));
      File cfg = new File( fname );
      if( cfg.isFile() )
        cfg_file = fname;
      else
        usage("The config file (" + fname + ") is invalid");
    }
    
    
    if( cmd.hasOption('f') )
    {
      String fname = cmd.getOptionValue('f');
      File test = new File( fname );
      if( test.isFile() )
        filename = fname;
      else
        usage("The resource file (" + fname + ") is invalid");
    }
    
    
    CcdpUtils.loadProperties(cfg_file);
    CcdpUtils.configLogger();
    
    if( filename != null )
    {
      byte[] data = Files.readAllBytes( Paths.get( filename ) );
      String resource = new String(data, "utf-8");
      new CcdpStatusSender(resource);
    }
    else
      throw new RuntimeException("Was not able to load the resource file");
    
  }
}


