package com.axios.ccdp.mesos.fmwk;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import org.apache.log4j.Logger;
import org.apache.mesos.MesosSchedulerDriver;
import org.apache.mesos.Protos.CommandInfo;
import org.apache.mesos.Protos.Credential;
import org.apache.mesos.Protos.ExecutorID;
import org.apache.mesos.Protos.ExecutorInfo;
import org.apache.mesos.Protos.FrameworkInfo;
import org.apache.mesos.Protos.Status;
import org.apache.mesos.Scheduler;
import org.json.JSONArray;
import org.json.JSONObject;

import com.axios.ccdp.mesos.utils.CcdpUtils;

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
      root = CcdpUtils.getProperty(CcdpUtils.KEY_FMWK_ROOT);
    }
    // if we don't have a path then quit
    if( root == null )
    {
      CCDPEngineMain.usage("Please set the running environment");
    }
    String jar_file = CcdpUtils.getProperty(CcdpUtils.KEY_EXEC_JAR);
    
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
        .setUser("") // Have Mesos fill in
        // the current user.
        .setName("CCDP Remote Command Exec");

    if (System.getenv("MESOS_CHECKPOINT") != null) 
    {
      this.logger.info("Enabling checkpoint for the framework");
      frameworkBuilder.setCheckpoint(true);
    }

    List<Job> jobs = new ArrayList<Job>();
    
    File json_jobs = new File(json_file);
    this.logger.debug("Loading File: " + json_file);
    
    // loading Jobs from the command line
    if( json_jobs.isFile() )
    {
      this.logger.debug("Is a valid file");
      byte[] data = Files.readAllBytes( Paths.get( json_file ) );
      JSONObject cfg = new JSONObject( new String( data, "UTF-8") );
      JSONArray jobsArray = cfg.getJSONArray("jobs");
      
      for( int i=0; i < jobsArray.length(); i++ )
      {
        jobs.add( Job.fromJSON(jobsArray.getJSONObject(i) ) );
      }
      this.logger.debug("Jobs:\n" + jobs );
    }
    else
    {
      String msg = "The Json Jobs file (" + json_file + ") is invalid";
      this.logger.error(msg);
      CCDPEngineMain.usage(msg);
    }

    Scheduler scheduler = new CcdpRemoteScheduler( ccdpExec, jobs );
    // Running Mesos Specific stuff
    MesosSchedulerDriver driver = null;
    String master = CcdpUtils.getProperty(CcdpUtils.KEY_MESOS_MASTER_URI);
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
    String name = CcdpRemoteScheduler.class.getName();
    if( msg != null )
      System.err.println(msg);
    System.err.println("Usage: " + name + " json_jobs_filename [config file]");
    System.err.println("The configuration file can be either pass as an ");
    System.err.println("argument or it can be passed as a property using the ");
    System.err.println("key 'ccdp.config.file'");
    
    System.exit(1);
  }

  /**
   * Runs the show, it requires the URL for the Mesos Master and a json file
   * containing the jobs to run
   * 
   * @param args
   * @throws Exception
   */
  public static void main(String[] args) throws Exception 
  {
    if (args.length < 1 || args.length > 2) 
    {
      usage("Wrong number of arguments");
      
    }
    String cfg_file = null;
    String jobs_file = null;
    
    int sz = args.length;
    String key = CcdpUtils.KEY_CFG_FILE;
    
    switch( sz )
    {
      case 0:
        usage("The jobs file is required");
        break;
      case 1:
        if( System.getProperty( key ) != null )
        {
          String fname = CcdpUtils.expandVars(System.getProperty(key));
          File file = new File( fname );
          if( file.isFile() )
          {
            cfg_file = fname;
            jobs_file = args[0];
          } 
          else
            usage("The config file (" + fname + ") is invalid");
        }
        break;
      case 2:
        File file = new File( args[1] );
        if( file.isFile() )
        {
         cfg_file = args[1];
         jobs_file = args[0];
        }
        else
          usage("The config file (" + args[1] + ") is invalid");
        break;
      default:
        usage(null);
    }// end of switch statement
    
    CcdpUtils.loadProperties(cfg_file);
    CcdpUtils.configLogger();
    
    new CCDPEngineMain(jobs_file);
  }
}
