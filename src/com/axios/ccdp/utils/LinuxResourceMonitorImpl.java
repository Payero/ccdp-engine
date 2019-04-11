package com.axios.ccdp.utils;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.apache.log4j.Logger;

import com.axios.ccdp.fmwk.CcdpMainApplication;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import oshi.SystemInfo;
import oshi.hardware.CentralProcessor;
import oshi.hardware.GlobalMemory;
import oshi.hardware.HardwareAbstractionLayer;
import oshi.hardware.NetworkIF;
import oshi.software.os.OSFileStore;
import oshi.software.os.OperatingSystem;

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
public class LinuxResourceMonitorImpl implements SystemResourceMonitorAbs
{
  /**
   * Generates debug print statements based on the verbosity level.
   */
  private Logger logger = Logger.getLogger(LinuxResourceMonitorImpl.class
      .getName());
  
  /**
   * Retrieves all the information possible for this node
   */
  private SystemInfo system_info = new SystemInfo();

  /**
   * Handles all the OS information queries to the node
   * 
   */
  private OperatingSystem os = null;
  
  /**
   * Handles all the hardware information queries to the node
   * 
   */
  private HardwareAbstractionLayer hardware = null;
  
  /**
   * Handles all CPU related information
   */
  private CentralProcessor processor = null;
  
  /**
   * Handles all the memory related information
   */
  private GlobalMemory memory = null;
  
  /**
   * Used to generate all the JSON structure objects
   */
  private ObjectMapper mapper = new ObjectMapper();

  /**
   * Stores the default units base to use
   */
  private long units = 1L;
  
  /**
   * Stores previous ticks to calculate CPU load
   */
  private long[] prevTicks;

  /**
   * Stores previous ticks to calculate CPU load for each one of the cores
   */
  private long[][] prevProcTicks;
  
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
    String units = SystemResourceMonitorAbs.UNITS.KB.toString();
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
  public LinuxResourceMonitorImpl( UNITS units )
  {
    this.logger.debug("Initiating new Monitor");
    this.setUnits( units );
    
    this.os = this.system_info.getOperatingSystem();
    this.hardware = this.system_info.getHardware();
    this.processor = this.hardware.getProcessor();
    this.memory = this.hardware.getMemory();
    this.processor.updateAttributes();
    this.prevTicks = this.processor.getSystemCpuLoadTicks();
    this.prevProcTicks = this.processor.getProcessorCpuLoadTicks();
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
    this.setUnits( UNITS.valueOf(units) );
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
    this.units = SystemResourceMonitorAbs.getDivisor(units);
  }
  
  
  /**
   * Gets the node's Operating System family (Windows, Linux) or null if it 
   * can't be obtained
   * 
   * @return the node's Operating System family (Windows, Linux) or null if it 
   *         can't be obtained
   */
  public String getOSFamily()
  {
    return this.os.getFamily();
  }
  
  /**
   * Gets the node's Operating System bit architecture (32, 64) or null if it 
   * can't be obtained
   * 
   * @return the node's Operating System bit architecture (32, 64) or null if 
   *         it can't be obtained
   */
  public int getOSBitArchitecture()
  {
    return this.os.getBitness();
  }

  /**
   * Gets the node's Operating System manufacturer (GNU, Microsoft) or null if 
   * it can't be obtained
   * 
   * @return the node's Operating System manufacturer (GNU, Microsoft) or null 
   *         if it can't be obtained
   */
  public String getOSManufacturer()
  {
    return this.os.getManufacturer();
  }
  
  /**
   * Gets the node's Operating System Version (7.6, 10 Pro) or null if 
   * it can't be obtained
   * 
   * @return the node's Operating System Version (7.6, 10 Pro) or null if 
   * it can't be obtained
   */
  public String getOSVersion()
  {
    return this.os.getVersion().getVersion();
  }

  /**
   * Gets the node's Operating System code name (Core, N/A) or null if 
   * it can't be obtained
   * 
   * @return the node's Operating System code name (Core, N/A) or null if 
   *         it can't be obtained
   */
  public String getOSCodeName()
  {
    return this.os.getVersion().getCodeName();
  }
  
