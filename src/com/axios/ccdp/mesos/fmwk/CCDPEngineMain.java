package com.axios.ccdp.mesos.fmwk;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.log4j.Logger;
import org.apache.mesos.MesosSchedulerDriver;
import org.apache.mesos.Protos.CommandInfo;
import org.apache.mesos.Protos.Credential;
import org.apache.mesos.Protos.ExecutorID;
import org.apache.mesos.Protos.ExecutorInfo;
import org.apache.mesos.Protos.FrameworkInfo;
import org.apache.mesos.Protos.Status;
import org.apache.mesos.Scheduler;

import com.axios.ccdp.mesos.tasking.CcdpThreadRequest;
import com.axios.ccdp.mesos.utils.CcdpUtils;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;

/**
 * Main class to run a Mesos Framework.  It is used for testing and learning
 * 
 * @author Oscar E. Ganteaume
 *
 */
public class CCDPEngineMain 
{
  /**
   * Displays all the debug messages to the screen
   */
  private Logger logger = Logger.getLogger(CCDPEngineMain.class.getName());
  /**
   * Parses and prints all the options or arguments used by this application
   */
  private static HelpFormatter formatter = new HelpFormatter();
  /**
   * Stores all the options that can be used by this application
   */
  private static Options options = new Options();
  
  /**
   * Instantiates a new object which will deployed a custom executor.  The 
   * executor would connect to the Mesos Master and will launch all the jobs
   * specified in the json_file
   * 
   * @param json_file contains all the jobs to execute
   * 
   * @throws Exception an Exception is thrown if there is a problem executing
   *         the tasks
   */
  public CCDPEngineMain(String json_file ) throws Exception
  {
    this.logger.debug("Running JSON Remote Main");
    String root = System.getenv("CCDP_HOME");
    if( root == null )
    {
      this.logger.debug("The CCDP_HOME was not set, trying Property");
      root = CcdpUtils.getProperty(CcdpUtils.CFG_KEY_FMWK_ROOT);
    }
    // if we don't have a path then quit
    if( root == null )
    {
      String msg = "Please set the running environment. Need to either set " +
               " the CCDP_HOME environment variable or set the path of the " +
               " installation using the System Property: '" + 
               CcdpUtils.CFG_KEY_FMWK_ROOT + "'";
      
      CCDPEngineMain.usage(msg);
    }
    String jar_file = CcdpUtils.getProperty(CcdpUtils.CFG_KEY_EXEC_JAR);
    
    File obj = new File(jar_file);
    if( !obj.isFile() )
      CCDPEngineMain.usage("Could not find jar file: " + jar_file);
    
    CommandInfo.URI uri = CommandInfo.URI.newBuilder()
                  .setValue(jar_file)
                  .setExtract(false)
                  .build();

    String cmdExec = "java -cp " + jar_file + 
                     " com.axios.ccdp.mesos.fmwk.CcdpCommandExecutor";
    
    CommandInfo commandInfoJson = CommandInfo.newBuilder()
                  .setValue(cmdExec)
                  .addUris(uri)
                  .build();

    this.logger.info("Running Command Executor using: " + cmdExec);
    // The object that will actually execute the command
    ExecutorInfo ccdpExec = ExecutorInfo.newBuilder()
        .setExecutorId(ExecutorID.newBuilder()
        .setValue("CcdpCommandExecutor"))
        .setCommand(commandInfoJson)
        .setName("CCDP Remote Command Executor").build();

    this.logger.info("Executor ID = " + ccdpExec.getExecutorId().getValue() );
    FrameworkInfo.Builder frameworkBuilder = FrameworkInfo.newBuilder()
        .setFailoverTimeout(120000)
        .setUser("") // Have Mesos fill in the current user
        .setName("CCDP Remote Command Exec");

    if (System.getenv("MESOS_CHECKPOINT") != null) 
    {
      this.logger.info("Enabling checkpoint for the framework");
      frameworkBuilder.setCheckpoint(true);
    }
    
    List<CcdpThreadRequest> requests = new ArrayList<CcdpThreadRequest>();
    boolean test = false;
    List<CcdpJob> jobs = new ArrayList<CcdpJob>();
    
    if( json_file != null )
    {
      File json_jobs = new File(json_file);
      this.logger.debug("Loading File: " + json_file);
      
      
      // loading Jobs from the command line
      if( json_jobs.isFile() )
      {
        requests = CcdpUtils.toCcdpThreadRequest( json_jobs );
        this.logger.debug("Number of Jobs: " + requests.size() );
        
        if( test )
        {
          byte[] data = Files.readAllBytes( Paths.get( json_file ) );
          JsonNode node = new ObjectMapper().readTree( data );
          if( node.has("jobs") )
          {
            ArrayNode jobs_node = (ArrayNode)node.get("jobs");
            for( JsonNode job : jobs_node )
            {
              JsonNode copy = job.deepCopy();
              this.logger.debug("Adding Job: " + copy.toString());
              jobs.add(CcdpJob.fromJSON(copy));
            }
          }
        }
      }
      else
      {
        String msg = "The Json Jobs file (" + json_file + ") is invalid";
        this.logger.error(msg);
        CCDPEngineMain.usage(msg);
      }
    }
    
    Scheduler scheduler = new CcdpRemoteScheduler( ccdpExec, requests );
    
    if( test )
    {
     
      scheduler = new SimpleRemoteScheduler( ccdpExec, jobs );
      this.logger.warn("\n\nUSING THE SIMPLE REMOTE SCHEDULER");
      this.logger.warn("USING THE SIMPLE REMOTE SCHEDULER");
      this.logger.warn("USING THE SIMPLE REMOTE SCHEDULER");
      this.logger.warn("USING THE SIMPLE REMOTE SCHEDULER\n\n");
      
    }
    
    // Running Mesos Specific stuff
    MesosSchedulerDriver driver = null;
    String master = CcdpUtils.getProperty(CcdpUtils.CFG_KEY_MESOS_MASTER_URI);
    this.logger.info("Master  URI: " + master);
    
    if (System.getenv("MESOS_AUTHENTICATE") != null) 
    {
      this.logger.info("Enabling authentication for the framework");

      if (System.getenv("DEFAULT_PRINCIPAL") == null) 
      {
        this.logger.error("Expecting authentication principal in the environment");
        System.exit(1);
      }

      if (System.getenv("DEFAULT_SECRET") == null) 
      {
        this.logger.error("Expecting authentication secret in the environment");
        System.exit(1);
      }
      Credential.Builder bldr = Credential.newBuilder();
      bldr.setPrincipal( System.getenv("DEFAULT_PRINCIPAL") );
      bldr.setSecret(System.getenv("DEFAULT_SECRET"));
      Credential credential = bldr.build();

      frameworkBuilder.setPrincipal(System.getenv("DEFAULT_PRINCIPAL"));
      FrameworkInfo fmwk = frameworkBuilder.build();
      driver = new MesosSchedulerDriver(scheduler, fmwk, master, credential);
    } 
    else 
    {
      this.logger.debug("Skippig Credentials");
      frameworkBuilder.setPrincipal("ccdp-framework");
      FrameworkInfo fmwk = frameworkBuilder.build();
      driver = new MesosSchedulerDriver(scheduler, fmwk, master);
    }

    int status = driver.run() == Status.DRIVER_STOPPED ? 0 : 1;

    // Ensure that the driver process terminates.
    driver.stop();

    System.exit(status);
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
    
    formatter.printHelp(CCDPEngineMain.class.toString(), options);
    System.exit(1);
  }

