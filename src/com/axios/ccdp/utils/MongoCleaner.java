package com.axios.ccdp.utils;

import org.apache.log4j.Logger;
import java.io.File;
import java.net.URL;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;

import com.axios.ccdp.fmwk.CcdpAgent;
import com.axios.ccdp.impl.db.mongo.CcdpMongoDbImpl;
import com.axios.ccdp.resources.CcdpResourceAbs;
import com.axios.ccdp.resources.CcdpVMResource;
import com.axios.ccdp.utils.CcdpUtils;
import com.fasterxml.jackson.databind.JsonNode;


public class MongoCleaner
{

  /**
   * Generates debug print statements based on the verbosity level.
   */
  private Logger logger = Logger.getLogger(MongoCleaner.class.getName());
  /**
   * Parses and prints all the options or arguments used by this application
   */
  private static HelpFormatter helpFormatter = new HelpFormatter();
  /**
   * Stores all the options that can be used by this application
   */
  private static Options options = new Options();
  
  /**
   * Removes all MongoDB entries
   * @throws InterruptedException throws exception if wait for heartbeat is interrupted
   */
  
  public MongoCleaner() throws InterruptedException
  {
    this.logger.debug("Cleaning MongoDB Records");
    CcdpMongoDbImpl dbConnection = null;
    
    // Add ccdp util checks to make sure everything is connected properly
    JsonNode db_node = CcdpUtils.getDatabaseIntfCfg();
    
    // Make new impl, configure, connect
    dbConnection = new CcdpMongoDbImpl();
    dbConnection.configure(db_node);
    
    if ( dbConnection.connect() ) 
     this.logger.debug("Connection Successful");
    
    else
    {
      this.logger.debug("Unable to connect to MongoDB Database");
      System.exit(1);
    }
    
    // Now that we are connected, get all VM entries, iterate through, and delete
    List<CcdpResourceAbs> vms = dbConnection.getAllInformation();
    if (vms.size() == 0)
    {
      this.logger.info("There are no VMs in MongoDB");
      System.exit(0);
    }
    for( CcdpResourceAbs vm :vms )
    {
      if ( vm.getIsServerless() )
      {
        String cont_type = vm.getNodeType();
        this.logger.debug("Deleting controller " + cont_type);
        dbConnection.deleteServerlessInformation(cont_type);
      }
      else
      {
        CcdpVMResource vmID = (CcdpVMResource) vm;
        String iid = vmID.getInstanceId();
        this.logger.debug("Deleting VM " + iid);
        dbConnection.deleteVMInformation(iid);
      }
    }
 
    // Wait for a potential heartbeat
    
    TimeUnit.MILLISECONDS.sleep(5000);
    // Check for remaining vms
    vms = dbConnection.getAllInformation();
    if (vms.size() == 0)
      this.logger.info("All VMs were successfully removed from MongoDB");
    else
    {
      this.logger.info("Not all VMs could be deleted. The following may still be running:");
      for (CcdpResourceAbs vm : vms)
      {
        if ( vm.getIsServerless() )
        {
          String cont_type = vm.getNodeType();
          this.logger.debug("Controller: " + cont_type);
        }
        else
        {
          CcdpVMResource vmID = (CcdpVMResource) vm;
          String iid = vmID.getInstanceId();
          this.logger.debug("VM: " + iid);
        }
      }
    }
    
    if (dbConnection != null)
    {
      try
      {
        dbConnection.disconnect();
      }
      catch(Exception e)
      {
        System.out.println("Error closing connection: " + e);
      }
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

    helpFormatter.printHelp(MongoCleaner.class.toString(), options);
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
      helpFormatter.printHelp("utility-name", options);

      System.exit(1);
      return;
    }
        
    // if help is requested, print it an quit
    if( cmd.hasOption('h') )
    {
      helpFormatter.printHelp(CcdpAgent.class.toString(), options);
      System.exit(0);
    }
    String cfg_file = null;
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

    // If it was not specified, let's try as part of the classpath using the
    // default name stored in CcdpUtils.CFG_FILENAME
    if( cfg_file == null )
    {
      String name = CcdpUtils.CFG_FILENAME;
      URL urlCfg = CcdpUtils.class.getClassLoader().getResource(name);

      // making sure it was found
      if( urlCfg != null )
      {
        System.out.println("Configuring CCDP using URL: " + urlCfg);
        CcdpUtils.loadProperties( urlCfg.openStream() );
      }
      else
      {
        System.err.println("Could not find " + name + " file");
        usage("The configuration is null, but it is required");
      }
    }

    // Uses the cfg file to configure all CcdpUtils for use in the next service
    CcdpUtils.loadProperties(cfg_file);
    CcdpUtils.configLogger();
    
    new MongoCleaner();
  }
}