  /**
   * Gets the node's Operating System build number (7.6, 12345) or null if 
   * it can't be obtained
   * 
   * @return the node's Operating System build number (7.6, 12345) or null if 
   *         it can't be obtained
   */
  public String getOSBuildNumber()
  {
    return this.os.getVersion().getBuildNumber();
  }
  
  /**
   * Gets the total amount of virtual memory in the node or -1 if it can't 
   * be obtained
   * 
   * @return the total amount of virtual memory in the node or -1 if it can't 
   *         be obtained
   */
  public long getTotalVirtualMemorySize()
  {
    long total = this.memory.getVirtualMemory().getSwapTotal();
    return ( Long.valueOf( total ) ) / this.units;
  }

  /**
   * Gets the amount of virtual memory used in the node or -1 if it can't 
   * be obtained
   * 
   * @return the amount of virtual memory used in the node or -1 if it can't 
   *         be obtained
   */
  public long getUsedVirtualMemorySize()
  {
    long used = this.memory.getVirtualMemory().getSwapUsed();
    return ( Long.valueOf( used ) ) / this.units;
  }
  
  /**
   * Gets the amount of virtual memory available in the node or -1 if it can't 
   * be obtained
   * 
   * @return the amount of virtual memory available in the node or -1 if it  
   *         can't be obtained
   */
  public long getFreeVirtualMemorySize()
  {
    long total = this.memory.getVirtualMemory().getSwapTotal();
    long used = this.memory.getVirtualMemory().getSwapUsed();
    
    return ( Long.valueOf( total - used ) ) / this.units;
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
    long total = this.memory.getTotal(); 
    return ( Long.valueOf( total ) / this.units );
  }
  
  /**
   * Returns the total amount of physical memory used in bytes or -1 if the   
   * value cannot be obtained
   * 
   * @return the total amount of physical memory in bytes or -1 if the value  
   *         cannot be obtained
   */
  public long getUsedPhysicalMemorySize()
  {
    long total = this.memory.getTotal();
    long avail = this.memory.getAvailable();
    return ( Long.valueOf( total - avail ) / this.units );
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
    long avail = this.memory.getAvailable();
    return ( Long.valueOf( avail ) / this.units );
  }
  
  /**
   * Gets the number of physical CPUs installed in the node or -1 if it can't 
   * be obtained
   * 
   * @return the number of physical CPUs installed in the node or -1 if it  
   *         can't be obtained
   */
  public int getPhysicalCPUCount()
  {
    return this.processor.getPhysicalPackageCount();
  }
  
  /**
   * Gets the number of physical CPUs cores in the node or -1 if it can't 
   * be obtained
   * 
   * @return the number of physical CPUs cores in the node or -1 if it  
   *         can't be obtained
   */
  public int getPhysicalCPUCoreCount()
  {
    return this.processor.getPhysicalProcessorCount();
  }
  
  /**
   * Gets the number of logical (virtual) CPUs cores in the node or -1 if it  
   * can't be obtained
   * 
   * @return the number of logical (virtual) CPUs cores in the node or -1 if it  
   *         can't be obtained
   */
  public int getVirtualCPUCoreCount()
  {
    return this.processor.getLogicalProcessorCount();
  }
  
  /**
   * Gets the load of the Central CPU as a number between 0 and 1.  It uses the
   * tick count to generate the result
   * 
   * @return the load of the Central CPU as a number between 0 and 1
   * 
   */
  public double getSystemCpuLoad()
  {
    this.processor.updateAttributes();
    double val = this.processor.getSystemCpuLoadBetweenTicks(this.prevTicks);
    this.prevTicks = this.processor.getSystemCpuLoadTicks();
    this.prevProcTicks = this.processor.getProcessorCpuLoadTicks();
    return val;
  }
  
  /**
   * Gets the load of each core as a number between 0 and 1.  It uses the tick
   * count to generate the result.  The result is a list of double values, one
   * for each core present
   * 
   * @return the load of each core as a number between 0 and 1
   * 
   */
  public double[] getCPUCoresLoad()
  {
    this.processor.updateAttributes();
    
    double[] val = 
        this.processor.getProcessorCpuLoadBetweenTicks(this.prevProcTicks);
    this.prevTicks = this.processor.getSystemCpuLoadTicks();
    this.prevProcTicks = this.processor.getProcessorCpuLoadTicks();
    return val;
  }
  
