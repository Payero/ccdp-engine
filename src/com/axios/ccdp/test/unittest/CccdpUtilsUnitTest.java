package com.axios.ccdp.test.unittest;

import static org.junit.Assert.*;

import org.junit.BeforeClass;
import org.junit.Test;

import com.axios.ccdp.utils.CcdpUtils;
import com.fasterxml.jackson.databind.node.ObjectNode;

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
    //Test changing connection interface value.
    ObjectNode conn_cfg = CcdpUtils.getConnnectionIntfCfg().deepCopy();
    String key = "broker";
    String val = "failover://tcp://localhost:61616";
    assertTrue("The key was not found in the properties", conn_cfg.has(key));
    conn_cfg.put(key, val);
    
    assertEquals("conn-cfg val did not change", val, conn_cfg.get(key).asText());
    assertNotEquals("Making sure they are actually different", CcdpUtils.getConfigValue("broker"), val);
  }
  
  
  @Test
  public void loadImageTest()
  {
    // Get EC2 Img info from json, change image id, check if changed.
    
    String imageId = CcdpUtils.getImageInfo("EC2").getImageId();
    ObjectNode res_cfg = CcdpUtils.getResourceCfg("EC2").deepCopy();
    
    String key = "image-id";
    String val = "ami-0cdc695251d96520a";
    assertTrue("The key was not found in the properties", res_cfg.has(key));
    res_cfg.put(key, val);
    
    // Instead of comparing two of the same thing,
    // Check the original with the hopefully updated.
    //assertNotEquals(imageId, CcdpUtils.getImageInfo("EC2").getImageId() ); -original, should be same
    assertNotEquals("The deep copy didn't work", res_cfg.get(key), imageId );
  }
}



