package com.axios.ccdp.test;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
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
import com.axios.ccdp.message.KillTaskMessage;
import com.axios.ccdp.message.StartSessionMessage;
import com.axios.ccdp.message.ThreadRequestMessage;
import com.axios.ccdp.tasking.CcdpTaskRequest;
import com.axios.ccdp.tasking.CcdpThreadRequest;
import com.axios.ccdp.utils.CcdpUtils;


public class CcdpMsgSender
{

  /**
   * Generates debug print statements based on the verbosity level.
   */
  private Logger logger = Logger.getLogger(CcdpMsgSender.class.getName());
  /**
   * Parses and prints all the options or arguments used by this application
   */
  private static HelpFormatter formatter = new HelpFormatter();
  /**
   * Stores all the options that can be used by this application
   */
  private static Options options = new Options();
  
  /**
   * Sends JMS Tasking Messages to the framework
   */
  private AmqSender sender = null;
  
  public CcdpMsgSender( String channel, String jobs, String task )
  {
    this.sender = new AmqSender();
    
    Map<String, String> map = 
        CcdpUtils.getKeysByFilter(CcdpUtils.CFG_KEY_CONN_INTF);
    
    String broker = map.get(CcdpUtils.CFG_KEY_BROKER_CONNECTION);
    if( channel == null )
      channel = CcdpUtils.getProperty(CcdpUtils.CFG_KEY_MAIN_CHANNEL);
    
    channel = channel.trim();
    
    this.logger.info("Sending Tasking to " + broker + ":" + channel);
    this.sender.connect(broker, channel);
    
    try
    {
      if( jobs != null )
      {
        jobs = jobs.trim();
        this.logger.debug("Running a Task sender, sending " + jobs);
        
        List<CcdpThreadRequest> reqs = CcdpUtils.toCcdpThreadRequest(jobs);
        for( CcdpThreadRequest req : reqs )
        {
          this.logger.info("Sending " + req.toPrettyPrint());
          ThreadRequestMessage msg = new ThreadRequestMessage();
          msg.setRequest(req);
          this.sender.sendMessage(null, msg);
          this.logger.debug("Message sent");
        }
      }
      
      if( task != null )
      {
        this.logger.info("Sending KillTaskMessage: " + task);
        KillTaskMessage msg = new KillTaskMessage();
        msg.setHowMany(1);
        CcdpTaskRequest task_req = new CcdpTaskRequest();
        task_req.setTaskId(null);
        task_req.setName(task);
        msg.setTask(task_req);
        this.sender.sendMessage(null, msg);
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
   * Prints a message indicating how to use this framework and then quits
   * 
   * @param msg the message to display on the screen along with the usage
   */
  private static void usage(String msg) 
  {
    if( msg != null )
      System.err.println(msg);
    
    formatter.printHelp(CcdpMsgSender.class.toString(), options);
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
    Option jobs_file = new Option("f", "file", true, 
        "Optional JSON file with the jobs to run");
    jobs_file.setRequired(false);
    options.addOption(jobs_file);
    
    // the jobs option as a string
    Option jobs_string = new Option("j", "jobs", true, 
        "Optional JSON file with the jobs to run passed as a string");
    jobs_string.setRequired(false);
    options.addOption(jobs_string);
    
    // the destination to send the jobs
    Option dest_string = new Option("d", "destination", true, 
        "The name of the queue to send the jobs");
    dest_string.setRequired(false);
    options.addOption(dest_string);
    

    // an optional task to kill
    Option kill_task = new Option("t", "kill-task", true, 
        "Optional task to kill");
    kill_task.setRequired(false);
    options.addOption(kill_task);
    
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
      formatter.printHelp(CcdpMsgSender.class.toString(), options);
      System.exit(0);
    }
    
    String cfg_file = null;
    String filename = null;
    String jobs = null;
    String dest = null;
    String task = null;
        
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
    
    if( cmd.hasOption('j') )
    {
      jobs = cmd.getOptionValue('j');
    }
    
    if( cmd.hasOption('d') )
    {
      dest = cmd.getOptionValue('d');
    }
    
    if( cmd.hasOption('f') )
    {
      String fname = cmd.getOptionValue('f');
      File test = new File( fname );
      if( test.isFile() )
        filename = fname;
      else
        usage("The jobs file (" + fname + ") is invalid");
    }
    
    if( cmd.hasOption('t') )
    {
      task = cmd.getOptionValue('t');
    }
    if( filename == null && jobs == null  && task == null)
      usage("Need to provide either a job file, a job, or a task as argument");
    
    CcdpUtils.loadProperties(cfg_file);
    CcdpUtils.configLogger();
    
    if( filename != null )
    {
      byte[] data = Files.readAllBytes( Paths.get( filename ) );
      String job = new String(data, "utf-8");
      new CcdpMsgSender(dest, job, task);
    }
    
    new CcdpMsgSender(dest, jobs, task);
    
  }
}


