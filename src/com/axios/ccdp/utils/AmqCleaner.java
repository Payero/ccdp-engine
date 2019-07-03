package com.axios.ccdp.utils;

import java.io.File;
import java.net.URL;
import java.util.Set;
import java.util.StringTokenizer;

import javax.jms.JMSException;

import org.apache.activemq.ActiveMQConnection;
import org.apache.activemq.ActiveMQConnectionFactory;
import org.apache.activemq.advisory.DestinationSource;
import org.apache.activemq.command.ActiveMQQueue;
import org.apache.activemq.command.ActiveMQTopic;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.log4j.Logger;

import com.axios.ccdp.fmwk.CcdpAgent;
import com.axios.ccdp.utils.CcdpUtils;


public class AmqCleaner 
{
  /**
   * Generates debug print statements based on the verbosity level.
   */
  private Logger logger = Logger.getLogger(AmqCleaner.class.getName());
  /**
   * Parses and prints all the options or arguments used by this application
   */
  private static HelpFormatter helpFormatter = new HelpFormatter();
  /**
   * Stores all the options that can be used by this application
   */
  private static Options options = new Options();
  
  /**
   * Removes all the queues and topics from ActiveMQ.  Both, queues and topics,
   * should be a comma delimited list of names to delete.  To remove all the
   * topics or queues then the word ALL should be used (case insensitive).
   * 
   * @param url the URL where the ActiveMQ server is listening for connections
   * @param queues a comma delimited list of queues to delete or 'ALL' to 
   *               remove all of them (case insensitive)
   * @param topics a comma delimited list of topics to delete or 'ALL' to 
   *               remove all of them (case insensitive)
   */
  public AmqCleaner(String url, String queues, String topics)
  {
    this.logger.debug("Cleaning ActiveMQ Topics and Queues");
    ActiveMQConnection connection = null;
    
    if( url == null )
      throw new RuntimeException("The URL cannot be null");
    
    try
    {
      ActiveMQConnectionFactory factory = new 
          ActiveMQConnectionFactory(url);
      connection = (ActiveMQConnection)factory.createConnection();
      connection.start();
      if( queues != null )
        this.destroyQueues( connection, queues );
      if( topics != null )
        this.destroyTopics( connection, topics );
      
    }
    catch( Exception e )
    {
      this.logger.error("Message: " + e.getMessage(), e);
    }
    finally 
    {
     if (connection != null) 
     {
        try 
        {
          connection.close();
        } 
        catch (JMSException e) 
        {
           System.out.println("Error closing connection" + e);
        }
     }
   }
    
  }
  
  /**
   * Removes all the queues listed in the comma delimited string queues or 
   * if the queues is set to ALL (case insensitive) it removes them all
   * 
   * @param connection the active connection to the ActiveMQ server
   * @param queues a comma delimited list of queues to delete or 'ALL' to 
   *               remove all of them (case insensitive)
   */
  private void destroyQueues( ActiveMQConnection connection, String queues)
  {
    this.logger.debug("Removing queues: " + queues );
    try
    {
      if( queues.toUpperCase().equals("ALL") )
      {
        this.logger.info("Removing all Queues");
        DestinationSource ds = connection.getDestinationSource();
        Set<ActiveMQQueue> amq_queues = ds.getQueues();
        for( ActiveMQQueue queue : amq_queues )
        {
          try 
          {
            this.logger.debug("Removing: " + queue.getQueueName() );
            connection.destroyDestination(queue);
          }
          catch( JMSException je )
          {
            this.logger.warn("Problem removing queue " + je.getMessage() );
            continue;
          }
        }
      }
      else
      {
        StringTokenizer st = new StringTokenizer(queues, ",");
        while( st.hasMoreElements() )
        {
          try
          {
            String name = st.nextToken();
            connection.destroyDestination( new ActiveMQQueue( name ) );
          }
          catch( JMSException je)
          {
            this.logger.warn("Problem removing queue " + je.getMessage() );
            continue;
          }
        }
      }
    }
    catch(Exception e)
    {
      e.printStackTrace();
    }
  }
  
  /**
   * Removes all the topics listed in the comma delimited string 'topics' or 
   * if the topics is set to ALL (case insensitive) it removes them all
   * 
   * @param connection the active connection to the ActiveMQ server
   * @param topics a comma delimited list of topics to delete or 'ALL' to 
   *               remove all of them (case insensitive)
   */
  private void destroyTopics( ActiveMQConnection connection, String topics)
  {
    this.logger.debug("Removing Topics: " + topics );
    
    try
    {
      if( topics.toUpperCase().equals("ALL") )
      {
        this.logger.info("Removing all Topics");
        DestinationSource ds = connection.getDestinationSource();
        Set<ActiveMQTopic> amq_topics = ds.getTopics();
        for( ActiveMQTopic topic : amq_topics )
        {
          try 
          {
            this.logger.debug("Removing: " + topic.getTopicName() );
            connection.destroyDestination(topic);
          }
          catch( JMSException je )
          {
            this.logger.warn("Problem removing topic " + je.getMessage() );
            continue;
          }
        }
      }
      else
      {
        StringTokenizer st = new StringTokenizer(topics, ",");
        while( st.hasMoreElements() )
        {
          try
          {
            String name = st.nextToken();
            connection.destroyDestination( new ActiveMQTopic( name ) );
          }
          catch( JMSException je)
          {
            this.logger.warn("Problem removing topic " + je.getMessage() );
            continue;
          }
        }
      }
    }
    catch(Exception e)
    {
      e.printStackTrace();
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

    helpFormatter.printHelp(AmqCleaner.class.toString(), options);
    System.exit(1);
  }
  

  public static void main( String[] args ) throws Exception
  {
    // building all the options available
    String txt = "Path to the configuration file.  This can also be set using "
        + "the System Property 'ccdp.config.file'";
    Option config = new Option("c", "config-file", true, txt);
    config.setRequired(false);
    options.addOption(config);

    Option urlOption = new Option("u", "amq-url", true,
        "The ActiveMQ URL to connect");
    urlOption.setRequired(false);
    options.addOption(urlOption);
    
    Option queuesOption = new Option("q", "queue-names", true,
        "A comma delimited with all the name of the queues to delete or ALL to remove them all");
    queuesOption.setRequired(false);
    options.addOption(queuesOption);
    
    Option topicsOption = new Option("t", "topic-names", true,
        "A comma delimited with all the name of the topics to delete or ALL to remove them all");
    topicsOption.setRequired(false);
    options.addOption(topicsOption);
    
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
    
    String url = "tcp://localhost:61616";
    String queues = null;
    String topics = null;
    
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
    
    
    // do we have an URL?
    if( cmd.hasOption('u') )
      url = cmd.getOptionValue('u');

    // do we have queues to remove?
    if( cmd.hasOption('q') )
      queues = cmd.getOptionValue('q');

    // do we have topics to remove?
    if( cmd.hasOption('t') )
      topics = cmd.getOptionValue('t');

    // Uses the cfg file to configure all CcdpUtils for use in the next service
    CcdpUtils.loadProperties(cfg_file);
    CcdpUtils.configLogger();
    
    new AmqCleaner(url, queues, topics);
  }

}



