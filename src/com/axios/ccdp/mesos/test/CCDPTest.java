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

import com.axios.ccdp.mesos.resources.CcdpVMResource;
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
    
//    String fname = "/home/oeg/dev/CCDP/data/resource.json";
//   
//    byte[] bytes = Files.readAllBytes( Paths.get( fname ) );
//    String data = new String( bytes, "UTF-8");

    HolderClass hc = new HolderClass();
    hc.addItem(new MyItem("One", "First Number"));
    hc.addItem(new MyItem("Two", "Second Number"));
    hc.addItem(new MyItem("Three", "Third Number"));
    hc.addItem(new MyItem("Four", "Fourth Number"));
    
    this.logger.debug("Before \n" + hc.toString());
    
    MyItem test = hc.getItem();
    this.logger.debug("Contains Item? " + hc.hasItem(test));
    test.setDescription("Modified Descriction");
    
    this.logger.debug("After \n" + hc.toString());
    this.logger.debug("Contains Item? " + hc.hasItem(test));
    
  }
  

  public static void main(String[] args) throws Exception
  {
    CcdpUtils.loadProperties(System.getProperty(CcdpUtils.CFG_KEY_CFG_FILE));
    CcdpUtils.configLogger();
    
    new CCDPTest();
  }
}
