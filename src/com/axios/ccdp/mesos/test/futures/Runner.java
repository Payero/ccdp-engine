package com.axios.ccdp.mesos.test.futures;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.function.BiFunction;

import org.apache.log4j.Logger;
import org.json.JSONObject;

import com.axios.ccdp.mesos.utils.CcdpUtils;


public class Runner
{

  /**
   * Generates debug print statements based on the verbosity level.
   */
  private Logger logger = Logger.getLogger(Runner.class.getName());
  
  public Runner()
  {
    this.logger.debug("Running");
    
    boolean simple = false;
    if ( simple )
    {
      TaskRunner task = new TaskRunner("One");
      Thread t = new Thread(task);
      t.start();
      CcdpUtils.pause(5);
      this.logger.debug("The Task " + task.get());
    }
    else
    {
      Executor exec = Executors.newFixedThreadPool(2);
      TaskRunner task = new TaskRunner("One");
      CompletableFuture<JSONObject> 
                        future = CompletableFuture.supplyAsync(task, exec);
     
      
      try
      {
        future.get();
      }
      catch (InterruptedException e)
      {
        // TODO Auto-generated catch block
        e.printStackTrace();
      }
      catch (ExecutionException e)
      {
        // TODO Auto-generated catch block
        e.printStackTrace();
      }
      
      JSONObject json = future.join();
     
      this.logger.debug("The Task: " + json );
    }
    
  }
  public void handleTask( BiFunction<JSONObject, Throwable, Void> a )
  {
    
  }

  public void good()
  {
    
  }
  
  public void bad()
  {
    
  }
  public static void main(String[] args)
  {
    CcdpUtils.configLogger();
    new Runner();
  }
}
