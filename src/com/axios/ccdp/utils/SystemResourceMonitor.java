package com.axios.ccdp.utils;

import java.lang.management.ManagementFactory;
import java.lang.management.OperatingSystemMXBean;
import java.lang.reflect.Method;

import org.apache.log4j.Logger;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Simple utility class used to obtain some of the resource utilization 
 * information from the operating system.  
 * 
 * The methods used here to get the information cannot be accessed directly and
 * therefore this class uses Reflection in order to get those values. It uses 
 * the 'sun.management.OperatingSystemImpl' class to query the OS.
 * 
 * @author Oscar E. Ganteaume
 *
 */
public class SystemResourceMonitor
{
  /**
   * Generates debug print statements based on the verbosity level.
   */
  private Logger logger = Logger.getLogger(SystemResourceMonitor.class
      .getName());

  /**
   * The Operating System implementation used to get all the resources
   * 
   */
  private OperatingSystemMXBean os;
  
  /**
   * Instantiates a new resource monitor
   */
  public SystemResourceMonitor()
  {
    this.logger.debug("Initiating new Monitor");
    this.os = ManagementFactory.getOperatingSystemMXBean();
  }
  
  /**
   * Returns the amount of virtual memory that is guaranteed to be available 
   * to the running process in bytes, or -1 if this operation is not supported.
   * 
   * @return the amount of virtual memory that is guaranteed to be available 
   *         to the running process in bytes, or -1 if this operation is not 
   *         supported.
   */
  public long getCommittedVirtualMemorySize()
  {
    Object obj = this.getResource("getCommittedVirtualMemorySize");
    if( obj != null )
    {
      return new Long((long)obj);
    }
    else
    {
      this.logger.error("Could not get Committed Virtual Memory Size");
    }
    
    return -1L;
  }
  
  /**
   * Returns the total amount of swap space in bytes or -1 if the value cannot 
   * be obtained
   * 
   * @return the total amount of swap space in bytes or -1 if the value cannot 
   *         be obtained
   */
  public long getTotalSwapSpaceSize()
  {
    Object obj = this.getResource("getTotalSwapSpaceSize");
    if( obj != null )
    {
      return new Long((long)obj);
    }
    else
    {
      this.logger.error("Could not get Total Swap Space Size");
    }
    
    return -1L;    
  }
  
  /**
   * Returns the amount of free swap space in bytes or -1 if the value cannot 
   * be obtained
   * 
   * @return the amount of free swap space in bytes or -1 if the value cannot 
   *         be obtained
   */
  public long getFreeSwapSpaceSize()
  {
    Object obj = this.getResource("getFreeSwapSpaceSize");
    if( obj != null )
    {
      return new Long((long)obj);
    }
    else
    {
      this.logger.error("Could not get Free Swap Space Size");
    }
    
    return -1L;     
  }
  
  /**
   * Returns the CPU time used by the process on which the Java virtual machine 
   * is running in nanoseconds. The returned value is of nanoseconds precision 
   * but not necessarily nanoseconds accuracy. This method returns -1 if the 
   * the platform does not support this operation.
   * 
   * @return the CPU time used by the process in nanoseconds, or -1 if this 
   *         operation is not supported.
   */
  public long getProcessCpuTime()
  {
    Object obj = this.getResource("getProcessCpuTime");
    if( obj != null )
    {
      return new Long((long)obj);
    }
    else
    {
      this.logger.error("Could not get Process CPU Time");
    }
    
    return -1L;
  }
  
  /**
   * Returns the amount of free physical memory in bytes or -1 if the value 
   * cannot be obtained
   * 
   * @return the amount of free physical memory in bytes or -1 if the value  
   *         cannot be obtained
   */
  public long getFreePhysicalMemorySize()
  {
    Object obj = this.getResource("getFreePhysicalMemorySize");
    if( obj != null )
    {
      return new Long((long)obj);
    }
    else
    {
      this.logger.error("Could not get Free Physical Memory Size");
    }
    
    return -1L;    
  }
  
  /**
   * Returns the total amount of physical memory in bytes or -1 if the value  
   * cannot be obtained
   * 
   * @return the total amount of physical memory in bytes or -1 if the value  
   *         cannot be obtained
   */
  public double getTotalPhysicalMemorySize()
  {
    Object obj = this.getResource("getTotalPhysicalMemorySize");
    if( obj != null )
    {
      return new Long((long)obj);
    }
    else
    {
      this.logger.error("Could not get Total Physical Memory Size");
    }
    
    return -1L;      
  }
  
  /**
   * Returns the number of open file descriptors or -1 if the value cannot 
   * be obtained
   * 
   * @return the number of open file descriptors or -1 if the value cannot 
   *         be obtained
   */
  public long getOpenFileDescriptorCount()
  {
    Object obj = this.getResource("getOpenFileDescriptorCount");
    if( obj != null )
    {
      return new Long((long)obj);
    }
    else
    {
      this.logger.error("Could not get Open File Descriptor Count");
    }
    
    return -1L;  
  }
  
  /**
   * Returns the maximum number of file descriptors or -1 if the value cannot 
   * be obtained
   * 
   * @return the maximum number of file descriptors or -1 if the value cannot 
   *         be obtained
   */
  public long getMaxFileDescriptorCount()
  {
    Object obj = this.getResource("getMaxFileDescriptorCount");
    if( obj != null )
    {
      return new Long((long)obj);
    }
    else
    {
      this.logger.error("Could not get Max File Descriptor Count");
    }
    
    return -1L;  
  }
  
