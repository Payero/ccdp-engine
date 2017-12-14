package com.axios.ccdp.cloud.mock;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.apache.log4j.Logger;

import com.axios.ccdp.connections.intfs.CcdpVMControllerIntf;
import com.axios.ccdp.resources.CcdpVMResource;
import com.axios.ccdp.resources.CcdpVMResource.ResourceStatus;
import com.axios.ccdp.tasking.CcdpTaskRequest;
import com.axios.ccdp.utils.CcdpImageInfo;
import com.axios.ccdp.utils.CcdpUtils.CcdpNodeType;
import com.fasterxml.jackson.databind.node.ObjectNode;

public class MockCcdpVMControllerImpl implements CcdpVMControllerIntf
{

  /**
   * Generates debug print statements based on the verbosity level.
   */
  private Logger logger = Logger
      .getLogger(MockCcdpVMControllerImpl.class.getName());

  /**
   * Stores all the Virtual Machines based on the Session they are running.
   * Each session id has a list of VMs assigned to it
   */
  Map<String, List<MockVirtualMachine>> nodes = new HashMap<>();

  /**
   * Stores the object's configuration parameters
   */
  private ObjectNode config = null;
  
  public MockCcdpVMControllerImpl()
  {
    this.logger.debug("Creating a new Mock VM Controller");
  }

  /**
   * Configures the object which in this case means nothing more than setting
   * the config object
   * 
   * @param config the object containing all the configuration parameters
   */
  @Override
  public void configure(ObjectNode config)
  {
    logger.debug("Configuring ResourceController using: " + config);
    // the configuration is required
    if( config == null )
      throw new IllegalArgumentException("The config cannot be null");
    
    this.config = config;

  }

  /**
   * Starts one or more VM instances using the defined Image ID as given by the
   * imageId argument.  The number of instances are determined by the min and 
   * max arguments.  If the tags is not null then they are set and the new 
   * Virtual Machine will contain them.
   * 
   * @param imgCfg the image configuration containing all the parameters 
   *        required to start an instance
   * 
   * 
   * @return a list of unique Virtual Machine identifiers
   */
  @Override
  public List<String> startInstances(CcdpImageInfo imgCfg)
  {
    
    int max = imgCfg.getMaxReq();
    
    CcdpNodeType type = imgCfg.getNodeType();
    String typeStr = type.toString();
    
    this.logger.info("Launching " + max + " Nodes of type " + typeStr );
    List<String> launched = new ArrayList<>();
    for(int i = 0; i < max; i++ )
    {
      MockVirtualMachine node = new MockVirtualMachine( type );
      node.setTags(imgCfg.getTags());
      
      launched.add(node.getVirtualMachineInfo().getNodeTypeAsString());
      if( !this.nodes.containsKey(typeStr) )
      {
        this.logger.info("Creating a new List for " + typeStr);
        List<MockVirtualMachine> vms = new ArrayList<>();
        this.nodes.put(typeStr, vms);
      }
      this.nodes.get(typeStr).add(node);
    }
    
    return launched;
  }


  /**
   * Mark as Stopped each one of the Virtual Machines whose unique identifier 
   * matches the ones given in the argument
   * 
   * @param instIDs a list of unique identifiers used to determine which Virtual
   *        Machine needs to be stopped
   *        
   * @return true if the request was submitted successfully or false otherwise
   */
  @Override
  public boolean stopInstances(List<String> instIDs)
  {
    return this.performAction("stop", instIDs);
  }

  /**
   * Mark as Terminated each one of the Virtual Machines whose unique  
   * identifier matches the ones given in the argument
   * 
   * @param instIDs a list of unique identifiers used to determine which Virtual
   *        Machine needs to be stopped
   *        
   * @return true if the request was submitted successfully or false otherwise
   */
  @Override
  public boolean terminateInstances(List<String> instIDs)
  {
    return this.performAction("terminate", instIDs);
  }

  /**
   * Performs a specific action; stop or terminate, to a list of Virtual 
   * Machines.
   * 
   * @param action either stop or terminate
   * @param hostIds a list of unique identifiers to perform the action
   * 
   * @return true if all the actions were performed or false otherwise
   */
  private boolean performAction( String action, List<String> hostIds)
  {
    boolean done = true;
    try
    {
      // first we need to get all the lists
      for( String nodeType : this.nodes.keySet() )
      {
        List<MockVirtualMachine> vms = this.nodes.get(nodeType);
        // for each list, get all the host ids and compare
        for( MockVirtualMachine vm : vms )
        {
          String hostId = vm.getVirtualMachineInfo().getInstanceId();
          for( String id : hostIds )
          {
            // found it, skip the rest
            if( id.equals(hostId) )
            {
              vm.changeVirtualMachineState(action);
              continue;
            }
          }
        }
      }
    }
    catch( Exception e)
    {
      done = false;
      this.logger.error("Message: " + e.getMessage(), e);
    }
    
    return done;
  }
  
