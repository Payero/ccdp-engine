package com.axios.ccdp.mesos.test;

import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

import org.apache.log4j.Logger;

public class HolderClass
{

  /**
   * Generates debug print statements based on the verbosity level.
   */
  private Logger logger = Logger.getLogger(HolderClass.class.getName());
  
  private List<MyItem> items = new LinkedList<MyItem>();
  
  public HolderClass()
  {
    this.logger.debug("Creating new Holder Class");
  }
  public void addItem(MyItem item)
  {
    this.items.add(item);
  }
  
  public MyItem getItem()
  {
    return this.getItem(0);
  }
  
  public MyItem getItem(int i)
  {
    if( this.items.size() > i )
      return this.items.get(i);
    
    return null;
  }
  
  public String toString()
  {
    StringBuffer buf = new StringBuffer();
    Iterator<MyItem> i = this.items.iterator();
    while( i.hasNext() )
    {
      MyItem item = i.next();
      
      buf.append("Name: ");
      buf.append(item.getName());
      buf.append(", Description: ");
      buf.append(item.getDescription());
      buf.append("\n");
    }
    
    return buf.toString();
  }
  
  public boolean hasItem(MyItem item)
  {
    return this.items.contains(item);
  }
}
