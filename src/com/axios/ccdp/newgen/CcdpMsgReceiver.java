package com.axios.ccdp.newgen;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Map;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.log4j.Logger;

import com.axios.ccdp.connections.intfs.CcdpMessageConsumerIntf;
import com.axios.ccdp.message.CcdpMessage;
import com.axios.ccdp.message.KillTaskMessage;
import com.axios.ccdp.message.ResourceUpdateMessage;
import com.axios.ccdp.message.TaskUpdateMessage;
import com.axios.ccdp.message.ThreadRequestMessage;
import com.axios.ccdp.message.CcdpMessage.CcdpMessageType;
import com.axios.ccdp.utils.CcdpUtils;


public class CcdpMsgReceiver implements CcdpMessageConsumerIntf
{
  /**
   * Generates debug print statements based on the verbosity level.
   */
  private Logger logger = Logger.getLogger(CcdpMsgReceiver.class.getName());
  /**
   * Parses and prints all the options or arguments used by this application
   */
  private static HelpFormatter formatter = new HelpFormatter();
  /**
   * Stores all the options that can be used by this application
   */
  private static Options options = new Options();
  
  /**
   * Receives JMS Tasking Messages to the framework
   */
  private AmqReceiver receiver;
  /**
   * Writes the events to the file
   */
  private BufferedWriter writer = null;
  
  public CcdpMsgReceiver( String broker, String channel, String outfile ) 
  {
    this.logger.debug("Receiving Messages ");
    this.logger.debug("*******************************************************");
    this.logger.debug("*********************   Config   **********************");
    this.logger.debug("Broker: "   + broker);
    this.logger.debug("Channel: "  + channel);
    this.logger.debug("Out File: " + outfile);
    this.logger.debug("*******************************************************");
    this.logger.debug("*******************************************************");
    
    this.receiver = new AmqReceiver(this);
    
    if( broker == null || channel == null )
      usage("Need to configure the broker and the channel properly");
    
    if( outfile != null )
    {
      this.logger.debug("Saving events in " + outfile);
      try
      {
        FileWriter fw = new FileWriter(outfile);
        this.writer = new BufferedWriter(fw);
      }
      catch( IOException e )
      {
        
      }
    }
    this.logger.info("Sending Tasking to " + broker + ":" + channel);
    this.receiver.connect(broker, channel);
  }  
  
  public void onCcdpMessage( CcdpMessage message )
  {
    
    CcdpMessageType msgType = CcdpMessageType.get( message.getMessageType() );
    this.logger.debug("Got a new Message: " + message.toString());
    String msg = null;
    switch( msgType )
    {
      case RESOURCE_UPDATE:
        ResourceUpdateMessage resMsg = (ResourceUpdateMessage)message;
        msg = resMsg.getCcdpVMResource().toPrettyPrint();
        this.logger.info("Got a ResourceUpdateMessage for " + msg);
        break;
      case KILL_TASK:
        KillTaskMessage killMsg = (KillTaskMessage)message;
        msg = killMsg.getTask().toPrettyPrint();
        this.logger.info("Got a KillTaskMessage for " + msg);
        break;
      case TASK_UPDATE:
        TaskUpdateMessage taskMsg = (TaskUpdateMessage)message;
        msg = taskMsg.getTask().toPrettyPrint();
        this.logger.info("Got a TaskUpdateMessage for " + msg);
        break;
      case THREAD_REQUEST:
        ThreadRequestMessage reqMsg = (ThreadRequestMessage)message;
        msg = reqMsg.getRequest().toPrettyPrint();
        this.logger.info("Got a ThreadRequestTaskMessage for " + msg);
        break;
      case UNDEFINED:
      default:
        msg = "Cannot process events of type " + msgType;
        this.logger.warn(msg);
    }
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
    
    formatter.printHelp(CcdpMsgReceiver.class.toString(), options);
    System.exit(1);
  }
  
  
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
    Option outfile = new Option("f", "file", true, 
        "Optional JSON file with the jobs to run");
    outfile.setRequired(false);
    options.addOption(outfile);
    
    // the jobs option as a file
    Option brk_opt = new Option("b", "broker", true, 
        "The broker to use to listen for incoming events");
    brk_opt.setRequired(false);
    options.addOption(brk_opt);
    
    // the jobs option as a file
    Option dest = new Option("d", "destination", true, 
        "The topic or queue to subscribe");
    dest.setRequired(false);
    options.addOption(dest);
    
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
      formatter.printHelp(CcdpMsgReceiver.class.toString(), options);
      System.exit(0);
    }
    
    String cfg_file = null;
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
    
    CcdpUtils.loadProperties(cfg_file);
    CcdpUtils.configLogger();
    
    
    Map<String, String> map = CcdpUtils.getKeysByFilter("taskingIntf");
    String broker = map.get(CcdpUtils.CFG_KEY_BROKER_CONNECTION);
    String channel = null;  
    
    if( cmd.hasOption('b') )
      broker = cmd.getOptionValue('b');
    
    if( cmd.hasOption('d') )
      channel = cmd.getOptionValue('d');
    else
    {
      channel = CcdpUtils.getProperty(CcdpUtils.CFG_KEY_RESPONSE_CHANNEL);
      if( channel == null || channel.length() == 0 )
        usage("The destination or channel is required");
    }
    new CcdpMsgReceiver( broker, channel, cmd.getOptionValue('f'));
    
  }
}

