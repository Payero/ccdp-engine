package com.axios.ccdp.mesos.test;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.StringJoiner;

import org.apache.log4j.Logger;

import com.axios.ccdp.mesos.fmwk.CcdpJob;
import com.axios.ccdp.mesos.tasking.CcdpTaskRequest;
import com.axios.ccdp.mesos.tasking.CcdpThreadRequest;
import com.axios.ccdp.mesos.utils.CcdpUtils;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;


public class CCDPTest
{

  /**
   * Generates debug print statements based on the verbosity level.
   */
  private Logger logger = Logger.getLogger(CCDPTest.class.getName());

  
  public CCDPTest() throws Exception
  {
    this.logger.debug("Running CCDP Test");
    List<String> lista = new ArrayList<>();
    lista.add("Uno");
    lista.add("Dos");
    lista.add("Tres");
    lista.add("Cuatro");
    lista.add("Cinco");
    
    List<String> list = new ArrayList<>();
    list.add("One");
    list.add("Two");
    list.add("Three");
    list.add("Four");
    list.add("Five");
    
    List<String> both = new ArrayList<>();
    both.addAll(lista);
    both.addAll(list);
    for( String s : both )
      this.logger.debug("Item " + s);
    
  }  
  

  
  public static void main(String[] args) throws Exception
  {
    CcdpUtils.loadProperties(System.getProperty(CcdpUtils.CFG_KEY_CFG_FILE));
    CcdpUtils.configLogger();
    
    new CCDPTest();
  }
}


