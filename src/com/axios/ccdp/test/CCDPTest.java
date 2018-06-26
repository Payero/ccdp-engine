package com.axios.ccdp.test;


import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.math.RoundingMode;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.URI;
import java.net.URL;
import java.net.UnknownHostException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.text.DecimalFormat;
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
import org.json.JSONArray;
import org.json.JSONObject;

import com.amazonaws.services.devicefarm.model.ArgumentException;
import com.amazonaws.services.rds.model.DBClusterOptionGroupStatus;
import com.axios.ccdp.cloud.sim.SimCcdpTaskRunner.BusyThread;
import com.axios.ccdp.cloud.sim.SimVirtualMachine;
import com.axios.ccdp.connections.amq.AmqSender;
import com.axios.ccdp.factory.CcdpObjectFactory;
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
    
    JSONObject prev = this.getStats();
    JSONObject prevCpuStats = prev.getJSONObject("cpu_stats");
    JSONObject prevCpuUsage =  prevCpuStats.getJSONObject("cpu_usage");
    
    CcdpUtils.pause(2);
    
    JSONObject curr = this.getStats();
    JSONObject currCpuStats = curr.getJSONObject("cpu_stats");
    JSONObject currCpuUsage =  currCpuStats.getJSONObject("cpu_usage");
    
    double prevCPU = prevCpuUsage.getDouble("total_usage");
    double currCPU = currCpuUsage.getDouble("total_usage");
    double cpuDelta = currCPU - prevCPU;
    this.logger.debug("The CPU Delta " + cpuDelta);
    
    double prevSysUsg = prevCpuStats.getDouble("system_cpu_usage");
    double currSysUsg = currCpuStats.getDouble("system_cpu_usage");
    double sysDelta = currSysUsg - prevSysUsg;
    this.logger.debug("The System Delta " + sysDelta);
    
    
    double cpuPercent = 0;
    if( sysDelta > 0.0 && cpuDelta > 0.0 )
      cpuPercent = ( cpuDelta / sysDelta) * ( (double)currCpuUsage.getJSONArray("percpu_usage").length() * 100.0 );
    
    this.logger.debug("The Percentage " + cpuPercent );
    
  }  
  
  public JSONObject getStats() throws IOException 
  {
    URL url = new URL("http://172.17.0.1:2375/containers/1b6c5ae96bcd/stats?stream=0"); 
    JSONObject obj = null;
    try (BufferedReader reader = new BufferedReader(new InputStreamReader(url.openStream(), "UTF-8"))) 
    {
        for (String line; (line = reader.readLine()) != null;) 
        {
          this.logger.debug("Parsing Line " + line);
          obj = new JSONObject(line);
        }
        if( obj != null )
          this.logger.debug("Object " + obj.toString(2));
    }
    return obj;
}
  
  public static void main( String[] args ) throws Exception
  {
    String cfg_file = System.getProperty("ccdp.config.file");
    CcdpUtils.loadProperties(cfg_file);
    CcdpUtils.configLogger();
    
    new CCDPTest();
  }

}



