package com.axios.ccdp.mesos.connections.intfs;

import com.fasterxml.jackson.databind.node.ObjectNode;


/**
 * Factory Pattern class that is used to generate all the objects used by the 
 * system to talk to any external entity.  The actual implementation of this 
 * class is retrieved by invoking the newInstance() method and passing the fully
 * qualified dot notation path of the class.
 * 
 * @author Oscar E. Ganteaume
 *
 */
public abstract class CcdpObjectFactoryAbs
{
  /**
   * A singleton object used to create the objects
   */
  protected static CcdpObjectFactoryAbs factory = null;
  
  /**
   * Limiting access to this constructor is intentional in order to inforce the
   * use of a single factory object
   */
  protected CcdpObjectFactoryAbs()
  {

  }
  
  /**
   * Instantiates a class implementing this abstract class from the given
   * argument.  The clazz arguments needs to be a fully qualified java path
   * using the dot notation.  It also needs to be found in the classpath. 
   * 
   * If the class cannot be instantiated then an exception is thrown.
   * 
   * @param clazz the fully qualified location of the class to instantiate using
   *        Java dot notation
   *        
   * @return an instance of the Class specified by the clazz argument
   */
  public static CcdpObjectFactoryAbs newInstance(String clazz)
  {
    // if we have not created a factory before do it, otherwise just return it
    if( CcdpObjectFactoryAbs.factory == null )
    {
      try
      {
        Class<?> instantation = Class.forName(clazz);
    
        CcdpObjectFactoryAbs.factory = 
                        (CcdpObjectFactoryAbs) instantation.newInstance();
      }
      catch( Exception e)
      {
        System.err.println("Message: " + e.getMessage());
        e.printStackTrace();
        return null;
      }
    }
    
    return CcdpObjectFactoryAbs.factory;
  }
  
  /**
   * Gets the object that is used to task the scheduler.  The same interface
   * is also used to send messages back to a specific destination
   * 
   * @param config a JSON Object containing required configuration parameters
   * @return an actual implementation of the object that allows the scheduler
   *         to send and receive tasking events
   */
  public abstract CcdpTaskingIntf getCcdpTaskingInterface(ObjectNode config);
  
  /**
   * Gets the object responsible for controlling the resources.  For instance, 
   * it starts and stops VMs, ask for status, etc.
   * 
   * @param config a JSON Object containing required configuration parameters
   * @return an actual implementation of the object that allows the scheduler
   *         to manipulate the resources
   */
  public abstract CcdpVMControllerIntf 
                            getCcdpResourceController(ObjectNode config);

  /**
   * Gets the object responsible for tasking the resources.  For instance, 
   * it will start a task based on a session-id, capacity, etc
   * 
   * @param config a JSON Object containing required configuration parameters
   * 
   * @return an actual implementation of the object that allows the scheduler
   *         to manipulate the tasking
   */
  public abstract CcdpTaskingControllerIntf 
                            getCcdpTaskingController(ObjectNode config);
  
  /**
   * Gets the object responsible for controlling the storage of objects.  For
   * instance, it creates and deletes directories and files in a file system
   * implementation.  It can also retrieve the contents of a file or an object
   * stored in a S3 bucket
   * 
   * @param config a JSON Object containing required configuration parameters
   * @return an actual implementation of the object that allows the scheduler
   *         to manipulate the storage resources
   */
  public abstract CcdpStorageControllerIntf 
                            getCcdpStorageControllerIntf(ObjectNode config);
}
