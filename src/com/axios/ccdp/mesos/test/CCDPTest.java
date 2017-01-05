package com.axios.ccdp.mesos.test;

import java.nio.file.Files;
import java.nio.file.Paths;

import org.apache.log4j.Logger;
import org.json.JSONObject;

import com.amazonaws.util.Base64;
import com.axios.ccdp.mesos.connections.intfs.CcdpEventConsumerIntf;
import com.axios.ccdp.mesos.connections.intfs.CcdpTaskingIntf;
import com.axios.ccdp.mesos.factory.CcdpObejctFactoryIntf;
import com.axios.ccdp.mesos.utils.CcdpUtils;

public class CCDPTest
{

  /**
   * Generates debug print statements based on the verbosity level.
   */
  private Logger logger = Logger.getLogger(CCDPTest.class.getName());

  public CCDPTest() throws Exception
  {
    this.logger.debug("Running CCDP Test");
    String str = null;
    byte[]   bytesEncoded = Base64.encode(str.getBytes());
  System.out.println("ecncoded value is " + new String(bytesEncoded ));
        

  }
  
  public void onEvent( Object event )
  {
    this.logger.info("Got a new event");
  }

  public static void main(String[] args) throws Exception
  {
    CcdpUtils.loadProperties(System.getProperty(CcdpUtils.KEY_CFG_FILE));
    CcdpUtils.configLogger();
    new CCDPTest();
  }
}
