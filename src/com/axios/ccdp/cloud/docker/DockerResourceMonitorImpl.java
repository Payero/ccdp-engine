package com.axios.ccdp.cloud.docker;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import org.apache.log4j.Logger;

import com.axios.ccdp.connections.intfs.SystemResourceMonitorIntf;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.spotify.docker.client.DefaultDockerClient;
import com.spotify.docker.client.DockerClient;
import com.spotify.docker.client.exceptions.DockerException;
import com.spotify.docker.client.messages.ContainerStats;
import com.spotify.docker.client.messages.CpuStats;
import com.spotify.docker.client.messages.MemoryStats;
import com.spotify.docker.client.messages.CpuStats.CpuUsage;
import com.spotify.docker.client.messages.MemoryStats.Stats;

import oshi.SystemInfo;
import oshi.hardware.CentralProcessor;
import oshi.hardware.HardwareAbstractionLayer;
import oshi.hardware.NetworkIF;
import oshi.software.os.OSFileStore;
import oshi.software.os.OperatingSystem;

public class DockerResourceMonitorImpl implements SystemResourceMonitorIntf
{
  /**
   * The default URL to reach the Docker engine to get resource statistics for
   * this container
   */
  public static String DEFAULT_DOCKER_HOST = "http://172.17.0.1:2375";
  /**
   * The default file with the container id in the same line as the word 
   * 'docker'.  This is used to actually extract the container id
   */
  public static String DEFAULT_CGROUP_FILE = "/proc/self/cgroup";
  
  /**
   * Generates debug print statements based on the verbosity level.
   */
  private Logger logger = Logger.getLogger(DockerResourceMonitorImpl.class
      .getName());
  
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
   * Stores the container id in the long form
   */
  private String longCid;
  /**
   * Stores the container id in the short form; the first 12 characters of the
   * long container id
   */
  private String shortCid;
  /**
   * Stores the object that actually talks to the docker engine to get 
   * statistic data about the containers
   */
  private DockerClient docker = null;
  /**
   * Stores the previous stats value to generate statistics and percentages
   */
  private ContainerStats prevStats = null;
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
   * Stores previous ticks to calculate CPU load for each one of the cores
   */
  private long[][] prevProcTicks;
  
  /**
   * Instantiates a new resource monitor
   */
  public DockerResourceMonitorImpl()
  {
    this(UNITS.KB);
  }
  
  /**
   * Instantiates a new resource monitor
   * 
   * @param units the units to use when displaying some of the values
   */
  public DockerResourceMonitorImpl( String units )
  {
    this(UNITS.valueOf(units));
  }
  
  /**
   * Instantiates a new resource monitor
   * 
   * @param units the units to use when displaying some of the values
   */
  public DockerResourceMonitorImpl( UNITS units)
  {
    this.logger.debug("Initiating new Monitor");
    this.setUnits( units );
    
    this.os = this.system_info.getOperatingSystem();
    this.hardware = this.system_info.getHardware();
    this.processor = this.hardware.getProcessor();
    this.processor.updateAttributes();
    this.prevProcTicks = this.processor.getProcessorCpuLoadTicks();
    
  }
  
  @Override
  public void configure(ObjectNode config)
  {
    String units = UNITS.KB.toString();
    String url = DockerResourceMonitorImpl.DEFAULT_DOCKER_HOST;
    String fname = DockerResourceMonitorImpl.DEFAULT_CGROUP_FILE;
    JsonNode node = config.get("units");
    
    if( node != null )
      units = node.asText();
    else
      this.logger.warn("The units was not defined using default (KB)");
    
    this.setUnits( units );
    
    if( config.has("docker.url") && config.get("docker.url") != null )
    {
      JsonNode obj = config.get("docker.url");
      url = obj.asText();
    }
    else
      this.logger.warn("The docker host was not defined " +
                       "using default (http://172.17.0.1:2375)");      
    
    if( config.has("cgroup.file") && config.get("cgroup.file") != null )
    {
      JsonNode obj = config.get("cgroup.file");
      fname = obj.asText();
    }
    else
      this.logger.warn("The cgroup file was not defined " +
                       "using default (/proc/self/cgroup)");      

    this.logger.debug("Connecting to Docker engine: " + url);
    this.logger.debug("cgroup file: " + fname);
    
    try
    {
      this.longCid = DockerResourceMonitorImpl.getContainerID(fname);
      this.shortCid = this.longCid.substring(0, 12);

      this.docker = new DefaultDockerClient(url);
      this.prevStats = this.docker.stats(this.longCid);
    }
    catch( Exception ioe )
    {
      this.logger.error("Message: " + ioe.getMessage(), ioe);
    }

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
  }
  

