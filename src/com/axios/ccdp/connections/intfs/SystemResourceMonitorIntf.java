package com.axios.ccdp.connections.intfs;

import java.util.HashMap;
import java.util.Map;

import com.axios.ccdp.connections.intfs.SystemResourceMonitorIntf.UNITS;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Simple utility interface used to obtain some of the resource utilization 
 * information from the operating system.  
 * 
 * The methods used here to get the information cannot be accessed directly and
 * therefore this class uses Reflection in order to get those values. It uses 
 * the 'sun.management.OperatingSystemImpl' class to query the OS.
 * 
 * @author Oscar E. Ganteaume
 *
 */
public interface SystemResourceMonitorIntf
{
  /**
   * Stores the units to return some of the values such as memory and disk space
   * 
   * @author Oscar E. Ganteaume
   *
   */
  public static enum UNITS { BYTE, KB, MB, GB };
  /**
   * Stores all the different long values based on the units specification
   */
  public static Map<UNITS, Long> DIVISORS = new HashMap<>();
  /**
   * Gets the numerical value to use when doing conversion between units
   * 
   * @param units the units to use when reporting resources
   * @return the actual numerical value to perform the calculations
   */
  public static long getDivisor(UNITS units)
  {
    if( !SystemResourceMonitorIntf.DIVISORS.isEmpty() )
      return SystemResourceMonitorIntf.DIVISORS.get(units);
    
    SystemResourceMonitorIntf.DIVISORS.put( UNITS.BYTE, new Long(1) );
    SystemResourceMonitorIntf.DIVISORS.put( UNITS.KB, new Long(1024) );
    SystemResourceMonitorIntf.DIVISORS.put( UNITS.MB, new Long(1024*1024) );
    SystemResourceMonitorIntf.DIVISORS.put( UNITS.GB, new Long(1024*1024*1024) );
    
    return SystemResourceMonitorIntf.DIVISORS.get(units);
  }
  
  /**
   * Configures the running environment and/or connections required to perform
   * the operations.  The JSON Object contains all the different fields 
   * necessary to operate.  These fields might change on each actual 
   * implementation
   * 
   * @param config a JSON Object containing all the necessary fields required 
   *        to operate
   */
  public void configure( ObjectNode config );
  
  /**
   * Sets the units to used to represent the resources such as BYTE, KB, MB, 
   * and GB.  The string is a representation of an actual value of the enum 
   * class
   * 
   * @param units the string representation of the units to use to measure the 
   *        resources
   */
  public void setUnits( String units );
  
  /**
   * Sets the units to used to represent the resources such as BYTE, KB, MB, 
   * and GB.  
   * 
   * @param units units to use to measure the resources
   */
  public void setUnits( UNITS units );
  
  /**
   * Returns the amount of virtual memory that is guaranteed to be available 
   * to the running process in bytes, or -1 if this operation is not supported.
   * 
   * @return the amount of virtual memory that is guaranteed to be available 
   *         to the running process in bytes, or -1 if this operation is not 
   *         supported.
   */
  public long getCommittedVirtualMemorySize();
  
  /**
   * Returns the total amount of swap space in bytes or -1 if the value cannot 
   * be obtained
   * 
   * @return the total amount of swap space in bytes or -1 if the value cannot 
   *         be obtained
   */
  public long getTotalSwapSpaceSize();
  
  /**
   * Returns the amount of free swap space in bytes or -1 if the value cannot 
   * be obtained
   * 
   * @return the amount of free swap space in bytes or -1 if the value cannot 
   *         be obtained
   */
  public long getFreeSwapSpaceSize();
  
  /**
   * Returns the CPU time used by the process on which the Java virtual machine 
   * is running in nanoseconds. The returned value is of nanoseconds precision 
   * but not necessarily nanoseconds accuracy. This method returns -1 if the 
   * the platform does not support this operation.
   * 
   * @return the CPU time used by the process in nanoseconds, or -1 if this 
   *         operation is not supported.
   */
  public long getProcessCpuTime();
  
  /**
   * Returns the amount of free physical memory in bytes or -1 if the value 
   * cannot be obtained
   * 
   * @return the amount of free physical memory in bytes or -1 if the value  
   *         cannot be obtained
   */
  public long getFreePhysicalMemorySize();
  
  /**
   * Returns the total amount of physical memory in bytes or -1 if the value  
   * cannot be obtained
   * 
   * @return the total amount of physical memory in bytes or -1 if the value  
   *         cannot be obtained
   */
  public long getTotalPhysicalMemorySize();
  
  /**
   * Returns the total amount of physical memory used in bytes or -1 if the value  
   * cannot be obtained
   * 
   * @return the total amount of physical memory in bytes or -1 if the value  
   *         cannot be obtained
   */
  public long getUsedPhysicalMemorySize();
  
  
  /**
   * Returns the number of open file descriptors or -1 if the value cannot 
   * be obtained
   * 
   * @return the number of open file descriptors or -1 if the value cannot 
   *         be obtained
   */
  public long getOpenFileDescriptorCount();
  
  /**
   * Returns the maximum number of file descriptors or -1 if the value cannot 
   * be obtained
   * 
   * @return the maximum number of file descriptors or -1 if the value cannot 
   *         be obtained
   */
  public long getMaxFileDescriptorCount();
  
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
  public double getSystemCpuLoad();
  
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
  public double getProcessCpuLoad();
  
  /**
   * Gets the total number of CPU or cores available in this machine
   * 
   * @return the total number of CPU or cores available in this machine
   */
  public int getTotalNumberCpuCores();
  
  /**
   * Gets the total amount of disk space of the root partition ('/' for Linux
   * based systems and 'c:' for Windows).  If the system does not have neither 
   * of the two partitions mentioned above then it return -1L
   * 
   * @return the total amount of disk space of the root partition
   */
  public long getTotalDiskSpace();
  
  /**
   * Gets the total usable amount of disk space of the root partition ('/' for
   * Linux based systems and 'c:' for Windows).  If the system does not have
   * neither of the two partitions mentioned above then it return -1L
   * 
   * @return the total usable amount of disk space of the root partition
   */
  public long getUsableDiskSpace();
  /**
   * Gets the total amount of disk space of the root partition ('/' for Linux
   * based systems and 'c:' for Windows) that is being used .  If the system 
   * does not have neither of the two partitions mentioned above then it 
   * return -1L
   * 
   * @return the total amount of disk space of the root partition being used
   */
  public long getUsedDiskSpace();
  
  /**
   * Gets the total free amount of disk space of the root partition ('/' for
   * Linux based systems and 'c:' for Windows).  If the system does not have
   * neither of the two partitions mentioned above then it return -1L
   * 
   * @return the total free amount of disk space of the root partition
   */
  public long getFreeDiskSpace();  

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
  public ObjectNode toJSON();
  
}
