package com.axios.ccdp.factory;

import com.axios.ccdp.connections.intfs.CcdpConnectionIntf;
import com.axios.ccdp.connections.intfs.CcdpDatabaseIntf;
import com.axios.ccdp.connections.intfs.CcdpStorageControllerIntf;
import com.axios.ccdp.connections.intfs.CcdpTaskingControllerIntf;
import com.axios.ccdp.connections.intfs.CcdpVMControllerIntf;
import com.axios.ccdp.controllers.CcdpVMControllerAbs;
import com.axios.ccdp.utils.CcdpUtils;
import com.axios.ccdp.utils.SystemResourceMonitorAbs;
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
public class CcdpObjectFactory
{
  /**
   * A singleton object used to create the objects
   */
  private static CcdpObjectFactory factory = null;
  
  /**
   * Limiting access to this constructor is intentional in order to enforce the
   * use of a single factory object
   */
  private CcdpObjectFactory()
  {

  }
  
  /**
   * Returns an instance of the CcdpObjectFactory.  To assure that all the 
   * classes in the system use the same factory, this class follows the 
   * Singleton Design Pattern.
   *        
   * @return an instance of the Class specified by the clazz argument
   */
  public static CcdpObjectFactory newInstance()
  {
    // if we have not created a factory before do it, otherwise just return it
    if( CcdpObjectFactory.factory == null )
      CcdpObjectFactory.factory = new CcdpObjectFactory();
    
    return CcdpObjectFactory.factory;
  }
  
  
  /**
   * Instantiates a new object based on a classname representing it.  The class
   * must have a default constructor otherwise an error message is thrown.
   * 
   * @param key the full classname of the object to instantiate
   * 
   * @return a fully instantiated object based on the given classname
   * 
   * @throws RuntimeException a RuntimeException is thrown if the property is
   *         not found or if there is a problem instantiating the object
   */
  private Object getNewInstance( String key, Class<?> clazz )
  {
    String classname = CcdpUtils.getProperty(key );
    if( classname == null )
    {
      String msg = "The name of the interface is required.  Please " +
                   "make sure the configuration " + key + " is set properly";
      throw new RuntimeException( msg );
    }
    
    try
    {
      Class<?> instantiation = Class.forName(classname);
      Object obj = instantiation.newInstance();
      if( clazz.isInstance(obj) )
        return obj;
      else
      {
        
        String msg = "The classname " + clazz.getName() + 
                     " is not assignable to the given class";
        throw new RuntimeException( msg );
      }
    }
    catch( ClassNotFoundException e)
    {
      throw new RuntimeException("Class " + classname + " was not found" );
    }
    catch (InstantiationException e)
    {
      throw new RuntimeException("Could not instantiace object of type " + 
                                  classname  );
    }
    catch (IllegalAccessException e)
    {
      throw new RuntimeException("Illegal Access for " + classname );
    }
  }
  
  /**
   * Gets the object that is used communicate among elements in the system.  
   * The same interface is also used to send messages back to a specific 
   * destination
   * 
   * @param config a JSON Object containing required configuration parameters
   * @return an actual implementation of the object that allows the main 
   *         application to send and receive tasking events
   */
  public CcdpConnectionIntf getCcdpConnectionInterface(ObjectNode config)
  {
    String key = CcdpUtils.CFG_KEY_CONNECTION_CLASSNAME;
    Object obj = this.getNewInstance(key, CcdpConnectionIntf.class);
    CcdpConnectionIntf impl = (CcdpConnectionIntf)obj;
    impl.configure(config);
    return impl;
  }
  
  /**
   * Gets the object that is used to measure the resources in the node where  
   * the agent is running. 
   * 
   * @param config a JSON Object containing required configuration parameters
   * @return an actual implementation of the object that allows the agent 
   *         get the resources
   */
  public SystemResourceMonitorAbs getResourceMonitorInterface(ObjectNode config)
  {
    String key = CcdpUtils.CFG_KEY_RES_MON_CLASSNAME;
    Object obj = this.getNewInstance(key, SystemResourceMonitorAbs.class);
    SystemResourceMonitorAbs impl = (SystemResourceMonitorAbs)obj;
    impl.configure(config);
    return impl;
  }
  
  /**
   * Gets the object responsible for controlling the resources.  For instance, 
   * it starts and stops VMs, ask for status, etc.
   * 
   * @param config a JSON Object containing required configuration parameters
   * 
   * @return an actual implementation of the object that allows the scheduler
   *         to manipulate the resources
   */
  public CcdpVMControllerIntf getCcdpResourceController(ObjectNode config)
  {
    String key = CcdpUtils.CFG_KEY_RESOURCE_CLASSNAME;
    Object obj = this.getNewInstance(key,CcdpVMControllerIntf.class);
    CcdpVMControllerIntf impl = (CcdpVMControllerIntf)obj;
    impl.configure(config);
    return impl;
  }
  
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
  public CcdpStorageControllerIntf getCcdpStorageControllerIntf(ObjectNode config)
  {
    String key = CcdpUtils.CFG_KEY_STORAGE_CLASSNAME;
    Object obj = this.getNewInstance(key, CcdpStorageControllerIntf.class);
    CcdpStorageControllerIntf impl = (CcdpStorageControllerIntf)obj;
    impl.configure(config);
    return impl;
  }
  
  /**
   * Gets the object responsible for tasking the resources.  For instance, 
   * it will start a task based on a session-id, capacity, etc
   * 
   * @param config a JSON Object containing required configuration parameters
   * 
   * @return an actual implementation of the object that allows the scheduler
   *         to manipulate the tasking
   */
  public CcdpVMControllerAbs getCcdpTaskingController(ObjectNode config)
  {
    String key = CcdpUtils.CFG_KEY_TASKER_CLASSNAME;
    Object obj = this.getNewInstance(key, CcdpTaskingControllerIntf.class);
    CcdpVMControllerAbs impl = (CcdpVMControllerAbs)obj;
    impl.configure(config);
    return impl; 
  }
  
  /**
   * Gets the object responsible for accessing the database.  It stores, update,
   * and delete entries from the database
   * 
   * @param config a JSON Object containing required configuration parameters
   * @return an actual implementation of the object that allows the framework 
   *         to access the database
   */
  public CcdpDatabaseIntf getCcdpDatabaseIntf(ObjectNode config)
  {
    String key = CcdpUtils.CFG_KEY_DATABASE_CLASSNAME;
    Object obj = this.getNewInstance(key, CcdpDatabaseIntf.class);
    CcdpDatabaseIntf impl = (CcdpDatabaseIntf)obj;
    impl.configure(config);
    return impl;
  }
  
  
}
