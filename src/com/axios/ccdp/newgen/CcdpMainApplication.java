package com.axios.ccdp.newgen;

import java.io.File;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.log4j.Logger;

import com.axios.ccdp.connections.intfs.CcdpEventConsumerIntf;
import com.axios.ccdp.connections.intfs.CcdpObjectFactoryAbs;
import com.axios.ccdp.connections.intfs.CcdpStorageControllerIntf;
import com.axios.ccdp.connections.intfs.CcdpTaskingControllerIntf;
import com.axios.ccdp.connections.intfs.CcdpVMControllerIntf;
import com.axios.ccdp.resources.CcdpVMResource;
import com.axios.ccdp.resources.CcdpVMResource.ResourceStatus;
import com.axios.ccdp.tasking.CcdpThreadRequest;
import com.axios.ccdp.utils.CcdpUtils;
import com.axios.ccdp.utils.TaskEventIntf;
import com.axios.ccdp.utils.ThreadedTimerTask;
import com.axios.ccdp.utils.CcdpUtils.EventType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

public class CcdpMainApplication implements CcdpEventConsumerIntf, TaskEventIntf
{
  /**
   * Stores the name of the session with available resources
   */
  private static final String FREE_SESSION = "available";
  
  /**
   * Generates debug print statements based on the verbosity level.
   */
  private Logger logger = Logger.getLogger(CcdpMainApplication.class.getName());
  /**
   * Parses and prints all the options or arguments used by this application
   */
  private static HelpFormatter helpFormatter = new HelpFormatter();
  /**
   * Stores all the options that can be used by this application
   */
  private static Options options = new Options();
  /**
   * Provides a consolidated way to format dates
   */
  private SimpleDateFormat formatter = 
      new SimpleDateFormat("yyyy-mm-dd hh:mm:ss");
  /**
   * Creates all the ArrayNode and ObjectNode
   */
  private ObjectMapper mapper = new ObjectMapper();
  /**
   * Stores a list of requests to process.  Each request is a processing thread
   * containing one or more processing task.
   */
  private ConcurrentLinkedQueue<CcdpThreadRequest> 
                requests = new ConcurrentLinkedQueue<>();
  
  /**
   * Object used to send and receive messages such as incoming tasks to 
   * process, heartbeats, and tasks updates
   */
  private CcdpConnectionIntf connection;
  /**
   * Stores the object that determines the logic to assign tasks to VMs
   */
  private CcdpTaskingControllerIntf tasker = null;
  /**
   * Controls all the VMs
   */
  private CcdpVMControllerIntf controller =null;
  /**
   * Object responsible for creating/deleting files
   */
  private CcdpStorageControllerIntf storage = null;
  /**
   * Stores all the VMs allocated to different sessions
   */
  private Map<String, CcdpVMResource> resources = new HashMap<>();
  /**
   * Stores the instance id of the EC2 running this framework
   */
  private String instanceId = null;
  /**
   * Continuously monitors the state of the system
   */
  private ThreadedTimerTask timer = null;
  
  
  /**
   * Instantiates a new object and if the 'jobs' argument is not null then
   * it executes all the tasks specified in the given file
   * 
   * @param json_file and optional file containing a series of tasks to execute
   *        at startup
   */
  public CcdpMainApplication( String json_file )
  {
 // creating the factory that generates the objects used by the scheduler
    String clazz = CcdpUtils.getProperty(CcdpUtils.CFG_KEY_FACTORY_IMPL);
    if( clazz != null )
    {
      CcdpObjectFactoryAbs factory = CcdpObjectFactoryAbs.newInstance(clazz);
      ObjectNode task_msg_node = 
          CcdpUtils.getJsonKeysByFilter(CcdpUtils.CFG_KEY_CONN_INTF);
      ObjectNode task_ctr_node = 
          CcdpUtils.getJsonKeysByFilter(CcdpUtils.CFG_KEY_TASK_CTR);
      ObjectNode res_ctr_node = 
          CcdpUtils.getJsonKeysByFilter(CcdpUtils.CFG_KEY_RESOURCE);
      ObjectNode storage_node = 
          CcdpUtils.getJsonKeysByFilter(CcdpUtils.CFG_KEY_STORAGE);
      
      this.connection = factory.getCcdpConnectionInterface(task_msg_node);
      this.tasker = factory.getCcdpTaskingController(task_ctr_node);
      this.controller = factory.getCcdpResourceController(res_ctr_node);
      this.storage = factory.getCcdpStorageControllerIntf(storage_node);
      
      this.connection.configure(task_msg_node);
      this.connection.setConsumer(this);
      
      String toMain = CcdpUtils.getProperty(CcdpUtils.CFG_KEY_MAIN_CHANNEL);
      this.logger.info("Registering as " + this.instanceId);
      this.connection.registerConsumer(this.instanceId, toMain);
      
    }
    else
    {
      String txt = "Could not find factory.  Please check configuration." +
                   "The key " + CcdpUtils.CFG_KEY_FACTORY_IMPL + " is missing";
      this.logger.error(txt);
      System.exit(-1);
    }
    
    try
    {
      this.instanceId = CcdpUtils.retrieveEC2Info("instance-id");
      this.logger.info("Framework running on instance: " + this.instanceId );
    }
    catch( Exception e )
    {
      this.logger.warn("Could not get Instance ID, assigning one");
    }

    // Let's check what is out there....
    int cycle = 5;;
    try
    {
      cycle = CcdpUtils.getIntegerProperty(CcdpUtils.CFG_KEY_CHECK_CYCLE);
    }
    catch( Exception e )
    {
      this.logger.warn("Could not parse the cycle, using default (5)");
    }
    
    cycle *= 1000;
    // wait twice the cycle time to allow time to the nodes to offer resources
    this.timer = new ThreadedTimerTask(this, cycle);
    
    List<CcdpThreadRequest> requests = new ArrayList<CcdpThreadRequest>();
    File json_jobs = null;
    
    if( json_file != null )
    {
      json_jobs = new File(json_file);
      this.logger.debug("Loading File: " + json_file);
      
      // loading Jobs from the command line
      if( json_jobs.isFile() )
      {
        try
        {
          requests = CcdpUtils.toCcdpThreadRequest( json_jobs );
          this.logger.debug("Number of Jobs: " + requests.size() );
          for( CcdpThreadRequest req : requests )
            this.addRequest( req );
        }
        catch( Exception e )
        {
          this.logger.error("Could not parse Jobs file " + json_file);
          this.logger.error("Message: " + e.getMessage(), e);
        }
      }
      else
      {
        String msg = "The Json Jobs file (" + json_file + ") is invalid";
        this.logger.error(msg);
        CcdpMainApplication.usage(msg);
      }
    }
  }
  
  
  
