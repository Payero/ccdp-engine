package com.axios.ccdp.impl.monitors;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.xml.bind.DatatypeConverter;

import org.apache.log4j.Logger;

import com.axios.ccdp.fmwk.CcdpMainApplication;
import com.axios.ccdp.utils.CcdpUtils;
import com.fasterxml.jackson.annotation.JsonGetter;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ObjectNode;

import oshi.SystemInfo;
import oshi.hardware.CentralProcessor;
import oshi.hardware.GlobalMemory;
import oshi.hardware.HardwareAbstractionLayer;
import oshi.hardware.NetworkIF;
import oshi.software.os.OSFileStore;
import oshi.software.os.OperatingSystem;
import oshi.util.Util;

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
public abstract class SystemResourceMonitorAbs
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
    if( !SystemResourceMonitorAbs.DIVISORS.isEmpty() )
      return SystemResourceMonitorAbs.DIVISORS.get(units);
    
    SystemResourceMonitorAbs.DIVISORS.put( UNITS.BYTE, Long.valueOf(1) );
    SystemResourceMonitorAbs.DIVISORS.put( UNITS.KB, Long.valueOf(1024) );
    SystemResourceMonitorAbs.DIVISORS.put( UNITS.MB, Long.valueOf(1024*1024) );
    SystemResourceMonitorAbs.DIVISORS.put( UNITS.GB, Long.valueOf(1024*1024*1024) );
    
    return SystemResourceMonitorAbs.DIVISORS.get(units);
  }
  
  /**
   * Generates debug print statements based on the verbosity level.
   */
  protected Logger logger = Logger.getLogger(LinuxResourceMonitorImpl.class
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
  protected ObjectMapper mapper = new ObjectMapper();

  /**
   * Stores the default units base to use
   */
  protected long units = 1L;
  
  /**
   * Stores previous ticks to calculate CPU load
   */
  private long[] prevTicks;

  /**
   * Stores previous ticks to calculate CPU load for each one of the cores
   */
  private long[][] prevProcTicks;
  
  /**
   * Stores all the different file system types to include
   */
  private List<String> fileSystemTypes = null;
  
  /**
   * Instantiates a new resource monitor using KB as the units
   */
  public SystemResourceMonitorAbs()
  {
    this(UNITS.KB);
  }
  
  /**
   * Instantiates a new resource monitor and sets the units to the given
   * value
   * 
   * @param units the units to use when displaying some of the values
   */
  public SystemResourceMonitorAbs( String units)
  {
    this(UNITS.valueOf( units ) );
  }
  
  /**
   * Instantiates a new resource monitor and sets the units to the given
   * value
   * 
   * @param units the units to use when displaying some of the values
   */
  public SystemResourceMonitorAbs( UNITS units)
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
    this.fileSystemTypes = this.getFileSystemTypes();
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
  public abstract void configure( ObjectNode config );
  
  /**
   * Gets all the different file system storage names such as ext3, ext4, NTFS,
   * etc.
   * 
   * @return all the different file system storage names such as ext3, ext4,
   *         NTFS, etc
   */
  protected abstract List<String> getFileSystemTypes();

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
  @JsonGetter("os-family")
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
  @JsonGetter("os-bit-arch")
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
  @JsonGetter("os-manufacturer")
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
  @JsonGetter("os-version")
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
  @JsonGetter("os-code-name")
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
  @JsonGetter("os-build-number")
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
  @JsonGetter("total-virtual-mem")
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
  @JsonGetter("used-virtual-mem")
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
  @JsonGetter("free-virtual-mem")
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
  @JsonGetter("total-physical-mem")
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
  @JsonGetter("used-physical-mem")
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
  @JsonGetter("free-physical-mem")
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
  @JsonGetter("physical-cpu-count")
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
  @JsonGetter("physical-cpu-core-count")
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
  @JsonGetter("virtual-cpu-core-count")
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
  @JsonGetter("system-cpu-load")
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
  @JsonGetter("cpu-cores-load")
  public double[] getCPUCoresLoad()
  {
    
    this.prevTicks = processor.getSystemCpuLoadTicks();
    this.prevProcTicks = processor.getProcessorCpuLoadTicks();
    // Wait sometime
    Util.sleep(250);
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
  @JsonGetter("file-storage-names")
  public String[] getFileStorageNames()
  {
    OSFileStore[] stores = this.os.getFileSystem().getFileStores();
    
    List<String> names = new ArrayList<>();
    
    for( int i = 0; i < stores.length; i++ )
    {
      OSFileStore store = stores[i];
      String type = store.getType();
          
      if( this.fileSystemTypes.contains(type) )
        names.add( store.getMount() );
    }
    
    return names.toArray(new String[0]);
  }
  
  /**
   * Gets the total amount of storage space of the given partition.  If the 
   * name cannot be found then it return -1L
   * 
   * @param name the name of the storage to retrieve the information
   * 
   * @return the total amount of storage space of the given partition
   */
  @JsonGetter("total-storage-space")
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
  @JsonGetter("used-storage-space")
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
  @JsonGetter("free-storage-space")
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
  @JsonGetter("disk-partition")
  public Map<String, String> getDiskPartitionInfo(String name)
  {
    OSFileStore store = this.getFileStore(name);
    Map<String, String> map = new HashMap<>();
    if( store != null )
    {
      String type = store.getType();
      if( this.fileSystemTypes.contains(type) )
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
  @JsonGetter("total-disk-space")
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
  @JsonGetter("used-disk-space")
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
  @JsonGetter("free-disk-space")
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
  @JsonGetter("hostname")
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
  @JsonGetter("domain")
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
  @JsonGetter("dns-servers")
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
  @JsonGetter("network-interfaces")
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
  @JsonGetter("network-interfaces-info")
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
  @JsonGetter("unique-host-id")
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
   * Returns a string representation of a JSON object where each key is the same
   * as the method without the 'get' and the value is the actual value
   * 
   * @return a JSON representation string of the OS
   */
  public String toString()
  {

    String str = null;
    
    try
    {
      str = mapper.writeValueAsString(this);
    }
    catch( Exception e )
    {
      throw new RuntimeException("Could not write Json " + e.getMessage() );
    }
    
    return str;
  }
  
  /**
   * Prints the contents of the object using a more human readable form.
   * 
   * @return a String representation of the object using a more human friendly
   *         formatting
   */
  public String toPrettyPrint()
  {
    String str = null;
    
    try
    {
      ObjectMapper mapper = new ObjectMapper();
      mapper.enable(SerializationFeature.INDENT_OUTPUT);
      str = mapper.writeValueAsString(this);
    }
    catch( Exception e )
    {
      throw new RuntimeException("Could not write Json " + e.getMessage() );
    }
    
    return str;
  }
  
  /**
   * Gets a JSON object representing the object.    
   * 
   * @return a JSON object representing an instance of this class
   */
  public ObjectNode toJSON()
  {
    return this.mapper.convertValue( this, ObjectNode.class );
  }
  
  /** 
   * Generates a String representing this object serialized.
   * 
   * @return a String representation of this object serialized
   * @throws IOException an IOException is thrown if there is a problem during
   *         the serialization of the object
   */
  public String toSerializedString( ) throws IOException 
  {
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    ObjectOutputStream oos = new ObjectOutputStream( baos );
    oos.writeObject( this );
    oos.close();
    
    //return Base64.getEncoder().encodeToString(baos.toByteArray()); 
    return DatatypeConverter.printBase64Binary(baos.toByteArray());
  }  
  
  /**
   * Gets the File Store based on the given name.  If is not found it returns
   * null
   * 
   * @return the File Store based on the given name.  If is not found it
   *         returns null
   */
  protected OSFileStore getFileStore()
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
   * @param name the name of the file storage to retrieve
   * 
   * @return the File Store based on the given name.  If is not found it
   *         returns null
   */
  protected OSFileStore getFileStore( String name )
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
}
