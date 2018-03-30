package com.axios.ccdp.test;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Map;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.log4j.Logger;

import com.axios.ccdp.connections.amq.AmqSender;
import com.axios.ccdp.utils.CcdpUtils;
import com.axios.ccdp.utils.TaskEventIntf;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;


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
  
  private JsonNode resource = null;
  
  /**
   * Instantiates a new Message Sender and performs different operations
   * 
   * @param channel the name of the channel to send the task and/or message
   * @param jobs a JSON file with a job to execute
   * @param task_filename a JSON file with a task to kill
   * @param reply_to the name of a channel to wait for replies
   */
  public CcdpStatusSender( String data )
  {
    this.sender = new AmqSender();
    
    Map<String, String> map = 
        CcdpUtils.getKeysByFilter(CcdpUtils.CFG_KEY_CONN_INTF);
    
    String broker = map.get(CcdpUtils.CFG_KEY_BROKER_CONNECTION);
    String channel = CcdpUtils.getProperty(CcdpUtils.CFG_KEY_MAIN_CHANNEL);
    
    channel = channel.trim();
    
    this.logger.info("Sending Tasking to " + broker + ":" + channel);
    this.sender.connect(broker, channel);


    try
    {
      
      if( data != null )
      {
        this.logger.info("Parsing data: " + data);
        this.resource = this.mapper.readTree( data );
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
  
  private void runMain()
  {
    
  }
  
  @Override
  public void onEvent()
  {
    // TODO Auto-generated method stub
    
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