  /**
   * Method invokes continuously to monitor the state of the system by the
   * ThreadedTimerTask object.  It determines whether or not we need to launch
   * free resources and/or terminate unused ones  
   */
  public void onEvent()
  {
    synchronized( this.resources )
    {
      this.checkMinVMRequirements();
      
      for( String key : this.resources.keySet() )
      {
        CcdpVMResource res = this.resources.get(key);
        this.checkDeallocation(res.getAssignedSession());
      }
    }
  }
  
  
  /**
   * Gets a JsonNode event of the form:
   *  {
   *    config: dictionary with configuration to add,
   *    body:
   *      {
   *        event-type: the event type (TASK_REQUEST, HEARTBEAT, TASKS_STATUS },
   *        event: the actual event such as the JSON representation of at task
   *      }
   *  }
   *  
   *  @param event the JSON object as described above
   */
  public void onEvent( Object event )
  {
    
    if( event instanceof JsonNode )
    {
      JsonNode node = (JsonNode)event;
      
      if( node.has("body") )
        node = node.get("body");
      
      this.logger.debug("Got a new Event: " + node.toString());
      Iterator<String> names = node.fieldNames(); 
      while( names.hasNext() )
      {
        String name = names.next();
        
        this.logger.debug("Name " + name );
      }
      
      if( !node.has("event-type") )
      {
        this.logger.error("Cannot procees an event without 'event-type' set");
        return;
      }
      
      if( !node.has("event") )
      {
        this.logger.error("Cannot procees an event without 'event' set");
        return;
      }
      
      EventType type = EventType.valueOf( node.get("event-type").asText() );
      this.logger.debug("Processing an event of type " + type);
      
      JsonNode jsonEvent = node.get("event");
      
      switch( type )
      {
      case TASK_REQUEST:
        try
        {
          for( CcdpThreadRequest r : CcdpUtils.toCcdpThreadRequest(jsonEvent) )
          {
            this.addRequest( r );
          }
        }
        catch( Exception e )
        {
          this.logger.error("Message: " + e.getMessage(), e);
        }
        
        break;
      case KILL_TASK:
        this.killTask(jsonEvent);
        break;
      case HEARTBEAT:
        this.updateResource(jsonEvent);
        break;
      case TASK_STATUS:
        this.updateStatus(jsonEvent);
        break;
        default:
          String msg = "CcdpAgent does not process events of type " + type +
                       "  It only accepts TASK_REQUEST and KILL_TASK ";
          this.logger.warn(msg);
      }
    }
  }
  
