package com.axios.ccdp.utils;

import java.io.File;
import java.lang.management.ManagementFactory;
import java.lang.management.OperatingSystemMXBean;
import java.lang.reflect.Method;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.apache.log4j.Logger;

import com.axios.ccdp.connections.intfs.SystemResourceMonitorIntf;
import com.axios.ccdp.fmwk.CcdpMainApplication;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
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
public class LinuxResourceMonitorImpl implements SystemResourceMonitorIntf
{
  /**
   * Generates debug print statements based on the verbosity level.
   */
  private Logger logger = Logger.getLogger(LinuxResourceMonitorImpl.class
      .getName());
  
  /**
   * The Operating System implementation used to get all the resources
   * 
   */
  private OperatingSystemMXBean os;
  
  /**
   * Used to generate all the JSON structure objects
   */
  private ObjectMapper mapper = new ObjectMapper();

  /**
   * Stores the default units base to use
   */
  private long units = 1L;
  /**
   * The Root directory of the filesystem
   */
  private File filesystem = null;
  
  /**
   * Instantiates a new resource monitor
   */
  public LinuxResourceMonitorImpl()
  {
    this(UNITS.KB);
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
  public void configure( ObjectNode config )
  {
    String units = com.axios.ccdp.connections.intfs.SystemResourceMonitorIntf.UNITS.KB.toString();
    JsonNode node = config.get("units");
    
    if( node != null )
      units = node.asText();
    else
      this.logger.warn("The units was not defined using default (KB)");
    
    this.setUnits( units );
    
  }
  
  /**
   * Instantiates a new resource monitor
   * 
   * @param units the units to use when displaying some of the values
   */
  public LinuxResourceMonitorImpl( String units )
  {
    this(UNITS.valueOf(units));
  }
  
  /**
   * Instantiates a new resource monitor
   * 
   * @param units the units to use when displaying some of the values
   */
  public LinuxResourceMonitorImpl( UNITS units)
  {
    this.logger.debug("Initiating new Monitor");
    this.setUnits( units );
  }
  
  /**
   * Sets the units to used to represent the resources such as BYTE, KB, MB, 
   * and GB.  The string is a representation of an actual value of the enum 
   * class
   * 
   * @param units the string representation of the units to use to measure the 
   *        resources
   */
  public void setUnits( String units )
  {
    this.setUnits( UNITS.valueOf(units));
  }
  
  /**
   * Sets the units to used to represent the resources such as BYTE, KB, MB, 
   * and GB.  
   * 
   * @param units units to use to measure the resources
   */
  public void setUnits( UNITS units )
  {
    this.logger.debug("Initiating new Monitor");
    
    this.filesystem = new File("/");
    // if is not Linux or Mac, Windows?
    if( !this.filesystem.isDirectory() )
      this.filesystem = new File("c:");
    
    // if it does not exists then make sure we don't send wrong information
    if( !this.filesystem.isDirectory() )
      this.filesystem = null;
    
    this.units = SystemResourceMonitorIntf.getDivisor(units);
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
      return ( new Long((long)obj) ) / this.units;
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
      return ( new Long((long)obj) ) / this.units;
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
      return ( new Long((long)obj) ) / this.units;
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
    Object obj = this.getResource("c");
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
      return ( new Long((long)obj) ) / this.units;
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
  public long getTotalPhysicalMemorySize()
  {
    Object obj = this.getResource("getTotalPhysicalMemorySize");
    if( obj != null )
    {
      return ( new Long((long)obj) ) / this.units;
    }
    else
    {
      this.logger.error("Could not get Total Physical Memory Size");
    }
    
    return -1L;      
  }
  
  /**
   * Returns the total amount of physical memory used in bytes or -1 if the value  
   * cannot be obtained
   * 
   * @return the total amount of physical memory in bytes or -1 if the value  
   *         cannot be obtained
   */
  public long getUsedPhysicalMemorySize()
  {
    long total;
    long free;
    
    Object obj = this.getResource("getTotalPhysicalMemorySize");
    if( obj != null )
    {
      total =  (long)obj ;
    }
    else
    {
      this.logger.error("Could not get Total Physical Memory Size");
      return -1L;
    }
    
    obj = this.getResource("getFreePhysicalMemorySize");
    if( obj != null )
    {
      free =  (long)obj ;
    }
    else
    {
      this.logger.error("Could not get Total Physical Memory Size");
      return -1L;
    }
    
    return ( total - free ) / this.units;
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
   * Gets the total number of CPU or cores available in this machine
   * 
   * @return the total number of CPU or cores available in this machine
   */
  public int getTotalNumberCpuCores()
  {
    return Runtime.getRuntime().availableProcessors();
  }
  
  /**
   * Gets the total amount of disk space of the root partition ('/' for Linux
   * based systems and 'c:' for Windows).  If the system does not have neither 
   * of the two partitions mentioned above then it return -1L
   * 
   * @return the total amount of disk space of the root partition
   */
  public long getTotalDiskSpace()
  {
    if( this.filesystem != null )
      return this.filesystem.getTotalSpace() / this.units ;
    else
      return -1L;
  }

  /**
   * Gets the total usable amount of disk space of the root partition ('/' for
   * Linux based systems and 'c:' for Windows).  If the system does not have
   * neither of the two partitions mentioned above then it return -1L
   * 
   * @return the total usable amount of disk space of the root partition
   */
  public long getUsableDiskSpace()
  {
    if( this.filesystem != null )
      return this.filesystem.getUsableSpace() / this.units ;
    else
      return -1L;
  }

  /**
   * Gets the total amount of disk space of the root partition ('/' for Linux
   * based systems and 'c:' for Windows) that is being used .  If the system 
   * does not have neither of the two partitions mentioned above then it 
   * return -1L
   * 
   * @return the total amount of disk space of the root partition being used
   */
  public long getUsedDiskSpace()
  {
    if( this.filesystem != null )
      return (this.getTotalDiskSpace() - this.getFreeDiskSpace()) / this.units ;
    else
      return -1L;
  }
  
  /**
   * Gets the total free amount of disk space of the root partition ('/' for
   * Linux based systems and 'c:' for Windows).  If the system does not have
   * neither of the two partitions mentioned above then it return -1L
   * 
   * @return the total free amount of disk space of the root partition
   */
  public long getFreeDiskSpace()
  {
    if( this.filesystem != null )
      return this.filesystem.getFreeSpace() / this.units ;
    else
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

    ObjectNode node = this.toJSON();
    String str = node.toString();
    try
    {
      str = 
          this.mapper.writerWithDefaultPrettyPrinter().writeValueAsString(node);
    }
    catch( JsonProcessingException e )
    {
      throw new RuntimeException("Could not write Json " + e.getMessage() );
    }
    
    return str;
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
    
    json.put("TotalDiskSpace", 
        this.getTotalDiskSpace());
    json.put("UsableDiskSpace", 
        this.getUsableDiskSpace());
    json.put("FreeDiskSpace", 
        this.getFreeDiskSpace());
    json.put("CommittedVirtualMemorySize", 
             this.getCommittedVirtualMemorySize());
    json.put("TotalSwapSpaceSize", 
        this.getTotalSwapSpaceSize());
    json.put("FreeSwapSpaceSize", 
        this.getFreeSwapSpaceSize());
    json.put("TotalNumberCpuCores", 
        this.getTotalNumberCpuCores());
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
   * Does not perform any action
   */
  public void close()
  {
    this.logger.debug("Nothing to close or clean");
  }
  
  /**
   * Gets the unique id identifying this node.
   * 
   * @return the unique id identifying this node.
   */
  public String getUniqueHostId()
  {
    String hostId = null;
    
    try
    {
      this.logger.debug("Retrieving Instance ID");
      hostId = CcdpUtils.retrieveEC2InstanceId();
    }
    catch( Exception e )
    {
      this.logger.error("Could not retrieve Instance ID");
      String[] uid = UUID.randomUUID().toString().split("-");
      hostId = CcdpMainApplication.VM_TEST_PREFIX + "-" + uid[uid.length - 1];
    }
    
    return hostId;
  }
  
  /**
   * 
   * Runs the show, used only as a quick way to test the methods.
   * 
   * @param args the command line arguments, not used
   */
  public static void main( String[] args)
  {
    CcdpUtils.configLogger();
    
    LinuxResourceMonitorImpl srm = new LinuxResourceMonitorImpl(UNITS.KB);
    
    
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
