package com.axios.ccdp.connections.intfs;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
   * Gets the unique id identifying this node.
   * 
   * @return the unique id identifying this node.
   */
  public String getUniqueHostId();
  
  /**
   * Gets the node's Operating System family (Windows, Linux) or null if it 
   * can't be obtained
   * 
   * @return the node's Operating System family (Windows, Linux) or null if it 
   *         can't be obtained
   */
  public String getOSFamily();
  
  /**
   * Gets the node's Operating System bit architecture (32, 64) or null if it 
   * can't be obtained
   * 
   * @return the node's Operating System bit architecture (32, 64) or null if 
   *         it can't be obtained
   */
  public int getOSBitArchitecture();

  /**
   * Gets the node's Operating System manufacturer (GNU, Microsoft) or null if 
   * it can't be obtained
   * 
   * @return the node's Operating System manufacturer (GNU, Microsoft) or null 
   *         if it can't be obtained
   */
  public String getOSManufacturer();
  
  /**
   * Gets the node's Operating System Version (7.6, 10 Pro) or null if 
   * it can't be obtained
   * 
   * @return the node's Operating System Version (7.6, 10 Pro) or null if 
   * it can't be obtained
   */
  public String getOSVersion();
  
  /**
   * Gets the node's Operating System code name (Core, N/A) or null if 
   * it can't be obtained
   * 
   * @return the node's Operating System code name (Core, N/A) or null if 
   *         it can't be obtained
   */
  public String getOSCodeName();
  
  /**
   * Gets the node's Operating System build number (7.6, 12345) or null if 
   * it can't be obtained
   * 
   * @return the node's Operating System build number (7.6, 12345) or null if 
   *         it can't be obtained
   */
  public String getOSBuildNumber();
  
  
  /**
   * Gets the total amount of virtual memory in the node or -1 if it can't 
   * be obtained
   * 
   * @return the total amount of virtual memory in the node or -1 if it can't 
   *         be obtained
   */
  public long getTotalVirtualMemorySize();

  /**
   * Gets the amount of virtual memory used in the node or -1 if it can't 
   * be obtained
   * 
   * @return the amount of virtual memory used in the node or -1 if it can't 
   *         be obtained
   */
  public long getUsedVirtualMemorySize();
  
  /**
   * Gets the amount of virtual memory available in the node or -1 if it can't 
   * be obtained
   * 
   * @return the amount of virtual memory available in the node or -1 if it  
   *         can't be obtained
   */
  public long getFreeVirtualMemorySize();
  
  /**
   * Returns the total amount of physical memory in bytes or -1 if the value  
   * cannot be obtained
   * 
   * @return the total amount of physical memory in bytes or -1 if the value  
   *         cannot be obtained
   */
  public long getTotalPhysicalMemorySize();
  
  /**
   * Returns the total amount of physical memory used in bytes or -1 if the   
   * value cannot be obtained
   * 
   * @return the total amount of physical memory in bytes or -1 if the value  
   *         cannot be obtained
   */
  public long getUsedPhysicalMemorySize();

  /**
   * Returns the amount of free physical memory in bytes or -1 if the value 
   * cannot be obtained
   * 
   * @return the amount of free physical memory in bytes or -1 if the value  
   *         cannot be obtained
   */
  public long getFreePhysicalMemorySize();
  
  /**
   * Gets the number of physical CPUs installed in the node or -1 if it can't 
   * be obtained
   * 
   * @return the number of physical CPUs installed in the node or -1 if it  
   *         can't be obtained
   */
  public int getPhysicalCPUCount();
  
  /**
   * Gets the number of physical CPUs cores in the node or -1 if it can't 
   * be obtained
   * 
   * @return the number of physical CPUs cores in the node or -1 if it  
   *         can't be obtained
   */
  public int getPhysicalCPUCoreCount();
  
  /**
   * Gets the number of logical (virtual) CPUs cores in the node or -1 if it  
   * can't be obtained
   * 
   * @return the number of logical (virtual) CPUs cores in the node or -1 if it  
   *         can't be obtained
   */
  public int getVirtualCPUCoreCount();
  
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
  public double[] getCPUCoresLoad();
  
  /**
   * Gets a list of all the File Systems names attached to the node or an empty
   * list if it can't obtain it.  Each name corresponds to the mount point name
   * 
   * @return list of all the file systems names attached to the node or an 
   *         empty list if it can't obtain it
   */
  public String[] getFileStorageNames();
  
  /**
   * Gets the total amount of storage space of the given partition.  If the 
   * name cannot be found then it return -1L
   * 
   * @param name the name of the storage to retrieve the information
   * 
   * @return the total amount of storage space of the given partition
   */
  public long getTotalStorageSpaceByName(String name);

  /**
   * Gets the total amount of used storage space of the given partition.  If 
   * the name cannot be found then it return -1L
   * 
   * @param name the name of the storage to retrieve the information
   * 
   * @return the total amount of used storage space of the given partition
   */
  public long getUsedStorageSpaceByName(String name);
  
  /**
   * Gets the total amount of free storage space of the given partition.  If  
   * the name cannot be found then it return -1L
   * 
   * @param name the name of the storage to retrieve the information
   * 
   * @return the total amount of free storage space of the given partition
   */
  public long getFreeStorageSpaceByName(String name);  

  
  /**
   * Gets map containing information about each of the partitions found in
   * the given disk name. Currently it contains the following information:
   * 
   * Name, Identification, Type, Size, MountPoint, TotalSpace, UsedSpace, 
   * FreeSpace
   * 
   * @param name the name of the disk to retrieve the information from
   * 
   * @return map containing information about each of the partitions found in
   *         the given disk name 
   */
  public Map<String, String> getDiskPartitionInfo(String name);

  /**
   * Gets the total amount of disk space of the root partition ('/' for Linux
   * based systems and 'c:' for Windows).  If the system does not have neither 
   * of the two partitions mentioned above then it return -1L
   * 
   * @param name the name of the disk to retrieve the information from
   * 
   * @return the total amount of disk space of the root partition
   */
  public long getTotalDiskSpace();
  
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
   * Gets the hostname of the node where this process was invoked or null if
   * it can't be obtained
   * 
   * @return the hostname of the node where this process was invoked or null if
   *         it can't be obtained
   */
  public String getHostName();
  
  /**
   * Gets the domain name of the node where this process was invoked or null if
   * it can't be obtained
   * 
   * @return the domain name of the node where this process was invoked or null 
   *         if it can't be obtained
   */
  public String getDomainName();
  
  /**
   * Gets a list of all the DNS serves used by this node or null if it can't be 
   * obtained
   * 
   * @return list of all the DNS serves used by this node or null if it can't 
   *         be obtained
   */
  public String[] getDNSServers();
  
  /**
   * Gets a list of all the network interfaces (cards) used by this node or 
   * null if it can't be obtained
   * 
   * @return list of all the network interfaces (cards) used by this node or 
   *         null if it can't be obtained
   */
  public String[] getNetworkInterfaces();
  
  /**
   * Gets map containing information about each of the interfaces found in
   * this node. Currently it contains the following information:
   * 
   * Name, MACAddress, MTU, Speed, PacketsSent, PacketsReceived, BytesSent, 
   * BytesReceived, InErrors, OutErrors
   * 
   * @param name the name of the disk to retrieve the information from
   * 
   * @return map containing information about each of the partitions found in
   *         the given disk name 
   */
  public Map<String, String> getNetworkInterfaceInfo( String name );
  
  
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
  
  /**
   * Closes all the connections and cleans all the resources it needs to clean
   * 
   */
  public void close();
  
}