  private void killTask(JsonNode node )
  {
    
  }
  
  private void updateResource(JsonNode node )
  {
    
  }
  
  private void updateStatus(JsonNode node )
  {
    
  }
  
  
  
  public void addRequest( CcdpThreadRequest request )
  {
    if( request == null )
    {
      this.logger.error("The request cannot be null!!");
      return;
    }
    
    // do we need to assign a new resource to this session?
    this.logger.info("Got a new Request: " + request.toString() );
    
    String sid = request.getSessionId();
    if( sid != null && sid.length() > 0 )
    {
      this.logger.info("Checking for resources assigned to " + sid);
      List<CcdpVMResource> list = this.getResources(request);
      int sz = list.size();
      this.logger.info("Session " + sid + " has " + sz + " VMs assigned");
      if( sz == 0 )
      {
        this.logger.info("Zero resources available, launchin one");
        List<String> launched = this.controller.startInstances(1, 1, sid);
        for( String id : launched )
        {
          CcdpVMResource vm = new CcdpVMResource(id);
          vm.setStatus(ResourceStatus.LAUNCHED);
          vm.setAssignedSession(sid);
          synchronized( this.resources )
          {
            this.logger.info("Adding new VM " + id);
            this.resources.put(id, vm);
          }
        }
      }
    }
    
    // adding the request
    synchronized( this.requests )
    {
      this.requests.add( request );
    }
  }
  
  

  
  /**
   * Gets all the resources assigned to the session.  The following checks are
   * done in order to return the appropriate list
   * 
   *    - The request cannot be null
   *    - The session-id cannot be null
   *    - At least one of the tasks in the request need to be submitted
   *    - The thread is not complete
   *    - The session exists in the list sessions container
   *    
   * @param req the request that needs to be processed and need resources
   * 
   * @return a list of resources available to process this request
   */
  private List<CcdpVMResource> getResources(CcdpThreadRequest req)
  {
    if( req == null )
    {
      this.logger.error("Cannot get resource for a NULL request");
      return null;
    }
    
    String id = req.getThreadId();
    String sid = req.getSessionId();
    
    this.logger.info("Assigning Resources to Request " + id + " Session " + sid);
    // is there a problem with the session-id?
    if( sid == null )
    {
      this.logger.error("The Thread " + id + " does not have Session ID");
      return null;
    }
    
    // have all the tasks been submitted already?
    if( req.isTasksSubmitted() )
    {
      this.logger.info("All the tasks have been submitted for " + id);
      return null;
    }
    
    // Is this thread done?
    if( req.threadRequestCompleted() )
    {
      this.logger.info("Thread " + id + " for Session " + sid + " Complete");
      synchronized( this.requests )
      {
        this.requests.remove(req);
      }
      return null;
    }
    
    List<CcdpVMResource> list = this.getResourcesBySessionId(sid);
    
    if ( this.tasker.needResourceAllocation(list) )
    {
      this.logger.info("The session id " + sid + 
                       " does not have resources, checking free");
      List<CcdpVMResource> free_vms = 
                    this.getResourcesBySessionId(FREE_SESSION);
      if( free_vms.size() > 0 )
      {
        CcdpVMResource res = free_vms.get(0);
        String iid = res.getInstanceId();
        this.logger.info("Assigning VM " + res.getInstanceId() + " to " + sid);
        ResourceStatus stat = res.getStatus();
        if( ResourceStatus.LAUNCHED.equals(stat) || 
            ResourceStatus.RUNNING.equals(stat))
        {
          res.setAssignedSession(sid);
          res.setStatus(ResourceStatus.REASSIGNED);
          synchronized( this.resources )
          {
            this.resources.put(iid, res);
          }
          list.add(res);
          // took one resource, check minimum requirement again
          this.checkMinVMRequirements();
        }
        else
          this.logger.info("Reource was not assigned due to: " + stat);
        
      }
      else
      {
        this.logger.info("Did not find an available resource, creating one");
        List<String> launched = this.controller.startInstances(1, 1);
        
        for( String iid : launched )
        {
          CcdpVMResource resource = new CcdpVMResource(iid);
          resource.setStatus(ResourceStatus.LAUNCHED);
          resource.setAssignedSession(sid);
          this.logger.debug("Adding resource " + resource.toString());
          synchronized( this.resources )
          {
            this.resources.put(iid, resource);
          }
          list.add(resource);
          // had to create one, is this OK?
          this.checkMinVMRequirements();
        }        
      }
    }
    
    this.logger.debug("Returning a list of resources size " + list.size());
    // Getting all the resources for this session
    return list;
  }
  
