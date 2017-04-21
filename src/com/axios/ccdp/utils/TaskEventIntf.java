package com.axios.ccdp.utils;

/**
 * Simple interface used to notify a running application that an event has 
 * occurred.  This interface is designed to work with the ThreadedTimerTask
 * @author Oscar E. Ganteaume
 *
 */
public interface TaskEventIntf
{

  /**
   * This method is invoked every time the waiting period expires
   */
  public void onEvent();
}
