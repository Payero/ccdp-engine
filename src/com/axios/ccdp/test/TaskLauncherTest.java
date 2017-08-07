package com.axios.ccdp.test;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.log4j.Logger;

import com.axios.ccdp.connections.intfs.CcdpTaskLauncher;
import com.axios.ccdp.message.ThreadRequestMessage;
import com.axios.ccdp.newgen.CcdpTaskRunner;
import com.axios.ccdp.tasking.CcdpTaskRequest;
import com.axios.ccdp.tasking.CcdpThreadRequest;
import com.axios.ccdp.tasking.CcdpTaskRequest.CcdpTaskState;
import com.axios.ccdp.utils.CcdpUtils;

public class TaskLauncherTest implements CcdpTaskLauncher
{

  /**
   * Generates debug print statements based on the verbosity level.
   */
  private Logger logger = Logger.getLogger(TaskLauncherTest.class.getName());
  /**
   * Parses and prints all the options or arguments used by this application
   */
  private static HelpFormatter formatter = new HelpFormatter();
  /**
   * Stores all the options that can be used by this application
   */
  private static Options options = new Options();
  
  
  public TaskLauncherTest( String content )
  {
    try
    {
      if( content != null )
      {
        content = content.trim();
        //this.logger.debug("Running a Task " + content);
        
        List<CcdpThreadRequest> reqs = CcdpUtils.toCcdpThreadRequest(content);
        for( CcdpThreadRequest req : reqs )
        {
          //this.logger.info("Processing " + req.toString());
          for( CcdpTaskRequest task : req.getTasks() )
          {
            //this.logger.info("Launching " + task.toPrettyPrint());
            this.launchTask(task);
          }
        }
      }
    }
    catch( Exception e )
    {
      e.printStackTrace();
    }
  }
  
  /**
   * Invoked when a task has been launched on this executor (initiated via 
   * SchedulerDriver.launchTasks(OfferID, TaskInfo, Filters). Note that this 
   * task can be realized with a thread, a process, or some simple computation, 
   * however, no other callbacks will be invoked on this executor until this 
   * callback has returned.
   * 
   * @param task describes the task to launch
   * 
   */
  public void launchTask( CcdpTaskRequest task)
  {
    try
    {
      CcdpTaskRunner ccdpTask = new CcdpTaskRunner(task, this);
      
      task.setState(CcdpTaskState.STAGING);
      //this.logger.info("Task " + task.getTaskId() + " set to " + task.getState());
      this.statusUpdate(task, null);
      
      ccdpTask.start();
      
      task.setState(CcdpTaskState.RUNNING);
      //this.logger.info("Task " + task.getTaskId() + " set to " + task.getState());
      this.statusUpdate(task, null);
    }
    catch( Exception e )
    {
      this.logger.error("Message: " + e.getMessage(), e);
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
    
    formatter.printHelp(TaskLauncherTest.class.toString(), options);
    System.exit(1);
  }
  
  /**
   * Sends an update to the ExecutorDriver with the status change provided
   * as an argument.  If the message is not null then is set using the 
   * setMessage() method in the TaskStatus.Builder object
   * 
   * @param task the task to send updates to the main application
   * @param message a message (optional) to be sent back to the ExecutorDriver
   */
  public void statusUpdate(CcdpTaskRequest task, String message)
  {
    StringBuffer buff = new StringBuffer("\n\nStatus Update:\n");
    buff.append("\tTask Id: ");
    buff.append(task.getTaskId());
    buff.append("\n");
    buff.append("\tStatus: ");
    buff.append(task.getState());
    buff.append("\n");
    
    if( message != null )
    {
      buff.append("--------------------------------------------------------\n");
      buff.append(message);
      buff.append("\n--------------------------------------------------------\n");
    }
    this.logger.debug( buff.toString() );
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
    Option job_file = new Option("f", "file", true, 
        "Optional JSON file with the jobs to run");
    job_file.setRequired(false);
    options.addOption(job_file);
    
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
      formatter.printHelp(TaskLauncherTest.class.toString(), options);
      System.exit(0);
    }
    
    String cfg_file = null;
    String job = null;
        
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
      job = cmd.getOptionValue('f');
    }
    
    if( job == null )
      usage("Need to provide either a job file");
    
    CcdpUtils.loadProperties(cfg_file);
    CcdpUtils.configLogger();
    
    byte[] data = Files.readAllBytes( Paths.get( job ) );
    String content = new String(data, "utf-8");
    new TaskLauncherTest(content);
    
  }
}