  /**
   * Gets a list of all the File Systems names attached to the node or an empty
   * list if it can't obtain it.  Each name corresponds to the mount point name
   * 
   * @return list of all the file systems names attached to the node or an 
   *         empty list if it can't obtain it
   */
  public String[] getFileStorageNames()
  {
    OSFileStore[] stores = this.os.getFileSystem().getFileStores();
    
    String[] names = new String[stores.length];
    for( int i = 0; i < stores.length; i++ )
    {
      OSFileStore store = stores[i];
      names[i] = store.getMount();
    }
    return names;
  }
  
  /**
   * Gets the total amount of storage space of the given partition.  If the 
   * name cannot be found then it return -1L
   * 
   * @param name the name of the storage to retrieve the information
   * 
   * @return the total amount of storage space of the given partition
   */
  public long getTotalStorageSpaceByName(String name)
  {
    OSFileStore store = this.getFileStore(name);
    if( store != null )
      return store.getTotalSpace() / this.units;
    else
      return -1;
  }

  /**
   * Gets the total amount of used storage space of the given partition.  If 
   * the name cannot be found then it return -1L
   * 
   * @param name the name of the storage to retrieve the information
   * 
   * @return the total amount of used storage space of the given partition
   */
  public long getUsedStorageSpaceByName(String name)
  {
    OSFileStore store = this.getFileStore(name);
    
    if( store != null )
      return (( store.getTotalSpace() - store.getUsableSpace()) / this.units );
    else
      return -1;
  }
  
  /**
   * Gets the total amount of free storage space of the given partition.  If  
   * the name cannot be found then it return -1L
   * 
   * @param name the name of the storage to retrieve the information
   * 
   * @return the total amount of free storage space of the given partition
   */
  public long getFreeStorageSpaceByName(String name)
  {
    OSFileStore store = this.getFileStore(name);
    if( store != null )
      return store.getUsableSpace() / this.units;
    else
      return -1;
  }

  
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
  public Map<String, String> getDiskPartitionInfo(String name)
  {
    OSFileStore store = this.getFileStore(name);
    Map<String, String> map = new HashMap<>();
    if( store != null )
    {
      long total = store.getTotalSpace();
      long free = store.getUsableSpace();
          
      map.put("Name", store.getName() );
      map.put("Volume", store.getVolume() );
      map.put("Type", store.getType() );
      map.put("Mount", store.getMount() );
      map.put("Total", Long.toString( total / this.units ) );
      map.put("Available", Long.toString( free / this.units ) );
      return map;
    }
    
    return null;
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
    OSFileStore store = this.getFileStore();
    if( store != null )
      return store.getTotalSpace() / this.units;
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
    OSFileStore store = this.getFileStore();
    if( store != null )
      return (( store.getTotalSpace() - store.getUsableSpace()) / this.units );
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
    OSFileStore store = this.getFileStore();
    if( store != null )
      return store.getUsableSpace() / this.units;
    else
      return -1L;
  }

  /**
   * Gets the hostname of the node where this process was invoked or null if
   * it can't be obtained
   * 
   * @return the hostname of the node where this process was invoked or null if
   *         it can't be obtained
   */
  public String getHostName()
  {
    return this.os.getNetworkParams().getHostName();
  }
  
  /**
   * Gets the domain name of the node where this process was invoked or null if
   * it can't be obtained
   * 
   * @return the domain name of the node where this process was invoked or null 
   *         if it can't be obtained
   */
  public String getDomainName()
  {
    return this.os.getNetworkParams().getDomainName();
  }
  
  /**
   * Gets a list of all the DNS serves used by this node or null if it can't be 
   * obtained
   * 
   * @return list of all the DNS serves used by this node or null if it can't 
   *         be obtained
   */
  public String[] getDNSServers()
  {
    return this.os.getNetworkParams().getDnsServers();
  }
  