  /**
   * Gets all the instances status that are currently 'available' on different
   * states
   * 
   * @return a list with all the instances status that are currently 
   *         'available' on different state
   */
  @Override
  public List<CcdpVMResource> getAllInstanceStatus()
  {
    this.logger.info("Getting all the resources");
    
    List<CcdpVMResource> resources = new ArrayList<>();
    try
    {
      // first we need to get all the lists
      for( String nodeType : this.nodes.keySet() )
      {
        List<MockVirtualMachine> vms = this.nodes.get(nodeType);
        // for each list, get all the host ids and compare
        for( MockVirtualMachine vm : vms )
        {
          resources.add( vm.getVirtualMachineInfo() );
        }
      }
    }
    catch( Exception e)
    {
      this.logger.error("Message: " + e.getMessage(), e);
    }
    
    this.logger.info("Found " + resources.size() + " resources total");
    return resources;
  }

  /**
   * Gets the current instance state of the resource with the given id
   * 
   * @return the status of the resource
   */
  @Override
  public ResourceStatus getInstanceState(String id)
  {
    ResourceStatus status = null;
    
    try
    {
      // first we need to get all the lists
      for( String nodeType : this.nodes.keySet() )
      {
        List<MockVirtualMachine> vms = this.nodes.get(nodeType);
        // for each list, get all the host ids and compare
        for( MockVirtualMachine vm : vms )
        {
          CcdpVMResource res = vm.getVirtualMachineInfo(); 
          String hostId = res.getInstanceId();
          this.logger.debug("Comparing " + id + " against " + hostId);
          if( hostId.equals(id) )
          {
            this.logger.info("Found Host ID " + id);
            return res.getStatus();
          }
        }
      }
    }
    catch( Exception e)
    {
      this.logger.error("Message: " + e.getMessage(), e);
    }
    
    return status;
  }

  /**
   * Returns information about all instances matching the set of filters given
   * by the filter JSON object.  In other words, if the instance contains a tag
   * matching ALL the names and values of the given in the filter then is 
   * flagged as a valid result.
   * 
   * The result is a JSON Object whose key is the Virtual Machine identifier and
   * the value is detailed information of the VM.
   * 
   * @param filter a JSON object containing the criteria to filter the Virtual
   *        Machines
   *        
   * @return A JSON Object containing all the Virtual Machines matching the 
   *         criteria
   */
  @Override
  public List<CcdpVMResource> getStatusFilteredByTags(ObjectNode filter)
  {
    logger.debug("Getting Filtered Status using: " + filter);
    List<CcdpVMResource> all = this.getAllInstanceStatus();
    List<CcdpVMResource> some = new ArrayList<>();
    
    logger.debug("All Instances: " + all);
    
    for(CcdpVMResource inst : all )
    {
      String id = inst.getInstanceId();
      logger.debug("Looking at ID " + id);
      Map<String, String> tags = inst.getTags();
      
      if( tags != null  )
      {
        Iterator<String> filter_keys = filter.fieldNames();
        boolean found = true;
        while( filter_keys.hasNext() )
        {
          String key = filter_keys.next();
          Object val = filter.get(key);
          logger.debug("Evaluating Filter[" + key + "] = " + val );
          if( !tags.containsKey(key) || !tags.get(key).equals(val) )
          {
            logger.info("Instance " + id + " does not have matching tag " + key);
            found = false;
            break;
          }
        }// end of filter keys while loop
        
        // if all the keys and values matched, then add it to the final result
        if( found )
        {
          logger.info("Adding Instance to list");
          some.add(inst);
        }
      }// it has tags to compare
    }// All instances checked
    
    return some;
  }

  /**
   * Returns information about the instance matching the unique id given as 
   * argument.  If the object is not found it returns null 
   * 
   * 
   * @param uuid the unique identifier used to select the appropriate resource
   *        
   * @return the resource whose unique identifier matches the given argument
   */
  @Override
  public CcdpVMResource getStatusFilteredById(String uuid)
  {
    logger.debug("Getting Filtered Status for: " + uuid);
    List<CcdpVMResource> all = this.getAllInstanceStatus();
    
    logger.debug("All Instances: " + all);
    
    for( CcdpVMResource res : all )
    {
      String id = res.getInstanceId();
      logger.debug("Looking at ID " + id);
      
      // found it
      if(id.equals(uuid))
        return res;
      
    }// All instances checked
    
    return null;
  }
  
  public String getStatusSummary()
  {
    StringBuffer buf = new StringBuffer();
    buf.append("NodeType - \tState - \tTasks\n");
    
    for( String type : this.nodes.keySet() )
    {
      buf.append(type + "\n");
      buf.append("-----------------------------------------------------------\n");
      List<MockVirtualMachine> vms = this.nodes.get(type);
      for( MockVirtualMachine vm : vms )
      {
        CcdpVMResource info = vm.getVirtualMachineInfo();
        String id = info.getInstanceId();
        String status = info.getStatus().toString();
        buf.append("\t- " + id + " - \t" + status + " - \tTasks");
        List<CcdpTaskRequest> tasks = info.getTasks();
        for( CcdpTaskRequest task : tasks )
        {
          String tid = task.getTaskId();
          String state = task.getState().toString();
          buf.append("\t     \t   \t - " + tid + " - \t" + state);
        } // end of the tasks
      }// end of the VMs
    }// end of the Node Types
    
    
    return buf.toString();
  }
  
}