  /**
   * Checks the minimum number of available VMs required by the framework and
   * deploy as many instances as needed.
   * 
   */
  private void checkMinVMRequirements()
  {
    // Starting the minimum number of free resources needed to run
    try
    {
      synchronized(this.resources)
      {
//        if( !CcdpUtils.getBooleanProperty(CcdpUtils.CFG_KEY_SKIP_AGENT) )
//        {
//          if( this.resources.size() == 0  && this.instanceId != null )
//          {
//            this.logger.info("Adding Framework Instance ID to available");
//            CcdpVMResource vm = new CcdpVMResource(this.instanceId);
//            vm.setAssignedSession(FREE_SESSION);
//            vm.setStatus(ResourceStatus.INITIALIZING);
//            this.resources.put(this.instanceId, vm);
//          }
//        }
//        else
//          this.logger.warn("Skipping Local Mesos Agent");
        
        int free_vms = 
          CcdpUtils.getIntegerProperty(CcdpUtils.CFG_KEY_INITIAL_VMS);
        List<CcdpVMResource> avails = 
            this.getResourcesBySessionId(FREE_SESSION); 
        int available = avails.size();
        
        if( free_vms > 0 )
        {
          int need = free_vms - available;
          if( need > 0 )
          {
            this.logger.info("Starting " + need + " free agents");
            int min = need;
            int max = need;
            List<String> launched = this.controller.startInstances(min, max);
          
            for( String id : launched )
            {
              CcdpVMResource resource = new CcdpVMResource(id);
              resource.setStatus(ResourceStatus.LAUNCHED);
              resource.setAssignedSession(FREE_SESSION);
              this.logger.debug("Adding resource " + resource.toString());
              this.resources.put(id, resource);
            }
            
          }// need to deploy agents
        
        }// I do need free agents
        
        // Now checking to make sure there are no more free agents than needed        
        this.logger.debug("Making sure we deallocate free nodes as well");
        int over = available - free_vms;
        
        int done = 0;
        List<String> terminate = new ArrayList<>();
        // Do it only if we have more available VMs than needed
        if( over > 0 )
        {
          for( CcdpVMResource res : avails )
          {
            if( done == over )
              break;
            
            // making sure we do not shutdown the framework node
            String id = res.getInstanceId();
            if( !id.equals( this.instanceId ) && 
                ResourceStatus.RUNNING.equals( res.getStatus() ) )
            {
              this.logger.info("Flagging VM " + id + " for termination");
              res.setStatus(ResourceStatus.SHUTTING_DOWN);
              terminate.add(id);
              done++;
            }// done searching for running VMs
          }// done with the VMs
        }
        int sz = terminate.size();
        if( sz > 0 )
        {
          this.logger.info("Terminating " + terminate.toString() );
          this.controller.terminateInstances(terminate);
        }
        
        
      }// end of the sync block
    }
    catch( Exception e )
    {
      String msg = "Error parsing the integer containing initial agents. "
          + "Message " + e.getMessage();
      this.logger.error(msg);
      e.printStackTrace();
    }
  }
  
