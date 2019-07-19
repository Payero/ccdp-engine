package com.axios.ccdp.impl.monitors;

import java.util.Arrays;
import java.util.List;

import org.apache.log4j.Logger;

import com.axios.ccdp.impl.cloud.docker.DockerResourceMonitorImpl;
import com.fasterxml.jackson.databind.JsonNode;

public class HybridResourceMonitorImpl extends SystemResourceMonitorAbs
{
  /**
   * Stores all the different types of file system storages to include
   */
  private static List<String> FILE_STORE_TYPES = 
      Arrays.asList( new String[] {"ext3", "ext4", "nfs", "xsf"});
  
  private LinuxResourceMonitorImpl linux = null;
  private DockerResourceMonitorImpl docker = null;
  /**
   * Generates debug print statements based on the verbosity level.
   */
  private Logger logger = Logger
      .getLogger(HybridResourceMonitorImpl.class.getName());

  public HybridResourceMonitorImpl()
  {
    //super();
    linux = new LinuxResourceMonitorImpl();
    docker = new DockerResourceMonitorImpl();
  }
  
  public HybridResourceMonitorImpl( String units )
  {
    linux = new LinuxResourceMonitorImpl( units );
    docker = new DockerResourceMonitorImpl( units );
  }
  
  public HybridResourceMonitorImpl( UNITS units )
  {
    linux = new LinuxResourceMonitorImpl( units );
    docker = new DockerResourceMonitorImpl( units );
  }

  
  public void configure(JsonNode config)
  {
    docker.configure(config);
    linux.configure(config);
  }

  
  protected List<String> getFileSystemTypes()
  {
    return HybridResourceMonitorImpl.FILE_STORE_TYPES;
  }
  
  @Override
  public void setUnits ( UNITS units )
  {
    docker.setUnits(units);
  }
  
  @Override
  public String getUniqueHostId()
  {
    return docker.getUniqueHostId();
  }
  
  @Override
  public long getTotalVirtualMemorySize()
  {
    return docker.getTotalVirtualMemorySize();
  }
  
  @Override
  public long getUsedVirtualMemorySize()
  {
    return docker.getUsedVirtualMemorySize();
  }
  
  @Override
  public long getFreeVirtualMemorySize()
  {
    return docker.getFreeVirtualMemorySize();
  }

  @Override
  public long getTotalPhysicalMemorySize()
  {
    return docker.getTotalPhysicalMemorySize();
  }

  @Override
  public long getUsedPhysicalMemorySize()
  {
    return docker.getUsedPhysicalMemorySize();
  }

  @Override
  public long getFreePhysicalMemorySize()
  {
    return docker.getFreePhysicalMemorySize();
  }
  
  @Override
  public double getSystemCpuLoad()
  {
    return docker.getSystemCpuLoad();
  }
}
