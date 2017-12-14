package com.axios.ccdp.cloud.mock;

import java.io.File;
import java.io.InputStream;

import org.apache.log4j.Logger;

import com.axios.ccdp.connections.intfs.CcdpStorageControllerIntf;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

public class MockCcdpStorageControllerImpl implements CcdpStorageControllerIntf
{

  /**
   * Generates debug print statements based on the verbosity level.
   */
  private Logger logger = Logger
      .getLogger(MockCcdpStorageControllerImpl.class.getName());

  public MockCcdpStorageControllerImpl()
  {

  }

  @Override
  public void configure(ObjectNode config)
  {
    // TODO Auto-generated method stub

  }

  @Override
  public boolean createStorage(String where)
  {
    // TODO Auto-generated method stub
    return false;
  }

  @Override
  public boolean storeElement(String where, String target, File source)
  {
    // TODO Auto-generated method stub
    return false;
  }

  @Override
  public InputStream getElement(String where, String what)
  {
    // TODO Auto-generated method stub
    return null;
  }

  @Override
  public ArrayNode listAllStorages()
  {
    // TODO Auto-generated method stub
    return null;
  }

  @Override
  public ArrayNode listAllElements(String where)
  {
    // TODO Auto-generated method stub
    return null;
  }

  @Override
  public ArrayNode listAllElementsWithPrefix(String where, String prefix)
  {
    // TODO Auto-generated method stub
    return null;
  }

  @Override
  public boolean deleteStorage(String where)
  {
    // TODO Auto-generated method stub
    return false;
  }

  @Override
  public boolean deleteElement(String where, String what)
  {
    // TODO Auto-generated method stub
    return false;
  }
}
