package com.axios.ccdp.test;


import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Enumeration;
import java.util.List;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Properties;
import java.util.Random;
import java.util.UUID;

import org.apache.log4j.Logger;

import com.axios.ccdp.cloud.mock.MockCcdpTaskRunner.BusyThread;
import com.axios.ccdp.connections.amq.AmqSender;
import com.axios.ccdp.messages.CcdpMessage.CcdpMessageType;
import com.axios.ccdp.resources.CcdpImageInfo;
import com.axios.ccdp.resources.CcdpVMResource;
import com.axios.ccdp.messages.KillTaskMessage;
import com.axios.ccdp.messages.ThreadRequestMessage;
import com.axios.ccdp.tasking.CcdpTaskRequest;
import com.axios.ccdp.tasking.CcdpThreadRequest;
import com.axios.ccdp.utils.CcdpUtils;
import com.axios.ccdp.utils.CcdpUtils.CcdpNodeType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

public class CCDPTest 
{
  /**
   * Generates debug print statements based on the verbosity level.
   */
  private Logger logger = Logger.getLogger(CCDPTest.class.getName());
  
  
  public CCDPTest()
  {
    this.logger.debug("Running CCDP Test");
    try
    {
      this.runTest();
    }
    catch( Exception e )
    {
      this.logger.error("Message: " + e.getMessage(), e);
    }
    
  }
  
  
  private void runTest() throws Exception
  {
    this.logger.debug("Running the Test");
    
    Map<Integer, List<TestContainer>> map = new HashMap<>();
    map.put(new Integer(1), new ArrayList<TestContainer>());
    map.put(new Integer(2), new ArrayList<TestContainer>());
    
    TestContainer t1 = new TestContainer(1, "T1", "The First Container");
    TestContainer t2 = new TestContainer(2, "T2", "The Second Container");
    TestContainer t3 = new TestContainer(3, "T3", "The Third Container");
    TestContainer t4 = new TestContainer(4, "T4", "The Fourth Container");
    TestContainer t5 = new TestContainer(5, "T5", "The Fith Container");
    
    List<TestContainer> l1 = map.get(1);
    l1.add(t1);
    l1.add(t2);
    this.printChanges(map);
  }
  
  private void printChanges(Map<Integer, List<TestContainer>> map )
  {
    
    StringBuffer buf = new StringBuffer();
    buf.append("\nNode:\n");
    
    for( Integer type : map.keySet() )
    {
      buf.append(type);
      buf.append("\n\tInstance Id\t\tName\t\tDescription\n");
      buf.append("--------------------------------------------------------------------------------\n");
      List<TestContainer> vms = map.get(type);
      for( TestContainer info : vms )
      {
        
        int id = info.number;
        String name = info.name;
        String desc = info.desc;
        buf.append("\t" + id + "\t" + name + "\t\t\t" + desc + "\t\t\tTasks\n");
        List<String> tasks = info.tasks;
        for( String task : tasks )
        {
          buf.append("\t\t\t\t\t\t\t\t\t * " + task + "\n");
        } // end of the tasks
        buf.append("\n");
        
      }// end of the VMs
    }// end of the Node Types
    
    
    this.logger.debug( buf.toString() );
  }

  private class TestContainer
  {
    public int number = 0;
    public String name = null;
    public String desc = null;
    public List<String> tasks = new ArrayList<>();
    
    public TestContainer(int num, String name, String desc)
    {
      this.number = num;
      this.name = name;
      this.desc = desc;
    }
    
    public String toString()
    {
      return String.format("Number %d, Name %s, Desc: %s", number, name, desc);
    }
  }
  
  public static void main( String[] args ) throws Exception
  {
    String cfg_file = System.getProperty("ccdp.config.file");
    CcdpUtils.loadProperties(cfg_file);
    CcdpUtils.configLogger();
    
    new CCDPTest();
  }

}



