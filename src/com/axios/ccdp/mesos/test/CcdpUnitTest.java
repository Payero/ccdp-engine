package com.axios.ccdp.mesos.test;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.stream.IntStream;

import org.apache.log4j.Logger;
import org.junit.Test;

import static org.hamcrest.CoreMatchers.*;

import com.axios.ccdp.mesos.utils.CcdpUtils;

import static org.junit.Assert.*;

public class CcdpUnitTest
{
  private int count = 0;
  
  /**
   * Generates debug print statements based on the verbosity level.
   */
  private Logger logger = Logger.getLogger(CcdpUnitTest.class.getName());

  
  public void runTest()
  {
    CcdpUtils.configLogger();
    
    this.logger.debug("Running the test");
    String a = "This is a";
    String b = "This is b";
    assertEquals(b, b);
  }
  
  
  public void runMatcher()
  {
    CcdpUtils.configLogger();
    assertThat( 123, is(123));
  }
  
  
  public void testConcurrency()
  {
    CompletableFuture cf = CompletableFuture.supplyAsync( ( ) -> {
    // big computation task
        return "100";
    } );
    
    try
    {
      this.logger.debug("The result " + cf.get());
      
      Callable<Integer> task = () -> {
        try {
            TimeUnit.SECONDS.sleep(1);
            return 123;
        }
        catch (InterruptedException e) {
            throw new IllegalStateException("task interrupted", e);
        }
        
    };
    
    ExecutorService executor = Executors.newFixedThreadPool(1);
    Future<Integer> future = executor.submit(task);

    this.logger.debug("future done? " + future.isDone());

    Integer result = future.get();

    this.logger.debug("future done? " + future.isDone());
    this.logger.debug("result: " + result);
    
    this.logger.info("******************   Using Callable   *****************");
    Callable<Integer> cal_task = () -> {
      try {
          TimeUnit.SECONDS.sleep(1);
          return 123;
      }
      catch (InterruptedException e) {
          throw new IllegalStateException("task interrupted", e);
      }
      };
      
     executor.submit(cal_task);
     executor.awaitTermination(2, TimeUnit.SECONDS);
     executor.shutdown();
     
     this.logger.info("**********************    Synchornization    **********************");
     ExecutorService exec = Executors.newFixedThreadPool(2);

     IntStream.range(0, 10000)
         .forEach(i -> exec.submit(this::increment));

     exec.shutdown();

     this.logger.debug("Count: " + this.count);
    }
    catch (InterruptedException | ExecutionException e)
    {
      e.printStackTrace();
    }
  }
  
  public void increment()
  {
    synchronized(this)
    {
      this.count = this.count + 1;
    }
  }
  
  
  public void testReadWrite()
  {
    this.logger.debug("Running Test Write");
    ExecutorService executor = Executors.newFixedThreadPool(2);
    Map<String, String> map = new HashMap<>();
    ReadWriteLock lock = new ReentrantReadWriteLock();

    executor.submit(() -> {
        lock.writeLock().lock();
        try 
        {
          this.logger.debug("Writing to the Map");
          CcdpUtils.pause(1);
            map.put("foo", "bar");
        } 
        finally 
        {
            lock.writeLock().unlock();
        }
    });
    
    
    Runnable readTask = () -> 
    {
      lock.readLock().lock();
      try 
      {
        this.logger.debug("Getting Item " + map.get("foo"));
          CcdpUtils.pause(1);
      } 
      finally 
      {
          lock.readLock().unlock();
      }
  };

  executor.submit(readTask);
  executor.submit(readTask);
  CcdpUtils.pause(2);
  executor.shutdown();
  
  }
  
  @Test
  public void myTest()
  {
    this.logger.debug("Running my own test");
    ExecutorService executor = Executors.newFixedThreadPool(1);
    CompletableFuture<String> cf = new CompletableFuture<>();
    
    
  }
}
