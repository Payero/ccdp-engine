package com.axios.ccdp.cloud.docker;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;

import org.apache.log4j.Logger;

import com.axios.ccdp.connections.intfs.SystemResourceMonitorIntf;
import com.axios.ccdp.utils.CcdpUtils;
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
   * Instantiates a new resource monitor
   */
  public DockerResourceMonitorImpl()
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
      this.longCid = this.getContainerID(fname);
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
  
  /**
   * Returns the total amount of swap space in bytes or -1 if the value cannot 
   * be obtained
   * 
   * @return the total amount of swap space in bytes or -1 if the value cannot 
   *         be obtained
   */
  public long getTotalSwapSpaceSize()
  {    
    return this.getCommittedVirtualMemorySize();
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
    return this.getCommittedVirtualMemorySize();
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
    CpuUsage usg = this.prevStats.cpuStats().cpuUsage();
    return usg.totalUsage() / this.units;
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
    MemoryStats mem = this.prevStats.memoryStats();
    // Got that the total memory used is the total_rss in 
    // http://blog.scoutapp.com/articles/2015/06/22/monitoring-docker-containers-from-scratch
    //
    return (mem.limit() - mem.stats().totalRss() )  / this.units;
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
    return this.prevStats.memoryStats().limit() / this.units;      
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
    return this.prevStats.memoryStats().stats().totalRss() / this.units;
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
    return -1L;  
  }
  
  /**
   * Returns the percentage of CPU utilization by comparing a previous stats 
   * against a new one
   * 
   * @return the percentage of CPU utilization by comparing a previous stats 
   *         against a new one; a negative value if not available.
   */
  public double getSystemCpuLoad()
  {
    return this.getSystemCpuPercent();
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
    return this.getUserCpuPercent();
  }
  
  /**
   * Gets the total number of CPU or cores available in this machine
   * 
   * @return the total number of CPU or cores available in this machine
   */
  public int getTotalNumberCpuCores()
  {
    return this.prevStats.cpuStats().cpuUsage().percpuUsage().size();
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
  public String getContainerID( String filename ) throws IOException
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
            this.logger.debug("Found Docker in " + line);
            int start = line.lastIndexOf('/');
            if( start >= 0 )
            {
              cid = line.substring(start + 1);
              this.logger.debug("Returning " + cid);
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
          this.logger.error("Message: " + e.getMessage(), e);
        }
      }
    }
    else
    {
      this.logger.error("Invalid filename (" + filename + 
                        ") or do not have appropriate permissions");
    }
    
    return cid;
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
      cpuPercent = ( cpuDelta / delta) * ( (double)currCpuUsage.percpuUsage().size() * 100.0 );
    
    // need to reset the previous stats
    this.prevStats = curr;
    
    return cpuPercent;
    
  }
  
  /**
   * Closes the DockerClient
   */
  public void close()
  {
    this.docker.close();
  }
  
  /**
   * Gets the unique id identifying this node.
   * 
   * @return the unique id identifying this node.
   */
  public String getUniqueHostId()
  {
    String hostId = "i-";
    if( this.shortCid != null )
    {
      hostId += this.shortCid;
    }
    else
    {
      try
      {
        this.longCid = 
          this.getContainerID(DockerResourceMonitorImpl.DEFAULT_CGROUP_FILE);
        this.shortCid = this.longCid.substring(0,  12);
      }
      catch( Exception e )
      {
        this.logger.error("Message: " + e.getMessage(), e);
      }
      
    }
    
    return hostId;
  }
  
  /**
   * Runs the show, used only as a quick way to test the methods.
   * 
   * @param args the command line arguments, not used
   */
  public static void main( String[] args)
  {
    CcdpUtils.configLogger();
    
    DockerResourceMonitorImpl srm = new DockerResourceMonitorImpl(UNITS.KB);
    
    
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
