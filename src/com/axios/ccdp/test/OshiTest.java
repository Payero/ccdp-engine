package com.axios.ccdp.test;

import static org.junit.Assert.assertFalse;

import java.util.Arrays;
import java.util.List;

import org.apache.log4j.Logger;
import org.junit.Test;

import com.axios.ccdp.utils.CcdpUtils;

import oshi.PlatformEnum;
import oshi.SystemInfo;
import oshi.hardware.Baseboard;
import oshi.hardware.CentralProcessor;
import oshi.hardware.CentralProcessor.TickType;
import oshi.hardware.ComputerSystem;
import oshi.hardware.Display;
import oshi.hardware.Firmware;
import oshi.hardware.GlobalMemory;
import oshi.hardware.HWDiskStore;
import oshi.hardware.HWPartition;
import oshi.hardware.HardwareAbstractionLayer;
import oshi.hardware.NetworkIF;
import oshi.hardware.PowerSource;
import oshi.hardware.Sensors;
import oshi.hardware.SoundCard;
import oshi.hardware.UsbDevice;
import oshi.hardware.VirtualMemory;
import oshi.software.os.FileSystem;
import oshi.software.os.NetworkParams;
import oshi.software.os.OSFileStore;
import oshi.software.os.OSProcess;
import oshi.software.os.OperatingSystem;
import oshi.software.os.OperatingSystem.ProcessSort;
import oshi.util.FormatUtil;
import oshi.util.Util;

public class OshiTest
{
  /**
   * Generates debug print statements based on the verbosity level.
   */
  private Logger logger = Logger.getLogger(OshiTest.class.getName());

  public OshiTest()
  {
    this.logger.info("Initializing System...");
    SystemInfo si = new SystemInfo();

    HardwareAbstractionLayer hal = si.getHardware();
    OperatingSystem os = si.getOperatingSystem();

    System.out.println(os);

    this.logger.info("Checking computer system...");
    printComputerSystem(hal.getComputerSystem());

    this.logger.info("Checking Processor...");
    printProcessor(hal.getProcessor());

    this.logger.info("Checking Memory...");
    printMemory(hal.getMemory());

    this.logger.info("Checking CPU...");
    printCpu(hal.getProcessor());

    this.logger.info("Checking Processes...");
    printProcesses(os, hal.getMemory());

    this.logger.info("Checking Sensors...");
    printSensors(hal.getSensors());

    this.logger.info("Checking Power sources...");
    printPowerSources(hal.getPowerSources());

    this.logger.info("Checking Disks...");
    printDisks(hal.getDiskStores());

    this.logger.info("Checking File System...");
    printFileSystem(os.getFileSystem());

    this.logger.info("Checking Network interfaces...");
    printNetworkInterfaces(hal.getNetworkIFs());

    this.logger.info("Checking Network parameterss...");
    printNetworkParameters(os.getNetworkParams());

    // hardware: displays
    this.logger.info("Checking Displays...");
    printDisplays(hal.getDisplays());

    // hardware: USB devices
    this.logger.info("Checking USB Devices...");
    printUsbDevices(hal.getUsbDevices(true));

    this.logger.info("Checking Sound Cards...");
    printSoundCards(hal.getSoundCards());
  }
  /**
   * Test system info.
   */
  @Test
  public void testCentralProcessor() {
      assertFalse(PlatformEnum.UNKNOWN.equals(SystemInfo.getCurrentPlatformEnum()));
  }
  

  
  