  /**
   * Gets a list of all the network interfaces (cards) used by this node or 
   * null if it can't be obtained
   * 
   * @return list of all the network interfaces (cards) used by this node or 
   *         null if it can't be obtained
   */
  public String[] getNetworkInterfaces()
  {
    NetworkIF[] nw = this.hardware.getNetworkIFs();
    String[] names = new String[nw.length];
    for(int i = 0; i < nw.length; i++ )
    {
      names[i] = nw[i].getDisplayName();
    }
    return names;
  }
  
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
  public Map<String, String> getNetworkInterfaceInfo( String name )
  {
    NetworkIF[] nw = this.hardware.getNetworkIFs();
    for( NetworkIF intrf : nw )
    {
      if( intrf.getDisplayName().equals(name) )
      {
        Map<String, String> map = new HashMap<>();
        map.put("Name", intrf.getDisplayName() );
        map.put("MACAddress", intrf.getMacaddr() );
        map.put("MTU", Integer.toString( intrf.getMTU() ) );
        map.put("Speed", Long.toString( intrf.getSpeed() ) );
        map.put("PacketsSent", Long.toString( intrf.getPacketsSent() ) );
        map.put("PacketsReceived", Long.toString( intrf.getPacketsRecv() ) );
        map.put("BytesSent", Long.toString( intrf.getBytesSent() ) );
        map.put("BytesReceived", Long.toString( intrf.getBytesRecv() ) );
        map.put("InErrors", Long.toString( intrf.getInErrors() ) );
        map.put("OutErrors", Long.toString( intrf.getOutErrors() ) );
        
        return map;
      }
    }
    return null;
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
    return null;
//    ObjectMapper mapper = new ObjectMapper();
//    ObjectNode json = mapper.createObjectNode();
//    
//    json.put("TotalDiskSpace", 
//        this.getTotalDiskSpace());
//    json.put("UsableDiskSpace", 
//        this.getUsableDiskSpace());
//    json.put("FreeDiskSpace", 
//        this.getFreeDiskSpace());
//    json.put("CommittedVirtualMemorySize", 
//             this.getCommittedVirtualMemorySize());
//    json.put("TotalSwapSpaceSize", 
//        this.getTotalSwapSpaceSize());
//    json.put("FreeSwapSpaceSize", 
//        this.getFreeSwapSpaceSize());
//    json.put("TotalNumberCpuCores", 
//        this.getTotalNumberCpuCores());
//    json.put("ProcessCpuTime", 
//        this.getProcessCpuTime());
//    json.put("FreePhysicalMemorySize", 
//        this.getFreePhysicalMemorySize());
//    json.put("TotalPhysicalMemorySize", 
//        this.getTotalPhysicalMemorySize());
//    json.put("OpenFileDescriptorCount", 
//        this.getOpenFileDescriptorCount());
//    json.put("MaxFileDescriptorCount", 
//        this.getMaxFileDescriptorCount());
//    json.put("SystemCpuLoad", 
//        this.getSystemCpuLoad());
//    json.put("ProcessCpuLoad", 
//        this.getProcessCpuLoad());
//    
//    return json;
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
   * Gets the File Store based on the given name.  If is not found it returns
   * null
   * 
   * @return the File Store based on the given name.  If is not found it
   *         returns null
   */
  private OSFileStore getFileStore()
  {
    OSFileStore[] stores = this.os.getFileSystem().getFileStores();
    for( OSFileStore store : stores )
    {
      String store_mount = store.getMount().toLowerCase();
      if( store_mount.equals("/") || store_mount.equals("c:\\") )
        return store;
    }
    return null;
  }
  
  /**
   * Gets the File Store based on the given name.  If is not found it returns
   * null
   * 
   * @return the File Store based on the given name.  If is not found it
   *         returns null
   */
  private OSFileStore getFileStore( String name )
  {
    OSFileStore[] stores = this.os.getFileSystem().getFileStores();
    for( OSFileStore store : stores )
    {
      String store_mount = store.getMount();
      if( store_mount != null && store_mount.equals(name) )
        return store;
    }
    return null;
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
