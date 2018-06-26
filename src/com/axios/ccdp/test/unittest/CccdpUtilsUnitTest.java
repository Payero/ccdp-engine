package com.axios.ccdp.test.unittest;

import static org.junit.Assert.*;

import org.junit.BeforeClass;
import org.junit.Test;

import com.axios.ccdp.utils.CcdpUtils;
import com.axios.ccdp.utils.CcdpUtils.CcdpNodeType;

public class CccdpUtilsUnitTest
{
  @BeforeClass
  public static void initialize()
  {
    JUnitTestHelper.initialize();
  }

  @Test
  public void ChangingPropertiesTest()
  {
    String key = "connectionIntf.broker.connection";
    String val = "failover://tcp://localhost:61616";
    assertTrue("The key was not found in the properties",CcdpUtils.containsKey(key));
    CcdpUtils.setProperty(key, val);
    
    assertEquals(val, CcdpUtils.getProperty(key));
  }
  
  
  @Test
  public void loadImageTest()
  {
    String imageId = CcdpUtils.getImageInfo(CcdpNodeType.EC2).getImageId();
    
    String key = "resourceIntf.ec2.image.id";
    String val = "ami-0cdc695251d96520a";
    assertTrue("The key was not found in the properties",CcdpUtils.containsKey(key));
    CcdpUtils.setProperty(key, val);
    assertNotEquals(imageId,CcdpUtils.getImageInfo(CcdpNodeType.EC2).getImageId() );
  }
}



