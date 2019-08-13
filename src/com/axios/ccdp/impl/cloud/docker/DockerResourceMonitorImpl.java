package com.axios.ccdp.impl.cloud.docker;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import com.axios.ccdp.impl.monitors.SystemResourceMonitorAbs;
import com.axios.ccdp.utils.CcdpConfigParser;
import com.fasterxml.jackson.annotation.JsonGetter;
import com.fasterxml.jackson.databind.JsonNode;
import com.spotify.docker.client.DefaultDockerClient;
import com.spotify.docker.client.DockerClient;
import com.spotify.docker.client.exceptions.DockerException;
import com.spotify.docker.client.messages.ContainerStats;
import com.spotify.docker.client.messages.CpuStats;
import com.spotify.docker.client.messages.MemoryStats;
import com.spotify.docker.client.messages.CpuStats.CpuUsage;
import com.spotify.docker.client.messages.MemoryStats.Stats;


public class DockerResourceMonitorImpl extends SystemResourceMonitorAbs
{
  /**
   * Stores all the different types of file system storages to include
   */
  private static List<String> FILE_STORE_TYPES = 
      Arrays.asList( new String[] {"ext3", "ext4", "nfs", "xsf"}); 
  
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
    super();
  }
  
  /**
   * Instantiates a new resource monitor
   * 
   * @param units the units to use when displaying some of the values
   */
  public DockerResourceMonitorImpl( String units )
  {
    super( units );
  }
  
  /**
   * Instantiates a new resource monitor
   * 
   * @param units the units to use when displaying some of the values
   */
  public DockerResourceMonitorImpl( UNITS units)
  {
    super( units );    
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
  public void configure(JsonNode config)
  {
    String units = UNITS.KB.toString();
    String url = DockerResourceMonitorImpl.DEFAULT_DOCKER_HOST;
    String fname = DockerResourceMonitorImpl.DEFAULT_CGROUP_FILE;
    //JsonNode node = config.get("units");
    JsonNode node = config.get(CcdpConfigParser.KEY_VM_RESOURCE_UNITS);
    
    if( node != null )
      units = node.asText();
    else
      this.logger.warn("The units was not defined using default (KB)");
    
    this.setUnits( units );
    
    /*if( config.has("docker-url") && config.get("docker-url") != null )
    {
      JsonNode obj = config.get("docker-url");
      url = obj.asText();
    }*/
    if( config.has(CcdpConfigParser.KEY_DOCKER_URL) && config.get(CcdpConfigParser.KEY_DOCKER_URL) != null )
    {
      JsonNode obj = config.get(CcdpConfigParser.KEY_DOCKER_URL);
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
      //this.logger.debug("long cid: " + this.longCid); -- This is null, results in a NullPointerException
      this.shortCid = this.longCid.substring(0, 12);
      //this.logger.debug("short cid: " + this.shortCid);

      this.docker = new DefaultDockerClient(url);
      this.prevStats = this.docker.stats(this.longCid);
    }
    catch( Exception ioe )
    {
      this.logger.error("Message: " + ioe.getMessage(), ioe);
    }

  }

  /**
   * Gets all the different file system storage names such as ext3, ext4, NTFS,
   * etc.
   * 
   * @return all the different file system storage names such as ext3, ext4,
   *         NTFS, etc
   */
  protected List<String> getFileSystemTypes()
  {
    return DockerResourceMonitorImpl.FILE_STORE_TYPES;
  }
  
  /**
   * Sets the units to used to represent the resources such as BYTE, KB, MB, 
   * and GB.  
   * 
   * @param units units to use to measure the resources
   */
  @Override
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
    
    this.units = SystemResourceMonitorAbs.getDivisor(units);
  }
  

  @Override
  @JsonGetter("unique-host-id")
  public String getUniqueHostId()
  {
    return this.shortCid;
  }

  
  @Override
  @JsonGetter("total-virtual-mem")
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
  @JsonGetter("used-virtual-mem")
  public long getUsedVirtualMemorySize()
  {
    return this.getTotalVirtualMemorySize();
  }

  @Override
  @JsonGetter("free-virtual-mem")
  public long getFreeVirtualMemorySize()
  {
    return this.getTotalVirtualMemorySize();
  }

  @Override
  @JsonGetter("total-physical-mem")
  public long getTotalPhysicalMemorySize()
  {
    return this.prevStats.memoryStats().limit() / this.units;
  }

  @Override
  @JsonGetter("used-physical-mem")
  public long getUsedPhysicalMemorySize()
  {
    return this.prevStats.memoryStats().stats().totalRss() / this.units;
  }

  @Override
  @JsonGetter("free-physical-mem")
  public long getFreePhysicalMemorySize()
  {
    MemoryStats mem = this.prevStats.memoryStats();
    // Got that the total memory used is the total_rss in 
    // http://blog.scoutapp.com/articles/2015/06/22/monitoring-docker-containers-from-scratch
    //
    return (mem.limit() - mem.stats().totalRss() )  / this.units;
  }

  
  @Override
  @JsonGetter("system-cpu-load")
  public double getSystemCpuLoad()
  {
    return this.getSystemCpuPercent();
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
