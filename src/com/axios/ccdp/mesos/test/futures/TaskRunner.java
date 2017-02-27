package com.axios.ccdp.mesos.test.futures;

import java.util.Date;
import java.util.Random;
import java.util.function.Supplier;

import org.apache.log4j.Logger;

import com.axios.ccdp.mesos.utils.CcdpUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

public class TaskRunner implements Runnable, Supplier<ObjectNode>
{

  /**
   * Generates debug print statements based on the verbosity level.
   */
  private Logger logger = Logger.getLogger(TaskRunner.class.getName());

  private String name = "";
  private Date date = null;
  
  public TaskRunner(String name)
  {
    this.logger.debug("Setting up Task to " + name );
    this.name = name;
  }

  @Override
  public void run()
  {
    this.logger.debug("Running task " + this.name);
    Random random = new Random();
    int wait = random.nextInt(5);
    this.logger.debug("Waiting " + wait + " seconds");
    CcdpUtils.pause(wait);
    this.date = new Date();
  }
  
  public ObjectNode get()
  {
    ObjectNode node = new ObjectMapper().createObjectNode();
    
    node.put("name", this.name);
    node.put("date", this.date.toString());
    
    return node;
  }
  
  public String getName()
  {
    return this.name;
  }
}
