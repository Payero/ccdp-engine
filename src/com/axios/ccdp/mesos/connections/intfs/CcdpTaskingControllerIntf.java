package com.axios.ccdp.mesos.connections.intfs;

import com.axios.ccdp.mesos.tasking.CcdpThreadRequest;
import com.google.gson.JsonObject;


public abstract class CcdpTaskingControllerIntf implements Runnable
{

  public abstract void configure( JsonObject config );
  
  public abstract void setVMController( CcdpVMControllerIntf controller );
  
  public abstract void setStorageController( CcdpStorageControllerIntf storage );
  
  public abstract void addTaskingThread(CcdpThreadRequest request);
  
  public abstract int getNumberPendingThreads();
  
  public abstract int getNumberPendingTasks();
  
  public abstract void updateResources(JsonObject resources);
  
  public abstract void stopThread( String threadId );
  
  public abstract void killThread( String threadId );
  
  public abstract void stopTask( String taskId );
  
  public abstract void killTask( String taskId );
  
  public abstract void stopTasking();
  
}
