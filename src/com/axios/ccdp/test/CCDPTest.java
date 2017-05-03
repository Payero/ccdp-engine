package com.axios.ccdp.test;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.net.URL;
import java.net.URLConnection;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import org.apache.log4j.Logger;

import com.axios.ccdp.connections.intfs.CcdpObjectFactoryAbs;
import com.axios.ccdp.connections.intfs.CcdpTaskConsumerIntf;
import com.axios.ccdp.connections.intfs.CcdpTaskingIntf;
import com.axios.ccdp.mesos.fmwk.CcdpJob;
import com.axios.ccdp.resources.CcdpVMResource;
import com.axios.ccdp.resources.CcdpVMResource.ResourceStatus;
import com.axios.ccdp.tasking.CcdpTaskRequest;
import com.axios.ccdp.tasking.CcdpThreadRequest;
import com.axios.ccdp.utils.CcdpUtils;
import com.axios.ccdp.utils.SystemResourceMonitor;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.jsonFormatVisitors.JsonArrayFormatVisitor;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;


public class CCDPTest 
{

  /**
   * Generates debug print statements based on the verbosity level.
   */
  private Logger logger = Logger.getLogger(CCDPTest.class.getName());
  
  private long latest_time;
  private SimpleDateFormat formatter = 
      new SimpleDateFormat("yyyy-mm-dd hh:mm:ss");
  
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
    ObjectMapper mapper = new ObjectMapper();
    
    ObjectNode task = mapper.createObjectNode();
    task.put("task-id", "csv_reader");
    task.put("name", "Csv File Reader");
    task.put("class-name", "tasks.csv_demo.CsvReader");
    task.put("node-type", "ec2");
    task.put("reply-to", "The Sender");
    task.put("cpu", "10");
    task.put("mem", "128");
    ArrayNode cmd = mapper.createArrayNode();
    cmd.add("python"); 
    cmd.add("/opt/modules/CsvReader.python");
    task.set("command", cmd);
    ObjectNode cfg = mapper.createObjectNode();
    cfg.put("filename", "${CCDP_HOME}/data/csv_test_file.csv");
    task.set("configuration",  cfg);
    ArrayNode ips = mapper.createArrayNode();
    ObjectNode ip = mapper.createObjectNode();
    ips.add(ip);
    task.set("input-ports", ips);

    ArrayNode fp = mapper.createArrayNode();
    ArrayNode tp = mapper.createArrayNode();
    fp.add("source-1");
    fp.add("source-2");
    
    tp.add("dest-1");
    tp.add("dest-2");
    
    ip.put("port-id", "from-exterior");
    ip.set("from-port", fp);
    ip.set("to-port", tp);
    
    this.logger.debug("The Task " + task.toString());
    
    ObjectNode event = mapper.createObjectNode();
    event.set("event", task);
    event.put("event-type",  "TASK_REQUEST");
    
    CcdpTaskRequest taskObj = 
        mapper.treeToValue(event.get("event"), CcdpTaskRequest.class);
    this.logger.debug("Task " + taskObj.toString());
    
  }
  
  
  
  public static void main( String[] args ) throws Exception
  {
    String cfg_file = System.getProperty("ccdp.config.file");
    CcdpUtils.loadProperties(cfg_file);
    CcdpUtils.configLogger();
    
    new CCDPTest();
  }

}




final class Tuple2<T1, T2> 
{
  public final T1 _1;
  
  public final T2 _2;

  public Tuple2( final T1 v1,  final T2 v2) {
      _1 = v1;
      _2 = v2;
  }

  public static <T1, T2> Tuple2<T1, T2> create( final T1 v1,  final T2 v2) {
    System.out.println("T1: " + v1.getClass().getName() + " T2: "+ v2.getClass().getName());
    return new Tuple2<>(v1, v2);
  }

  public static <T1, T2> Tuple2<T1, T2> t( final T1 v1,  final T2 v2) {
      return create(v1, v2);
  }
}