  /**
   * Returns the "recent cpu usage" for the whole system. This value is a 
   * double in the [0.0,1.0] interval. A value of 0.0 means that all CPUs were 
   * idle during the recent period of time observed, while a value of 1.0 means 
   * that all CPUs were actively running 100% of the time during the recent 
   * period being observed. All values betweens 0.0 and 1.0 are possible 
   * depending of the activities going on in the system. If the system recent 
   * cpu usage is not available, the method returns a negative value.
   * 
   * @return the "recent cpu usage" for the whole system; a negative value if 
   *         not available.
   */
  public double getSystemCpuLoad()
  {
    Object obj = this.getResource("getSystemCpuLoad");
    if( obj != null )
    {
      return new Double((double)obj);
    }
    else
    {
      this.logger.error("Could not get System CPU Load");
    }
    
    return -1L;  
  }
  
  /**
   * Returns the "recent cpu usage" for the Java Virtual Machine process. This 
   * value is a double in the [0.0,1.0] interval. A value of 0.0 means that 
   * none of the CPUs were running threads from the JVM process during the 
   * recent period of time observed, while a value of 1.0 means that all CPUs 
   * were actively running threads from the JVM 100% of the time during the 
   * recent period being observed. Threads from the JVM include the application 
   * threads as well as the JVM internal threads. All values betweens 0.0 and 
   * 1.0 are possible depending of the activities going on in the JVM process 
   * and the whole system. If the Java Virtual Machine recent CPU usage is not 
   * available, the method returns a negative value.
   * 
   * @return the "recent cpu usage" for the Java Virtual Machine process; a 
   *         negative value if not available.
   */
  public double getProcessCpuLoad()
  {
    
    Object obj = this.getResource("getProcessCpuLoad");
    if( obj != null )
    {
      return new Double((double)obj * 100);
    }
    else
    {
      this.logger.error("Could not get Process CPU Load");
    }
    
    return -1L;
  }
  
  /**
   * Gets the value of a given resource usage or availability.  Java does not 
   * allow direct access to the sun.management.OperatingSystemImpl class so I 
   * had to use reflection to invoke the different methods in this class.  The
   * method name used in this class matches the ones used by the 
   * OperatingSystemImpl class. If the method cannot be accessed or does not 
   * exists it returns null.
   * 
   * @param methodName the name of the method to invoke
   *  
   * @return the value from calling the method or null if it does not exists or 
   *         it cannot be invoked
   */
  private Object getResource( String methodName )
  {
    try
    {
      // first let's get the method and make it accessible
      Method method = this.os.getClass().getMethod(methodName);
      method.setAccessible(true);
      // now get the value
      return method.invoke(this.os);
    }
    catch( Exception e )
    {
      this.logger.error("Got an error " + e.getMessage());
      return null;
    }
  }
  
  /**
   * Returns a string representation of a JSON object where each key is the same
   * as the method without the 'get' and the value is the actual value
   * 
   * @return a JSON representation string of the OS
   */
  public String toString()
  {

    return this.toJSON().toString();
  }
  
  /**
   * Gets a JSON object representing the object.  The following are the keys
   * 
   *  - CommittedVirtualMemorySize
   *  - TotalSwapSpaceSize
   *  - FreeSwapSpaceSize
   *  - ProcessCpuTime
   *  - FreePhysicalMemorySize
   *  - TotalPhysicalMemorySize
   *  - OpenFileDescriptorCount
   *  - MaxFileDescriptorCount
   *  - SystemCpuLoad
   *  - ProcessCpuLoad
   *   
   * 
   * @return a JSON object representing an instance of this class
   */
  public ObjectNode toJSON()
  {
    ObjectMapper mapper = new ObjectMapper();
    ObjectNode json = mapper.createObjectNode();
    
    json.put("CommittedVirtualMemorySize", 
             this.getCommittedVirtualMemorySize());
    json.put("TotalSwapSpaceSize", 
        this.getTotalSwapSpaceSize());
    json.put("FreeSwapSpaceSize", 
        this.getFreeSwapSpaceSize());
    json.put("ProcessCpuTime", 
        this.getProcessCpuTime());
    json.put("FreePhysicalMemorySize", 
        this.getFreePhysicalMemorySize());
    json.put("TotalPhysicalMemorySize", 
        this.getTotalPhysicalMemorySize());
    json.put("OpenFileDescriptorCount", 
        this.getOpenFileDescriptorCount());
    json.put("MaxFileDescriptorCount", 
        this.getMaxFileDescriptorCount());
    json.put("SystemCpuLoad", 
        this.getSystemCpuLoad());
    json.put("ProcessCpuLoad", 
        this.getProcessCpuLoad());
    
    return json;
  }
  
  /**
   * Runs the show, used only as a quick way to test the methods.
   * 
   * @param args the command line arguments, not used
   */
  public static void main( String[] args)
  {
    CcdpUtils.configLogger();
    
    SystemResourceMonitor srm = new SystemResourceMonitor();
    
    
//    while( true )
//    {
      System.out.println("");
      System.out.println("***************************************************");
      System.out.println(srm.toString());
      System.out.println("***************************************************");
      System.out.println("");
      
      CcdpUtils.pause(1);
//    }
  }
}
