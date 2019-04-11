package com.axios.ccdp.test;


import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Arrays;

import org.apache.log4j.Logger;

import com.axios.ccdp.utils.CcdpUtils;

import oshi.SystemInfo;
import oshi.hardware.CentralProcessor;
import oshi.hardware.GlobalMemory;
import oshi.hardware.HWDiskStore;
import oshi.hardware.HWPartition;
import oshi.hardware.HardwareAbstractionLayer;
import oshi.hardware.NetworkIF;
import oshi.hardware.Sensors;
import oshi.hardware.VirtualMemory;
import oshi.software.os.FileSystem;
import oshi.software.os.NetworkParams;
import oshi.software.os.OSFileStore;
import oshi.software.os.OperatingSystem;
import oshi.software.os.OperatingSystemVersion;
import oshi.util.FormatUtil;
import oshi.util.Util;

public class OshiResTest
{
  /**
   * Generates debug print statements based on the verbosity level.
   */
  private Logger logger = Logger.getLogger(OshiTest.class.getName());

  public OshiResTest()
  {
    this.logger.info("Initializing System...");
    SystemInfo si = new SystemInfo();

    HardwareAbstractionLayer hal = si.getHardware();
    OperatingSystem os = si.getOperatingSystem();
    
    this.logger.debug("Operating System: " + os.toString() );
    os.getFamily();
    os.getBitness();
    os.getManufacturer();
    os.getVersion();
    os.getProcessId();
    OperatingSystemVersion ov = os.getVersion();
    
    this.logger.debug("Family: " + os.getFamily() );
    this.logger.debug("Bitness: " + os.getBitness() );
    this.logger.debug("Manufacturer: " + os.getManufacturer() );
    this.logger.debug("OS Version: " + ov.getVersion() );
    this.logger.debug("OS Code Name: " + ov.getCodeName() );
    this.logger.debug("OS Build Number: " + ov.getBuildNumber() );
    
    this.logger.info("\n\n\nChecking Processor...");
    printProcessor(hal.getProcessor());

    this.logger.info("\n\n\nChecking CPU...");
    printCpu(hal.getProcessor());

    this.logger.info("\n\n\nChecking Memory...");
    printMemory(hal.getMemory());

    this.logger.info("\n\n\nChecking Sensors...");
    printSensors(hal.getSensors());

    this.logger.info("\n\n\nChecking Disks...");
    printDisks(hal.getDiskStores());

    this.logger.info("\n\n\nChecking File System...");
    printFileSystem(os.getFileSystem());

    this.logger.info("\n\n\nChecking Network parameterss...");
    printNetworkParameters(os.getNetworkParams());
    
    this.logger.info("\n\n\nChecking Network interfaces...");
    printNetworkInterfaces(hal.getNetworkIFs());
  }
  
  

private void printProcessor(CentralProcessor processor) 
{
  this.logger.debug("Using Processor: " + processor);
  this.logger.debug("Identifier: " + processor.getIdentifier());    
  this.logger.debug(" " + processor.getPhysicalPackageCount() + " physical CPU package(s)");
  this.logger.debug(" " + processor.getPhysicalProcessorCount() + " physical CPU core(s)");
  this.logger.debug(" " + processor.getLogicalProcessorCount() + " logical CPU(s)");
}


private void printCpu(CentralProcessor processor) 
{
    long[] prevTicks = processor.getSystemCpuLoadTicks();
    long[][] prevProcTicks = processor.getProcessorCpuLoadTicks();
    // Wait a second...
    Util.sleep(1000);
    processor.updateAttributes();
    
    String txt = String.format("CPU load: %.1f%% (counting ticks)",
            processor.getSystemCpuLoadBetweenTicks(prevTicks) * 100);
    this.logger.debug(txt);
    txt = String.format("CPU load: %.1f%% (OS MXBean)", processor.getSystemCpuLoad() * 100);
    this.logger.debug(txt);
    // per core CPU
    StringBuilder procCpu = new StringBuilder("CPU load per processor:");
    double[] load = processor.getProcessorCpuLoadBetweenTicks(prevProcTicks);
    for (double avg : load) 
    {
        procCpu.append(String.format(" %.1f%%", avg * 100));
    }
    this.logger.debug(procCpu.toString());
}


private void printMemory(GlobalMemory memory) 
{
  this.logger.debug("Total Memory: " + FormatUtil.formatBytes(memory.getTotal()) );
  this.logger.debug("Memory Available: " + FormatUtil.formatBytes(memory.getAvailable()) );
  
  VirtualMemory vm = memory.getVirtualMemory();
  this.logger.debug("Total Swap Memory: " + FormatUtil.formatBytes(vm.getSwapTotal()) );
  this.logger.debug("Swap Memory Used: " + FormatUtil.formatBytes(vm.getSwapUsed() ) );
}



private void printSensors(Sensors sensors) 
{
  this.logger.debug("Sensors:");
  String txt = String.format(" CPU Temperature: %.1f°C", sensors.getCpuTemperature());
  this.logger.debug(txt);
  
  this.logger.debug(" Fan Speeds: " + Arrays.toString(sensors.getFanSpeeds()));
  txt = String.format(" CPU Voltage: %.1fV", sensors.getCpuVoltage());
  this.logger.debug(txt);
}


private void printDisks(HWDiskStore[] diskStores) 
{
    System.out.println("Disks:");
    for (HWDiskStore disk : diskStores) 
    {
      
        String name = disk.getName();
        long size = disk.getSize();
        if( size <0 ) size = -1;
        String txt = String.format("%s, size: %s", name, FormatUtil.formatBytesDecimal(size) );
        this.logger.debug(txt);
        
        HWPartition[] partitions = disk.getPartitions();
        if (partitions == null) {
            continue;
        }
        this.logger.debug("Partitions");
        for (HWPartition part : partitions) 
        {
          txt = String.format(" |-- %s: (%s), size: %s%s", part.getIdentification(),
                    part.getType(), FormatUtil.formatBytesDecimal(part.getSize()),
                    part.getMountPoint().isEmpty() ? "" : " @ " + part.getMountPoint());
          this.logger.debug(txt);
        }
    }
}

private void printFileSystem(FileSystem fileSystem) 
{
    OSFileStore[] fsArray = fileSystem.getFileStores();
    for (OSFileStore fs : fsArray) 
    {
        long usable = fs.getUsableSpace();
        long total = fs.getTotalSpace();
        String name = fs.getName();
        String type = fs.getType();
        String canUse = FormatUtil.formatBytes(usable);
        String totSpace = FormatUtil.formatBytes(total);
        String txt = String.format("free (%.1f%%)", 100d * usable / total);
        
        String vol = fs.getVolume();
        String mnt = fs.getMount();
        
        if( type.equals("xfs") || type.equals("nfs")  ||
            type.equals("NTFS") || type.startsWith("ext") )
        {
          this.logger.debug("Partition Information: " + name);
          this.logger.debug("\tVolume " + vol);
          this.logger.debug("\tType " + type);
          this.logger.debug("\tMount " + mnt);
          this.logger.debug("\tAvailable " + canUse);
          this.logger.debug("\tTotal Space " + totSpace);
          this.logger.debug("\tFree Space" + txt);
        }
        else
        {
          this.logger.debug("Skipping Volume: " + type);
        }

    }
}

private void printNetworkParameters(NetworkParams networkParams) 
{
  this.logger.debug(" Host name: " + networkParams.getHostName());
  this.logger.debug(" Domain name: " + networkParams.getDomainName());
  this.logger.debug(" DNS servers: " + Arrays.toString(networkParams.getDnsServers()));
  this.logger.debug(" IPv4 Gateway: " + networkParams.getIpv4DefaultGateway());
  this.logger.debug(" IPv6 Gateway: " + networkParams.getIpv6DefaultGateway());
}

private void printNetworkInterfaces(NetworkIF[] networkIFs) 
{
    for (NetworkIF net : networkIFs) 
    {
      this.logger.debug("Name: " + net.getDisplayName() );
      this.logger.debug("\tMAC Address: " + net.getMacaddr());
      String txt = String.format("\tMTU: %s, Speed: %s", net.getMTU(), 
                          FormatUtil.formatValue(net.getSpeed(), "bps"));
      this.logger.debug(txt);
      boolean hasData = net.getBytesRecv()   > 0 || net.getBytesSent()   > 0 || 
                        net.getPacketsRecv() > 0 || net.getPacketsSent() > 0;
      
      if( hasData )
      {
        String rcvd = Long.toString(net.getPacketsRecv());
        String bytesRcvd = FormatUtil.formatBytes(net.getBytesRecv());
        long inErr = net.getInErrors();
        long sent = net.getPacketsSent();
        String bytesSent = FormatUtil.formatBytes(net.getBytesSent());
        long outErr = net.getOutErrors();
        
        this.logger.debug("\tTraffic:");
        this.logger.debug("\t\t Sent:           " + sent);
        this.logger.debug("\t\t Bytes Sent:     " + bytesSent);
        this.logger.debug("\t\t Received:       " + rcvd);
        this.logger.debug("\t\t Bytes Received: " + bytesRcvd);
        this.logger.debug("\t\t In errors:      " + inErr);
        this.logger.debug("\t\t Out errors:     " + outErr);
      }
    }
}




  /**
   * The main method, demonstrating use of classes.
   *
   * @param args
   *            the arguments
   */
  public static void main(String[] args) 
  {
    String cfg_file = System.getProperty("ccdp.config.file");
    try
    {
      CcdpUtils.loadProperties(cfg_file);
    }
    catch (FileNotFoundException e)
    {
      e.printStackTrace();
    }
    catch (IOException e)
    {
      e.printStackTrace();
    }
    CcdpUtils.configLogger();
    
    new OshiResTest();
    
  }

}
