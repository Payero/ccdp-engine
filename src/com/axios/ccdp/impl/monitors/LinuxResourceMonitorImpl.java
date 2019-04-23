package com.axios.ccdp.impl.monitors;

import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import com.axios.ccdp.utils.CcdpUtils;
import com.fasterxml.jackson.databind.JsonNode;

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
public class LinuxResourceMonitorImpl extends SystemResourceMonitorAbs
{
  /**
   * Stores all the different types of file system storages to include
   */
  private static List<String> FILE_STORE_TYPES = 
      Arrays.asList( new String[] {"ext3", "ext4", "nfs", "xfs"}); 
  
  /**
   * Instantiates a new resource monitor
   */
  public LinuxResourceMonitorImpl()
  {
    super();
  }
  
  /**
   * Instantiates a new resource monitor
   * 
   * @param units the units to use when displaying some of the values
   */
  public LinuxResourceMonitorImpl( String units )
  {
    super( units );
  }
  
  /**
   * Instantiates a new resource monitor
   * 
   * @param units the units to use when displaying some of the values
   */
  public LinuxResourceMonitorImpl( UNITS units )
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
  public void configure( JsonNode config )
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
   * Gets all the different file system storage names such as ext3, ext4, NTFS,
   * etc.
   * 
   * @return all the different file system storage names such as ext3, ext4,
   *         NTFS, etc
   */
  protected List<String> getFileSystemTypes()
  {
    return LinuxResourceMonitorImpl.FILE_STORE_TYPES;
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
    
    LinuxResourceMonitorImpl srm = new LinuxResourceMonitorImpl(UNITS.MB);
    
    
//    while( true )
//    {
      System.out.println("");
      System.out.println("***************************************************");
      System.out.println(srm.toPrettyPrint());
      System.out.println("***************************************************");
      System.out.println("");
      
      String[] fs = srm.getFileStorageNames();
      for( String name : fs )
      {
        System.out.println("Storage: " + name);
        Map<String, String> map = srm.getDiskPartitionInfo(name);
        Iterator<String> keys = map.keySet().iterator();
        while( keys.hasNext() )
        {
          String key = keys.next();
          System.out.println("\t[" + key + "] = " + map.get(key) );
          
        }
      }
      
//    }
  }
}