  /**
   * Determines whether or not there are resources that need to be terminated
   * for a specific session id
   * 
   * @param sid the session id that has some activity and whose resources need
   *        need to be checked
   */
  private void checkDeallocation( String sid )
  {
    List<CcdpVMResource> sid_vms = this.getResourcesBySessionId(sid);
    
    // Do we need to deallocate resources?
    List<CcdpVMResource> vms = this.tasker.deallocateResource(sid_vms);
    List<String> terminate = new ArrayList<>();
    for( CcdpVMResource vm : vms )
    {
      String iid = vm.getInstanceId();
      this.logger.debug("Comparing Master " + this.instanceId + " and " + iid);
      if( iid != null && !iid.equals(this.instanceId) )
      {
        this.logger.info("Freeing VM " + iid);
        terminate.add(iid);
        // this way we can compare/use it as available if needed
        vm.setAssignedSession(FREE_SESSION);
      }
      else
      {
        this.logger.info("Will not terminate master " + iid);
        vm.setSingleTask(null);
        
      }
    }
  }
  
  
  /**
   * Gets all the resources belonging to the given Session Id.  If the session
   * does not contain any resource allocated then it returns an empty list.
   * 
   * @param sid the session id to get the resources from
   * 
   * @return a list of resources allocated to the session or an empty list 
   *         otherwise
   */
  private List<CcdpVMResource> getResourcesBySessionId( String sid )
  {
    List<CcdpVMResource> list = new ArrayList<>();
    if( sid == null )
      return list;
    
    this.logger.debug("Have " + this.resources.size() + " resources");
    for( CcdpVMResource res : this.resources.values() )
    {
      String asid = res.getAssignedSession();
      this.logger.debug("Comparing given sid: " + sid + " against " + asid );
      if( sid.equals(asid) && 
          !ResourceStatus.SHUTTING_DOWN.equals(res.getStatus() ) )
      {
        this.logger.debug("Found Resource based on SID, adding it to list");
        list.add(res);
      }
    }
    
    return list;
  }
  
  
  /**
   * Prints a message indicating how to use this framework and then quits
   * 
   * @param msg the message to display on the screen along with the usage
   */
  private static void usage(String msg) 
  {
    if( msg != null )
      System.err.println(msg);
    
    helpFormatter.printHelp(CcdpMainApplication.class.toString(), options);
    System.exit(1);
  }
  
  /**
   * Starts an agent to execute commands sent through the CcdpConnectionIntf
   * protocol
   * 
   * @param args the command line arguments
   * @throws Exception an exception is thrown if an error occurs
   */
  public static void main(String[] args) throws Exception
  {
    // building all the options available
    String txt = "Path to the configuration file.  This can also be set using "
        + "the System Property 'ccdp.config.file'";
    Option config = new Option("c", "config-file", true, txt);
    config.setRequired(false);
    options.addOption(config);
    
    Option jobs = new Option("f", "file-jobs", true, 
        "Optional JSON file with the jobs to run");
    jobs.setRequired(false);
    options.addOption(jobs);

    Option help = new Option("h", "help", false, "Shows this message");
    help.setRequired(false);
    options.addOption(help);

    
    CommandLineParser parser = new DefaultParser();
    
    CommandLine cmd;

    try 
    {
      cmd = parser.parse(options, args);
    } 
    catch (ParseException e) 
    {
      System.out.println(e.getMessage());
      helpFormatter.printHelp("utility-name", options);

      System.exit(1);
      return;
    }
    // if help is requested, print it an quit
    if( cmd.hasOption('h') )
    {
      helpFormatter.printHelp(CcdpAgent.class.toString(), options);
      System.exit(0);
    }
    String cfg_file = null;
    String key = CcdpUtils.CFG_KEY_CFG_FILE;
    String jobs_file = null;
    
    // do we have a valid job file?
    if( cmd.hasOption('f') )
    {
      String fname = CcdpUtils.expandVars( cmd.getOptionValue('f') );
      File file = new File( fname );
      if( file.isFile() )
        jobs_file = fname;
      else
        usage("The jobs file (" + fname + ") was provided, but is invalid");
    }
    
    // do we have a configuration file? if not search for the System Property
    boolean loaded = false;
    if( cmd.hasOption('c') )
    {
      cfg_file = cmd.getOptionValue('c');
    }
    else if( System.getProperty( key ) != null )
    {
      String fname = CcdpUtils.expandVars(System.getProperty(key));
      File file = new File( fname );
      if( file.isFile() )
        cfg_file = fname;
      else
        usage("The config file (" + fname + ") is invalid");
    }
    
    // If it was not specified, let's try as part of the classpath using the
    // default name stored in CcdpUtils.CFG_FILENAME
    if( cfg_file == null )
    {
      String name = CcdpUtils.CFG_FILENAME;
      URL url = CcdpUtils.class.getClassLoader().getResource(name);
      
      // making sure it was found
      if( url != null )
      {
        System.out.println("Configuring CCDP using URL: " + url);
        CcdpUtils.loadProperties( url.openStream() );
        loaded = true;
      }
      else
      {
        System.err.println("Could not find " + name + " file");
        usage("The configuration is null, but it is required");
      }
    }
    
    if( !loaded )
    {
      System.err.println("The Config File: " + cfg_file);
      CcdpUtils.loadProperties(cfg_file);
    }
    
    CcdpUtils.configLogger();
    new CcdpMainApplication(jobs_file);
    
  }
}
