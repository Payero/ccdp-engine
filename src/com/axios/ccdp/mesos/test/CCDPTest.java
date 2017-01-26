package com.axios.ccdp.mesos.test;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
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

import com.axios.ccdp.mesos.tasking.CcdpThreadRequest;
import com.axios.ccdp.mesos.utils.CcdpUtils;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.reflect.TypeToken;

public class CCDPTest
{

  /**
   * Generates debug print statements based on the verbosity level.
   */
  private Logger logger = Logger.getLogger(CCDPTest.class.getName());

  
  public CCDPTest() throws Exception
  {
    this.logger.debug("Running CCDP Test");
    
    String fname = "/home/oeg/dev/CCDP/data/pi_estimation.json";
    fname = "/home/oeg/dev/CCDP/data/single_task.json";
    fname = "/home/oeg/dev/CCDP/data/jobs.json";
    fname = "/home/oeg/dev/CCDP/data/multiple_tasks.json";
    
    
    byte[] data = Files.readAllBytes( Paths.get( fname ) );
//    JsonParser parser = new JsonParser();
//    JsonObject tst = parser.parse(new String( data, "UTF-8")).getAsJsonObject();
//    this.logger.debug("Test: " + tst.toString());
    
    //    
    String jobs = new String( data, "UTF-8");
    this.logger.debug("The String: " + jobs);
    List<CcdpThreadRequest> reqs = 
        CcdpUtils.toCcdpThreadRequest(jobs);
    Iterator<CcdpThreadRequest> threads = reqs.iterator();
    this.logger.debug("Jobs: " + reqs.size());
    while( threads.hasNext() )
    {
      this.logger.debug(threads.next().toString());
    }
  }
  

  public static void main(String[] args) throws Exception
  {
    CcdpUtils.loadProperties(System.getProperty(CcdpUtils.KEY_CFG_FILE));
    CcdpUtils.configLogger();
    
    
    Options options = new Options();

    Option input = new Option("i", "input", true, "input file path");
    input.setRequired(true);
    options.addOption(input);

    Option output = new Option("o", "output", true, "output file");
    output.setRequired(true);
    options.addOption(output);

    CommandLineParser parser = new DefaultParser();
    HelpFormatter formatter = new HelpFormatter();
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

    String inputFilePath = cmd.getOptionValue("input");
    String outputFilePath = cmd.getOptionValue("output");

    System.out.println(inputFilePath);
    System.out.println(outputFilePath);
    
    new CCDPTest();
  }
}
