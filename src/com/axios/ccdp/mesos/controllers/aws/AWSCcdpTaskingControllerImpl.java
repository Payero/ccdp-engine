/**
 * 
 */
package com.axios.ccdp.mesos.controllers.aws;

import org.apache.log4j.Logger;

import com.axios.ccdp.mesos.connections.intfs.CcdpStorageControllerIntf;
import com.axios.ccdp.mesos.connections.intfs.CcdpTaskingControllerIntf;
import com.axios.ccdp.mesos.connections.intfs.CcdpVMControllerIntf;
import com.axios.ccdp.mesos.tasking.CcdpThreadRequest;
import com.google.gson.JsonObject;

/**
 * @author Oscar E. Ganteaume
 *
 */
public class AWSCcdpTaskingControllerImpl extends CcdpTaskingControllerIntf
{

  /**
   * Generates debug print statements based on the verbosity level.
   */
  private Logger logger = Logger.getLogger(AWSCcdpTaskingControllerImpl.class
      .getName());

  public AWSCcdpTaskingControllerImpl()
  {

  }

  /* (non-Javadoc)
   * @see java.lang.Runnable#run()
   */
  @Override
  public void run()
  {
    // TODO Auto-generated method stub

  }

  /* (non-Javadoc)
   * @see com.axios.ccdp.mesos.connections.intfs.CcdpTaskingControllerIntf#init(com.google.gson.JsonObject)
   */
  @Override
  public void configure(JsonObject config)
  {
    // TODO Auto-generated method stub

  }

  /* (non-Javadoc)
   * @see com.axios.ccdp.mesos.connections.intfs.CcdpTaskingControllerIntf#setVMController(com.axios.ccdp.mesos.connections.intfs.CcdpVMControllerIntf)
   */
  @Override
  public void setVMController(CcdpVMControllerIntf controller)
  {
    // TODO Auto-generated method stub

  }

  /* (non-Javadoc)
   * @see com.axios.ccdp.mesos.connections.intfs.CcdpTaskingControllerIntf#setStorageController(com.axios.ccdp.mesos.connections.intfs.CcdpStorageControllerIntf)
   */
  @Override
  public void setStorageController(CcdpStorageControllerIntf storage)
  {
    // TODO Auto-generated method stub

  }

  /* (non-Javadoc)
   * @see com.axios.ccdp.mesos.connections.intfs.CcdpTaskingControllerIntf#addTaskingThread(com.axios.ccdp.mesos.tasking.CcdpThreadRequest)
   */
  @Override
  public void addTaskingThread(CcdpThreadRequest request)
  {
    // TODO Auto-generated method stub

  }

  /* (non-Javadoc)
   * @see com.axios.ccdp.mesos.connections.intfs.CcdpTaskingControllerIntf#getPendingThreads()
   */
  @Override
  public int getNumberPendingThreads()
  {
    // TODO Auto-generated method stub
    return 0;
  }

  /* (non-Javadoc)
   * @see com.axios.ccdp.mesos.connections.intfs.CcdpTaskingControllerIntf#getPendingThreads()
   */
  @Override
  public int getNumberPendingTasks()
  {
    // TODO Auto-generated method stub
    return 0;
  }
  
  /* (non-Javadoc)
   * @see com.axios.ccdp.mesos.connections.intfs.CcdpTaskingControllerIntf#updateResources(com.google.gson.JsonObject)
   */
  @Override
  public void updateResources(JsonObject resources)
  {
    // TODO Auto-generated method stub

  }
  

  @Override
  public void stopThread(String threadId)
  {
    // TODO Auto-generated method stub
    
  }

  @Override
  public void killThread(String threadId)
  {
    // TODO Auto-generated method stub
    
  }

  @Override
  public void stopTask(String taskId)
  {
    // TODO Auto-generated method stub
    
  }

  @Override
  public void killTask(String taskId)
  {
    // TODO Auto-generated method stub
    
  }
  
  /* (non-Javadoc)
   * @see com.axios.ccdp.mesos.connections.intfs.CcdpTaskingControllerIntf#updateResources(com.google.gson.JsonObject)
   */
  @Override
  public void stopTasking()
  {
    // TODO Auto-generated method stub

  }
}
