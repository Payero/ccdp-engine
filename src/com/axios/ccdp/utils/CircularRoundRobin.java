package com.axios.ccdp.utils;

import java.util.Iterator;
import java.util.List;

import org.apache.log4j.Logger;

/**
 * A Round Robin class that has a limited number of elements.  It retrieves the 
 * next element in the collection when the next() method is invoked.  Once it
 * reaches the end of the collection, returns to the first element again.
 *  
 * @author Oscar E. Ganteaume
 *
 * @param <E> Determines the class of the objects in the collection
 */
public class CircularRoundRobin<E> implements Iterable<E>
{
  /**
   * Generates debug print statements based on the verbosity level.
   */
  private Logger logger = Logger.getLogger(CircularRoundRobin.class.getName());
  /**
   * Stores all the elements passed during initialization
   */
  private List<E> collection;
  /**
   * Stores the size of the collection
   */
  private int size = 0;
  /**
   * Determines the position in the collection
   */
  private int position = 0;
  
  /**
   * Instantiates a new object that will iterate through all the elements in the
   * collection
   * 
   * @param collection a list of elements to iterate
   */
  public CircularRoundRobin(List<E> collection)
  {
    this.logger.debug("Creating a new Circular Round Robin Queue");
    this.setCollection(collection);
  }
  
  /**
   * Gets the next element in the collection.  If the current position is set
   *  to the last element, then it returns the first element in the collection.
   *  
   * @return the next element on the collection or the first one if the end is
   *         reached 
   */
  public E next()
  {
    // get the next element 
    E nextObj = this.collection.get( this.position );
    
    // set the position to be the next one or the first one
    if( this.position < this.size -1 )
      this.position++;
    else
      this.position = 0;
    
    return nextObj;
  }
  
  /**
   * Generates a JSON like String object containing all the elements in the
   * collection.  It invokes the toString() method on each element.
   * 
   * @return a JSON representation of the elements in the container
   */
  public String toString()
  {
    StringBuffer buf = new StringBuffer("[");
    for(int i = 0; i < this.size; i++ )
    {
      buf.append(this.collection.get(i).toString());
      // don't want the last comma
      if( i != this.size -1 )
        buf.append(",");
    }
    buf.append("]");
    
    return buf.toString();
  }
  
  /**
   * Sets the collection to iterate
   * 
   * @param collection the collection to iterate
   * 
   * @throws IllegalArgumentException an IllegalArgumentException is thrown if
   *         the collection is null
   */
  public void setCollection( List<E> collection )
  {
    if( collection == null )
      throw new IllegalArgumentException("The collection cannot be null");
    
    this.logger.debug("Setting a collection with " + collection.size() + " elements");
    this.collection = collection;
    this.size = this.collection.size();
  }
  
  /**
   * Adds a new element to the collection
   * 
   * @param element the element to add to the collection
   */
  public void addElement( E element )
  {
    this.collection.add(element);
    this.size = this.collection.size();
  }
  
  /**
   * Removes an element from the collection and resets the current position 
   * accordingly.  The element to be removed is the first element matching the
   * equals invocation.
   * 
   * @param element the element to remove from the collection
   */
  public void removeElement( E element )
  {
    for( int i = 0; i < this.collection.size(); i++ )
    {
      E current = this.collection.get(i);
      if( current.equals( element ) )
      {
        this.logger.info("Found element, removing it");
        if( i >= this.position )
        {
          this.logger.info("Resetting position to " + ( i - 1 ) );
          this.position = i - 1;
        }
      }
    }
  }
  
  
  /**
   * Returns an object that iterates through all the elements in the collection 
   * once
   */
  public Iterator<E> iterator()
  {
    /**
     * Instantiates a new iterator object
     */
    return new Iterator<E>()
    {
      private int index = 0;
      
      /**
       * Return true if the current position is less than the size of the
       * collection container or false otherwise
       * 
       * @return true of te current position is less than the max size
       */
      @Override
      public boolean hasNext()
      {
        if( this.index < size )
          return true;
        else
          return false;
      }
      
      /**
       * Gets the next object in the collection
       * 
       * @return the next object in the collection
       */
      @Override
      public E next()
      {
        E res = collection.get(index);
        index++;
        return res;
      }
      
      /**
       * This method has not been implemented intentionally
       * @throws UnsupportedOperationException an UnsupportedOperationException
       *         is thrown if this method is invoked 
       */
      @Override
      public void remove()
      {
        throw new UnsupportedOperationException();
      }
    };
  }
  
}