  private void printComputerSystem(final ComputerSystem computerSystem) {

    System.out.println("manufacturer: " + computerSystem.getManufacturer());
    System.out.println("model: " + computerSystem.getModel());
    System.out.println("serialnumber: " + computerSystem.getSerialNumber());
    final Firmware firmware = computerSystem.getFirmware();
    System.out.println("firmware:");
    System.out.println("  manufacturer: " + firmware.getManufacturer());
    System.out.println("  name: " + firmware.getName());
    System.out.println("  description: " + firmware.getDescription());
    System.out.println("  version: " + firmware.getVersion());
    System.out.println("  release date: " + (firmware.getReleaseDate() == null ? "unknown"
            : firmware.getReleaseDate() == null ? "unknown" : firmware.getReleaseDate()));
    final Baseboard baseboard = computerSystem.getBaseboard();
    System.out.println("baseboard:");
    System.out.println("  manufacturer: " + baseboard.getManufacturer());
    System.out.println("  model: " + baseboard.getModel());
    System.out.println("  version: " + baseboard.getVersion());
    System.out.println("  serialnumber: " + baseboard.getSerialNumber());
}

private void printProcessor(CentralProcessor processor) {
    System.out.println(processor);
    System.out.println(" " + processor.getPhysicalPackageCount() + " physical CPU package(s)");
    System.out.println(" " + processor.getPhysicalProcessorCount() + " physical CPU core(s)");
    System.out.println(" " + processor.getLogicalProcessorCount() + " logical CPU(s)");

    System.out.println("Identifier: " + processor.getIdentifier());
    System.out.println("ProcessorID: " + processor.getProcessorID());
}

private void printMemory(GlobalMemory memory) {
    System.out.println("Memory: " + FormatUtil.formatBytes(memory.getAvailable()) + "/"
            + FormatUtil.formatBytes(memory.getTotal()));
    VirtualMemory vm = memory.getVirtualMemory();
    System.out.println("Swap used: " + FormatUtil.formatBytes(vm.getSwapUsed()) + "/"
            + FormatUtil.formatBytes(vm.getSwapTotal()));
}

private void printCpu(CentralProcessor processor) {
    System.out.println("Uptime: " + FormatUtil.formatElapsedSecs(processor.getSystemUptime()));
    System.out.println(
            "Context Switches/Interrupts: " + processor.getContextSwitches() + " / " + processor.getInterrupts());
    long[] prevTicks = processor.getSystemCpuLoadTicks();
    long[][] prevProcTicks = processor.getProcessorCpuLoadTicks();
    System.out.println("CPU, IOWait, and IRQ ticks @ 0 sec:" + Arrays.toString(prevTicks));
    // Wait a second...
    Util.sleep(1000);
    processor.updateAttributes();
    long[] ticks = processor.getSystemCpuLoadTicks();
    System.out.println("CPU, IOWait, and IRQ ticks @ 1 sec:" + Arrays.toString(ticks));
    long user = ticks[TickType.USER.getIndex()] - prevTicks[TickType.USER.getIndex()];
    long nice = ticks[TickType.NICE.getIndex()] - prevTicks[TickType.NICE.getIndex()];
    long sys = ticks[TickType.SYSTEM.getIndex()] - prevTicks[TickType.SYSTEM.getIndex()];
    long idle = ticks[TickType.IDLE.getIndex()] - prevTicks[TickType.IDLE.getIndex()];
    long iowait = ticks[TickType.IOWAIT.getIndex()] - prevTicks[TickType.IOWAIT.getIndex()];
    long irq = ticks[TickType.IRQ.getIndex()] - prevTicks[TickType.IRQ.getIndex()];
    long softirq = ticks[TickType.SOFTIRQ.getIndex()] - prevTicks[TickType.SOFTIRQ.getIndex()];
    long steal = ticks[TickType.STEAL.getIndex()] - prevTicks[TickType.STEAL.getIndex()];
    long totalCpu = user + nice + sys + idle + iowait + irq + softirq + steal;

    System.out.format(
            "User: %.1f%% Nice: %.1f%% System: %.1f%% Idle: %.1f%% IOwait: %.1f%% IRQ: %.1f%% SoftIRQ: %.1f%% Steal: %.1f%%%n",
            100d * user / totalCpu, 100d * nice / totalCpu, 100d * sys / totalCpu, 100d * idle / totalCpu,
            100d * iowait / totalCpu, 100d * irq / totalCpu, 100d * softirq / totalCpu, 100d * steal / totalCpu);
    System.out.format("CPU load: %.1f%% (counting ticks)%n",
            processor.getSystemCpuLoadBetweenTicks(prevTicks) * 100);
    System.out.format("CPU load: %.1f%% (OS MXBean)%n", processor.getSystemCpuLoad() * 100);
    double[] loadAverage = processor.getSystemLoadAverage(3);
    System.out.println("CPU load averages:" + (loadAverage[0] < 0 ? " N/A" : String.format(" %.2f", loadAverage[0]))
            + (loadAverage[1] < 0 ? " N/A" : String.format(" %.2f", loadAverage[1]))
            + (loadAverage[2] < 0 ? " N/A" : String.format(" %.2f", loadAverage[2])));
    // per core CPU
    StringBuilder procCpu = new StringBuilder("CPU load per processor:");
    double[] load = processor.getProcessorCpuLoadBetweenTicks(prevProcTicks);
    for (double avg : load) {
        procCpu.append(String.format(" %.1f%%", avg * 100));
    }
    System.out.println(procCpu.toString());
    long freq = processor.getVendorFreq();
    if (freq > 0) {
        System.out.println("Vendor Frequency: " + FormatUtil.formatHertz(freq));
    }
    freq = processor.getMaxFreq();
    if (freq > 0) {
        System.out.println("Max Frequency: " + FormatUtil.formatHertz(freq));
    }
    long[] freqs = processor.getCurrentFreq();
    if (freqs[0] > 0) {
        StringBuilder sb = new StringBuilder("Current Frequencies: ");
        for (int i = 0; i < freqs.length; i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append(FormatUtil.formatHertz(freqs[i]));
        }
        System.out.println(sb.toString());
    }
}

private void printProcesses(OperatingSystem os, GlobalMemory memory) {
    System.out.println("Processes: " + os.getProcessCount() + ", Threads: " + os.getThreadCount());
    // Sort by highest CPU
    List<OSProcess> procs = Arrays.asList(os.getProcesses(5, ProcessSort.CPU));

    System.out.println("   PID  %CPU %MEM       VSZ       RSS Name");
    for (int i = 0; i < procs.size() && i < 5; i++) {
        OSProcess p = procs.get(i);
        System.out.format(" %5d %5.1f %4.1f %9s %9s %s%n", p.getProcessID(),
                100d * (p.getKernelTime() + p.getUserTime()) / p.getUpTime(),
                100d * p.getResidentSetSize() / memory.getTotal(), FormatUtil.formatBytes(p.getVirtualSize()),
                FormatUtil.formatBytes(p.getResidentSetSize()), p.getName());
    }
}

private void printSensors(Sensors sensors) {
    System.out.println("Sensors:");
    System.out.format(" CPU Temperature: %.1f°C%n", sensors.getCpuTemperature());
    System.out.println(" Fan Speeds: " + Arrays.toString(sensors.getFanSpeeds()));
    System.out.format(" CPU Voltage: %.1fV%n", sensors.getCpuVoltage());
}

private void printPowerSources(PowerSource[] powerSources) {
    StringBuilder sb = new StringBuilder("Power: ");
    if (powerSources.length == 0) {
        sb.append("Unknown");
    } else {
        double timeRemaining = powerSources[0].getTimeRemaining();
        if (timeRemaining < -1d) {
            sb.append("Charging");
        } else if (timeRemaining < 0d) {
            sb.append("Calculating time remaining");
        } else {
            sb.append(String.format("%d:%02d remaining", (int) (timeRemaining / 3600),
                    (int) (timeRemaining / 60) % 60));
        }
    }
    for (PowerSource pSource : powerSources) {
        sb.append(String.format("%n %s @ %.1f%%", pSource.getName(), pSource.getRemainingCapacity() * 100d));
    }
    System.out.println(sb.toString());
}

private void printDisks(HWDiskStore[] diskStores) {
    System.out.println("Disks:");
    for (HWDiskStore disk : diskStores) {
        boolean readwrite = disk.getReads() > 0 || disk.getWrites() > 0;
        System.out.format(" %s: (model: %s - S/N: %s) size: %s, reads: %s (%s), writes: %s (%s), xfer: %s ms%n",
                disk.getName(), disk.getModel(), disk.getSerial(),
                disk.getSize() > 0 ? FormatUtil.formatBytesDecimal(disk.getSize()) : "?",
                readwrite ? disk.getReads() : "?", readwrite ? FormatUtil.formatBytes(disk.getReadBytes()) : "?",
                readwrite ? disk.getWrites() : "?", readwrite ? FormatUtil.formatBytes(disk.getWriteBytes()) : "?",
                readwrite ? disk.getTransferTime() : "?");
        HWPartition[] partitions = disk.getPartitions();
        if (partitions == null) {
            // TODO Remove when all OS's implemented
            continue;
        }
        for (HWPartition part : partitions) {
            System.out.format(" |-- %s: %s (%s) Maj:Min=%d:%d, size: %s%s%n", part.getIdentification(),
                    part.getName(), part.getType(), part.getMajor(), part.getMinor(),
                    FormatUtil.formatBytesDecimal(part.getSize()),
                    part.getMountPoint().isEmpty() ? "" : " @ " + part.getMountPoint());
        }
    }
}

private void printFileSystem(FileSystem fileSystem) {
    System.out.println("File System:");

    System.out.format(" File Descriptors: %d/%d%n", fileSystem.getOpenFileDescriptors(),
            fileSystem.getMaxFileDescriptors());

    OSFileStore[] fsArray = fileSystem.getFileStores();
    for (OSFileStore fs : fsArray) {
        long usable = fs.getUsableSpace();
        long total = fs.getTotalSpace();
        System.out.format(
                " %s (%s) [%s] %s of %s free (%.1f%%), %s of %s files free (%.1f%%) is %s "
                        + (fs.getLogicalVolume() != null && fs.getLogicalVolume().length() > 0 ? "[%s]" : "%s")
                        + " and is mounted at %s%n",
                fs.getName(), fs.getDescription().isEmpty() ? "file system" : fs.getDescription(), fs.getType(),
                FormatUtil.formatBytes(usable), FormatUtil.formatBytes(fs.getTotalSpace()), 100d * usable / total,
                fs.getFreeInodes(), fs.getTotalInodes(), 100d * fs.getFreeInodes() / fs.getTotalInodes(),
                fs.getVolume(), fs.getLogicalVolume(), fs.getMount());
    }
}

private void printNetworkInterfaces(NetworkIF[] networkIFs) {
    System.out.println("Network interfaces:");
    for (NetworkIF net : networkIFs) {
        System.out.format(" Name: %s (%s)%n", net.getName(), net.getDisplayName());
        System.out.format("   MAC Address: %s %n", net.getMacaddr());
        System.out.format("   MTU: %s, Speed: %s %n", net.getMTU(), FormatUtil.formatValue(net.getSpeed(), "bps"));
        System.out.format("   IPv4: %s %n", Arrays.toString(net.getIPv4addr()));
        System.out.format("   IPv6: %s %n", Arrays.toString(net.getIPv6addr()));
        boolean hasData = net.getBytesRecv() > 0 || net.getBytesSent() > 0 || net.getPacketsRecv() > 0
                || net.getPacketsSent() > 0;
        System.out.format("   Traffic: received %s/%s%s; transmitted %s/%s%s %n",
                hasData ? net.getPacketsRecv() + " packets" : "?",
                hasData ? FormatUtil.formatBytes(net.getBytesRecv()) : "?",
                hasData ? " (" + net.getInErrors() + " err)" : "",
                hasData ? net.getPacketsSent() + " packets" : "?",
                hasData ? FormatUtil.formatBytes(net.getBytesSent()) : "?",
                hasData ? " (" + net.getOutErrors() + " err)" : "");
    }
}

private void printNetworkParameters(NetworkParams networkParams) {
    System.out.println("Network parameters:");
    System.out.format(" Host name: %s%n", networkParams.getHostName());
    System.out.format(" Domain name: %s%n", networkParams.getDomainName());
    System.out.format(" DNS servers: %s%n", Arrays.toString(networkParams.getDnsServers()));
    System.out.format(" IPv4 Gateway: %s%n", networkParams.getIpv4DefaultGateway());
    System.out.format(" IPv6 Gateway: %s%n", networkParams.getIpv6DefaultGateway());
}

private void printDisplays(Display[] displays) {
    System.out.println("Displays:");
    int i = 0;
    for (Display display : displays) {
        System.out.println(" Display " + i + ":");
        System.out.println(display.toString());
        i++;
    }
}

private void printUsbDevices(UsbDevice[] usbDevices) {
    System.out.println("USB Devices:");
    for (UsbDevice usbDevice : usbDevices) {
        System.out.println(usbDevice.toString());
    }
}

private void printSoundCards(SoundCard[] cards) {
    System.out.println("Sound Cards:");
    for (SoundCard card : cards) {
        System.out.println(card.toString());
    }
}


  /**
   * The main method, demonstrating use of classes.
   *
   * @param args
   *            the arguments
   */
  public static void main(String[] args) throws Exception
  {
    String cfg_file = System.getProperty("ccdp.config.file");
    CcdpUtils.loadProperties(cfg_file);
    CcdpUtils.configLogger();
    
    new OshiTest();
    
  }

}
