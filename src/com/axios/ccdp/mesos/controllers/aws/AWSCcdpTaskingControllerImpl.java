/**
 * 
 */
package com.axios.ccdp.mesos.controllers.aws;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;

import org.apache.log4j.Logger;

import com.axios.ccdp.mesos.connections.intfs.CcdpStorageControllerIntf;
import com.axios.ccdp.mesos.connections.intfs.CcdpTaskingControllerIntf;
import com.axios.ccdp.mesos.connections.intfs.CcdpVMControllerIntf;
import com.axios.ccdp.mesos.resources.CcdpVMResource;
import com.axios.ccdp.mesos.tasking.CcdpTaskRequest;
import com.axios.ccdp.mesos.tasking.CcdpThreadRequest;
import com.axios.ccdp.mesos.utils.CcdpUtils;
import com.axios.ccdp.mesos.utils.TaskEventIntf;
import com.axios.ccdp.mesos.utils.ThreadController;
import com.axios.ccdp.mesos.utils.ThreadedTimerTask;
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

  /**
   * Stores all the VMS or resources that have not been allocated to a 
   * particular session
   */
  private ConcurrentLinkedQueue<CcdpVMResource> free_vms; 
  /**
   * Stores all the VMs allocated to different sessions
   */
  private Map<String, ConcurrentLinkedQueue<CcdpVMResource>> sessions = null;
  /**
   * Stores all the different tasking threads to process
   */
  private ConcurrentLinkedQueue<CcdpThreadRequest> threads;
  /**
   * Controls the continuous running of this thread
   */
  private ThreadController event = new ThreadController();
  
  /**
   * Instantiates a new object and starts receiving and processing incoming 
   * assignments    
   */
  public AWSCcdpTaskingControllerImpl()
  {
    this.logger.debug("Initiating Tasker object");
    this.free_vms = new ConcurrentLinkedQueue<CcdpVMResource>();
    this.sessions = new HashMap<String, ConcurrentLinkedQueue<CcdpVMResource>>();
    this.threads = new ConcurrentLinkedQueue<CcdpThreadRequest>();
  }

  /**
   * Runs continuously checking for incoming tasking and assigning them to the 
   * appropriate VMs 
   */
  @Override
  public void run()
  {
    this.logger.info("Starting Tasking Process");
    while( !this.event.isSet() )
    {
      Iterator<CcdpThreadRequest> jobs = this.threads.iterator();
      while( jobs.hasNext() )
      {
        CcdpThreadRequest thread = jobs.next();
        CcdpTaskRequest task = thread.getNextTask();
        if( task != null )
        {
          this.logger.info("Launching Task");
          this.launchTask(task);
        }
        else if( thread.threadRequestCompleted() )
        {
          String msg = "Thread Reqwuest: " + thread.getThreadId() + " complete";
          this.logger.info(msg);
          this.threads.remove(thread);
        }
      }
    }
  }

  private void launchTask( CcdpTaskRequest task )
  {
    
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
  public void updateResource(CcdpVMResource resource)
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
