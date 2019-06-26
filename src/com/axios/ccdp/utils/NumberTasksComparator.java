package com.axios.ccdp.utils;

import java.util.Comparator;

import com.axios.ccdp.resources.CcdpVMResource;
import com.axios.ccdp.tasking.CcdpTaskRequest;

/**
 * Compares two CcdpVMResources based on the number of tasks they are 
 * running with the same name.
 */
public class NumberTasksComparator implements Comparator<CcdpVMResource>
{
  /**
   * The name of the tasks to find
   */
  private String taskName = null;
  /**
   * Instantiates a new comparator object setting the name of the task to 
   * search.
   * 
   * @param taskName the common name of the tasks to sort by
   */
  public NumberTasksComparator( String taskName )
  {
    this.taskName = taskName;
  }
  
  /**
   * It counts the number of tasks matching the name of the comparator object.
   * After all the tasks from each resource is checked, the comparator compares
   * both tasks counts matching the name and return such comparison.
   * 
   * @param res1 the first VM resource to compare
   * @param res2 the second VM resource to compare
   * 
   * @return a comparison between the total number of matching task names
   */
  @Override
  public int compare(CcdpVMResource res1, CcdpVMResource res2)
  {
    Integer res1_tasks = 0;
    Integer res2_tasks = 0;
    // how many tasks matches the name from the first resource?
    for( CcdpTaskRequest task : res1.getTasks() )
      if( this.taskName.equals( task.getName() ) )
        res1_tasks++;
    
    // how many tasks matches the name from the second resource?
    for( CcdpTaskRequest task : res2.getTasks() )
      if( this.taskName.equals( task.getName() ) )
        res2_tasks++;
    
    return res1_tasks.compareTo(res2_tasks);
  }    
}
