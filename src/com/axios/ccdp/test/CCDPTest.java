package com.axios.ccdp.test;


import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
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
    
    this.logger.debug("The data " + this.sendData() );
    
  }  
  
  public String sendData() throws IOException 
  {
    // curl_init and url
    URL url = new URL("http://172.17.0.1:2375");
    HttpURLConnection con = (HttpURLConnection) url.openConnection();

    //  CURLOPT_POST
    con.setRequestMethod("GET");

    // CURLOPT_FOLLOWLOCATION
    con.setInstanceFollowRedirects(true);

    String postData = "containers/json?all=1";
    con.setRequestProperty("Content-length", String.valueOf(postData.length()));

    con.setDoOutput(true);
    con.setDoInput(true);

    DataOutputStream output = new DataOutputStream(con.getOutputStream());
    output.writeBytes(postData);
    output.close();

    // "Post data send ... waiting for reply");
    int code = con.getResponseCode(); // 200 = HTTP_OK
    System.out.println("Response    (Code):" + code);
    System.out.println("Response (Message):" + con.getResponseMessage());

    // read the response
    DataInputStream input = new DataInputStream(con.getInputStream());
    int c;
    StringBuilder resultBuf = new StringBuilder();
    while ( (c = input.read()) != -1) 
    {
      resultBuf.append((char) c);
    }
    input.close();

    return resultBuf.toString();
}
  
  public static void main( String[] args ) throws Exception
  {
    String cfg_file = System.getProperty("ccdp.config.file");
    CcdpUtils.loadProperties(cfg_file);
    CcdpUtils.configLogger();
    
    new CCDPTest();
  }

}



