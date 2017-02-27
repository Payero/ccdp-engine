package com.axios.ccdp.mesos.test;

import org.apache.log4j.Logger;

public class MyItem
{

  /**
   * Generates debug print statements based on the verbosity level.
   */
  private Logger logger = Logger.getLogger(MyItem.class.getName());
  String name;
  String description;
  
  public MyItem(String name, String desc)
  {
    this.setName(name);
    this.setDescription(desc);
  }

  /**
   * @return the name
   */
  public String getName()
  {
    return name;
  }

  /**
   * @param name the name to set
   */
  public void setName(String name)
  {
    this.name = name;
  }

  /**
   * @return the description
   */
  public String getDescription()
  {
    return description;
  }

  /**
   * @param description the description to set
   */
  public void setDescription(String description)
  {
    this.description = description;
  }
}