  /**
   * Runs the CCDP Framework using the provided configuration file.  The 
   * configuration file can be passed either as an argument or using the Java
   * System Property 'ccdp.config.file'.  This property is defined in the 
   * CcdpUtils.KEY_CFG_FILE variable.  The configuration file is required and 
   * must be provided either way.  
   * 
   * In addition to the configuration file, this class can be launched with an 
   * additional file containing jobs to be executed after initialization. 
   * 
   * Running the class with -h produces the following message:
   * 
   * usage: class com.axios.ccdp.mesos.fmwk.CCDPEngineMain
   * -c,--config-file <arg>   Path to the configuration file.  This can also
   *                           be set using the System Property
   *                           'ccdp.config.file'
   *  -h,--help                Shows this message
   *  -j,--jobs <arg>          Optional JSON file with the jobs to run
   * 
   * @param args the command line arguments
   * @throws Exception throws an Exception if the class has problems parsing 
   *         the command line arguments
   */
  public static void main(String[] args) throws Exception 
  {
    // building all the options available
    String txt = "Path to the configuration file.  This can also be set using "
        + "the System Property 'ccdp.config.file'";
    Option config = new Option("c", "config-file", true, txt);
    config.setRequired(false);
    options.addOption(config);

    Option jobs = new Option("j", "jobs", true, 
        "Optional JSON file with the jobs to run");
    jobs.setRequired(false);
    options.addOption(jobs);
    
    Option help = new Option("h", "help", false, "Shows this message");
    help.setRequired(false);
    options.addOption(help);

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
      formatter.printHelp(CCDPEngineMain.class.toString(), options);
      System.exit(0);
    }
    String cfg_file = null;
    String jobs_file = null;
    String key = CcdpUtils.CFG_KEY_CFG_FILE;
    
    // do we have a configuration file? if not search for the System Property
    if( cmd.hasOption('c') )
    {
      cfg_file = cmd.getOptionValue('c');
    }
    else if( System.getProperty( key ) != null )
    {
      String fname = CcdpUtils.expandVars(System.getProperty(key));
      File file = new File( fname );
      if( file.isFile() )
        cfg_file = fname;
      else
        usage("The config file (" + fname + ") is invalid");
    }
    
    // do we have a valid job file?
    if( cmd.hasOption('j') )
    {
      String fname = CcdpUtils.expandVars( cmd.getOptionValue('j') );
      File file = new File( fname );
      if( file.isFile() )
        jobs_file = fname;
      else
        usage("The jobs file (" + fname + ") was provided, but is invalid");
    }
    
    if( cfg_file == null )
      usage("The configuration is null, but it is required");
    
    CcdpUtils.loadProperties(cfg_file);
    CcdpUtils.configLogger();
    
    new CCDPEngineMain(jobs_file);
  }
}