  @Override
  public String getUniqueHostId()
  {
    return this.shortCid;
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

  @Override
  public long getTotalVirtualMemorySize()
  {
    long mem = -1;
    try
    {
      ContainerStats stats = docker.stats(this.longCid);
      MemoryStats memStats = stats.memoryStats();
      this.logger.debug("The Mem Stats " + memStats.toString() );
      Stats st = memStats.stats();
      mem = st.cache();
      mem = mem / this.units;
    }
    catch( Exception e )
    {
      this.logger.error("Message: " + e.getMessage(), e);
    }
    
    return mem;
  }

  @Override
  public long getUsedVirtualMemorySize()
  {
    return this.getTotalVirtualMemorySize();
  }

  @Override
  public long getFreeVirtualMemorySize()
  {
    return this.getTotalVirtualMemorySize();
  }

  @Override
  public long getTotalPhysicalMemorySize()
  {
    return this.prevStats.memoryStats().limit() / this.units;
  }

  @Override
  public long getUsedPhysicalMemorySize()
  {
    return this.prevStats.memoryStats().stats().totalRss() / this.units;
  }

  @Override
  public long getFreePhysicalMemorySize()
  {
    MemoryStats mem = this.prevStats.memoryStats();
    // Got that the total memory used is the total_rss in 
    // http://blog.scoutapp.com/articles/2015/06/22/monitoring-docker-containers-from-scratch
    //
    return (mem.limit() - mem.stats().totalRss() )  / this.units;
  }

  @Override
  public int getPhysicalCPUCount()
  {
    return this.processor.getPhysicalPackageCount();
  }

  @Override
  public int getPhysicalCPUCoreCount()
  {
    return this.processor.getPhysicalProcessorCount();
  }

  @Override
  public int getVirtualCPUCoreCount()
  {
    return this.processor.getLogicalProcessorCount();
  }

  @Override
  public double getSystemCpuLoad()
  {
    return this.getSystemCpuPercent();
  }

  @Override
  public double[] getCPUCoresLoad()
  {
    this.processor.updateAttributes();
    
    double[] val = 
        this.processor.getProcessorCpuLoadBetweenTicks(this.prevProcTicks);
    this.prevProcTicks = this.processor.getProcessorCpuLoadTicks();
    return val;
  }

  @Override
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

  @Override
  public long getTotalStorageSpaceByName(String name)
  {
    OSFileStore store = this.getFileStore(name);
    if( store != null )
      return store.getTotalSpace() / this.units;
    else
      return -1;
  }

  @Override
  public long getUsedStorageSpaceByName(String name)
  {
    OSFileStore store = this.getFileStore(name);
    
    if( store != null )
      return (( store.getTotalSpace() - store.getUsableSpace()) / this.units );
    else
      return -1;
  }

  @Override
  public long getFreeStorageSpaceByName(String name)
  {
    OSFileStore store = this.getFileStore(name);
    if( store != null )
      return store.getUsableSpace() / this.units;
    else
      return -1;
  }

  @Override
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

  @Override
  public long getTotalDiskSpace()
  {
    OSFileStore store = this.getFileStore();
    if( store != null )
      return store.getTotalSpace() / this.units;
    else
      return -1L;
  }

  @Override
  public long getUsedDiskSpace()
  {
    OSFileStore store = this.getFileStore();
    if( store != null )
      return (( store.getTotalSpace() - store.getUsableSpace()) / this.units );
    else
      return -1L;
  }

  @Override
  public long getFreeDiskSpace()
  {
    OSFileStore store = this.getFileStore();
    if( store != null )
      return store.getUsableSpace() / this.units;
    else
      return -1L;
  }

  @Override
  public String getHostName()
  {
    return this.os.getNetworkParams().getHostName();
  }

  @Override
  public String getDomainName()
  {
    return this.os.getNetworkParams().getDomainName();
  }

  @Override
  public String[] getDNSServers()
  {
    return this.os.getNetworkParams().getDnsServers();
  }

  @Override
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

  @Override
  public Map<String, String> getNetworkInterfaceInfo(String name)
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
  
  @Override
  public ObjectNode toJSON()
  {
    // TODO Auto-generated method stub
    return null;
  }

  @Override
  public void close()
  {
    this.logger.debug("Nothing to close or clean");

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
   * Gets the container id from the given filename by looking for the first 
   * instance where the work docker is mentioned.  The file is a cgroup file
   * containing information about the cluster.  Each line in the file has the 
   * following format:
   *    1:name=systemd:/docker/2f521bd5e4fbb0
   *  
   * @param filename the name of the file to parse and get the docker id
   * 
   * @return the docker id from the file or null if not found
   * @throws IOException an IOException is thrown if there is a problem 
   *         reading the file
   */
  public static String getContainerID( String filename ) throws IOException
  {
    File file = new File(filename);
    String cid = null;
    
    // does the file exists and can it be read?
    if( file.isFile() && file.canRead() )
    {
      BufferedReader br = null;
      FileReader fr = null;
      try
      {
        fr = new FileReader(filename);
        br = new BufferedReader(fr);
        
        String line;
        // look for the word docker
        while( (line = br.readLine() ) != null )
        {
          line = line.trim();
          if( line.contains("docker") )
          {
            // found docker so let's get the id
//            this.logger.debug("Found Docker in " + line);
            int start = line.lastIndexOf('/');
            if( start >= 0 )
            {
              cid = line.substring(start + 1);
//              this.logger.debug("Returning " + cid);
              break;
            }
          }
        }
      }
      finally
      {
        // clean up
        try
        {
         if ( br != null ) br.close();
         if ( fr != null ) fr.close();
        }
        catch (Exception e)
        {
          e.printStackTrace();
        }
      }
    }
    else
    {
      System.err.println("Invalid filename (" + filename + 
          ") or do not have appropriate permissions");
    }
    
    return cid;
  }

  public static String getContainerID() 
  {
    try
    {  
      String fname = DockerResourceMonitorImpl.DEFAULT_CGROUP_FILE;
      return DockerResourceMonitorImpl.getContainerID(fname);
    }
    catch( IOException e )
    {
      System.err.println(e.getMessage());
      e.printStackTrace();
    }
    return null;
  }
  
  
  public double getSystemCpuPercent( )
  {
    try
    {
      return this.getCpuPercent(false);
    }
    catch( Exception e )
    {
      this.logger.error("Message: " + e.getMessage(), e);
    }
    return -1;
  }
  
  
  public double getUserCpuPercent( )
  {
    try
    {
      return this.getCpuPercent(true);
    }
    catch( Exception e )
    {
      this.logger.error("Message: " + e.getMessage(), e);
    }
    return -1;
  }
  
  
  public double getCpuPercent( boolean isUser  ) 
                              throws InterruptedException, DockerException
  {
    ContainerStats curr = this.docker.stats(this.longCid);
    
    CpuStats prevCpuStats = this.prevStats.cpuStats();
    CpuUsage prevCpuUsage = prevCpuStats.cpuUsage();
    
    CpuStats currCpuStats = curr.cpuStats();
    CpuUsage currCpuUsage = currCpuStats.cpuUsage();
    
    double prevCPU =  prevCpuUsage.totalUsage();
    double currCPU =  currCpuUsage.totalUsage();
    double cpuDelta = currCPU - prevCPU;
    
    double prevSysUsg = prevCpuStats.systemCpuUsage();
    double currSysUsg = currCpuStats.systemCpuUsage();
    double delta = 0;
    
    if( !isUser )
      delta = currSysUsg - prevSysUsg;
    else
      delta = currCpuUsage.usageInUsermode() - prevCpuUsage.usageInUsermode(); 
    
    double cpuPercent = 0;
    if( delta > 0.0 && cpuDelta > 0.0 )
      cpuPercent = ( cpuDelta / delta) * ( (double)currCpuUsage.percpuUsage().size() );
    
    // need to reset the previous stats
    this.prevStats = curr;
    
    return cpuPercent;
    
  }
  
}
