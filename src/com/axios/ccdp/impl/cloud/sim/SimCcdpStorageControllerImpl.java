package com.axios.ccdp.impl.cloud.sim;

import java.io.File;
import java.io.InputStream;


import com.axios.ccdp.intfs.CcdpStorageControllerIntf;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;

public class SimCcdpStorageControllerImpl implements CcdpStorageControllerIntf
{

//  /**
//   * Generates debug print statements based on the verbosity level.
//   */
//  private Logger logger = Logger
//      .getLogger(SimCcdpStorageControllerImpl.class.getName());

  public SimCcdpStorageControllerImpl()
  {

  }

  @Override
  public void configure(JsonNode config)
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
