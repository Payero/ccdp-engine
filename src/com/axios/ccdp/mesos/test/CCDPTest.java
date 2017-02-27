package com.axios.ccdp.mesos.test;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.StringJoiner;
import java.util.concurrent.ConcurrentLinkedQueue;

import org.apache.log4j.Logger;

import com.axios.ccdp.mesos.resources.CcdpVMResource;
import com.axios.ccdp.mesos.utils.CcdpUtils;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;


public class CCDPTest implements GetMe
{

  /**
   * Generates debug print statements based on the verbosity level.
   */
  private Logger logger = Logger.getLogger(CCDPTest.class.getName());

  
  public CCDPTest() throws Exception
  {
    this.logger.debug("Running CCDP Test");
    Properties props = System.getProperties();
    Enumeration<Object> keys = props.keys();
    while( keys.hasMoreElements() )
    {
      String key = (String)keys.nextElement();
      String val = props.getProperty(key);
      this.logger.debug("Property[" + key + "] = " + val);
    }

  }  
  
  
  public List<String> getItems()
  {
    List<String> array = new ArrayList<>();
    array.add("One");
    array.add("Dos");
    array.add("Tres");
    array.add("Cuatro");
    
    return array;
  }
  
  public static void main(String[] args) throws Exception
  {
    CcdpUtils.loadProperties(System.getProperty(CcdpUtils.CFG_KEY_CFG_FILE));
    CcdpUtils.configLogger();
    
    new CCDPTest();
  }
}

class TestLong implements GetMe<Long>
{
  public List<Long> getItems()
  {
    List<Long> list = new ArrayList<>();
    list.add(new Long(1));
    list.add(new Long(2));
    list.add(new Long(3));
    list.add(new Long(4));
    
    
    return list;
  }
  
}

interface GetMe<T>
{
  public List<T> getItems();
  
}
