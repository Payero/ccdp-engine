/**
 * 
 */
package com.axios.ccdp.mesos.fmwk;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;

import org.apache.log4j.Logger;
import org.apache.mesos.Protos.ExecutorID;
import org.apache.mesos.Protos.ExecutorInfo;
import org.apache.mesos.Protos.FrameworkID;
import org.apache.mesos.Protos.MasterInfo;
import org.apache.mesos.Protos.Offer;
import org.apache.mesos.Protos.OfferID;
import org.apache.mesos.Protos.SlaveID;
import org.apache.mesos.Protos.TaskID;
import org.apache.mesos.Protos.TaskState;
import org.apache.mesos.Protos.TaskStatus;
import org.apache.mesos.Protos.TaskStatus.Reason;
import org.apache.mesos.Scheduler;
import org.apache.mesos.SchedulerDriver;

import com.axios.ccdp.mesos.connections.intfs.CcdpObjectFactoryAbs;
import com.axios.ccdp.mesos.connections.intfs.CcdpStorageControllerIntf;
import com.axios.ccdp.mesos.connections.intfs.CcdpTaskConsumerIntf;
import com.axios.ccdp.mesos.connections.intfs.CcdpTaskingControllerIntf;
import com.axios.ccdp.mesos.connections.intfs.CcdpTaskingIntf;
import com.axios.ccdp.mesos.connections.intfs.CcdpVMControllerIntf;
import com.axios.ccdp.mesos.resources.CcdpVMResource;
import com.axios.ccdp.mesos.resources.CcdpVMResource.ResourceStatus;
import com.axios.ccdp.mesos.tasking.CcdpTaskRequest;
import com.axios.ccdp.mesos.tasking.CcdpTaskRequest.CcdpTaskState;
import com.axios.ccdp.mesos.tasking.CcdpThreadRequest;
import com.axios.ccdp.mesos.utils.CcdpUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Class used to coordinate the tasking among multiple Virtual Machines.  It 
 * uses the information on the task to make determinations regarding where to 
 * execute the task.  Information affecting the task execution includes, but 
 * not limited, the following fields:
 * 
 *  - NodeType: Based on the node type, it can run on a simple EC2 instance or
 *              a cluster such as EMR, Hadoop, etc.
 *  - CPU:  The CPU value determines the schema to use as follow:
 *      CPU = 0:        Let the Scheduler decide where to run it
 *      0 > CPU < 100:  Use the first VM with enough resources to run the task
 *      CPU = 100:      Run this task by itself on a new VM
 *  
 * @author Oscar E. Ganteaume
 *
 */
public abstract class CcdpMesosScheduler implements Scheduler, CcdpTaskConsumerIntf
{
  /**
   * Creates all the ArrayNode and ObjectNode
   */
  protected ObjectMapper mapper = new ObjectMapper();
  /**
   * Stores the Framework Unique ID this Scheduler is running under
   */
  protected FrameworkID fmwkId;
  /**
   * Stores the Executor responsible for running the tasks
   */
  protected ExecutorInfo executor;
  /**
   * Stores the scheduler driver requesting the command execution
   */
  protected SchedulerDriver driver = null;
  /**
   * Generates debug print statements based on the verbosity level.
   */
  protected Logger logger = 
      Logger.getLogger(CcdpMesosScheduler.class.getName());
  
  /**
   * Stores a list of requests to process.  Each request is a processing thread
   * containing one or more processing task.
   */
  protected ConcurrentLinkedQueue<CcdpThreadRequest> 
                requests = new ConcurrentLinkedQueue<>();
  /**
   * Stores the object responsible for creating all interfaces
   */
  protected CcdpObjectFactoryAbs factory = null;
  /**
   * Stores the object responsible for sending and receiving tasking information
   */
  protected CcdpTaskingIntf taskingInf = null;
  /**
   * Stores the object that determines the logic to assign tasks to VMs
   */
  protected CcdpTaskingControllerIntf tasker = null;
  /**
   * Controls all the VMs
   */
  protected CcdpVMControllerIntf controller =null;
  /**
   * Object responsible for creating/deleting files
   */
  protected CcdpStorageControllerIntf storage = null;
  
  /**
   * Stores all the VMS or resources that have not been allocated to a 
   * particular session
   */
  protected List<CcdpVMResource> free_vms = 
                Collections.synchronizedList(new ArrayList<CcdpVMResource>());
  
  /**
   * Stores all the VMs allocated to different sessions
   */
  protected Map<String, List<CcdpVMResource>> sessions = new HashMap<>();
  
  /**
   * Instantiates a new executors and starts the jobs assigned as the jobs
   * argument.  If the jobs is null then it ignores them
   * 
   * @param execInfo the name of the executor to use to execute the tasks
   * @param jobs an optional list of jobs
   */
  public CcdpMesosScheduler( ExecutorInfo execInfo, List<CcdpThreadRequest> jobs)
  {
    this.logger.debug("Creating a new CCDP Remote Scheduler");
    this.executor = execInfo;
    
    // creating the factory that generates the objects used by the scheduler
    String clazz = CcdpUtils.getProperty(CcdpUtils.CFG_KEY_FACTORY_IMPL);
    if( clazz != null )
    {
      this.factory = CcdpObjectFactoryAbs.newInstance(clazz);
      ObjectNode task_msg_node = 
          CcdpUtils.getJsonKeysByFilter(CcdpUtils.CFG_KEY_TASK_MSG);
      ObjectNode task_ctr_node = 
          CcdpUtils.getJsonKeysByFilter(CcdpUtils.CFG_KEY_TASK_CTR);
      ObjectNode res_ctr_node = 
          CcdpUtils.getJsonKeysByFilter(CcdpUtils.CFG_KEY_RESOURCE);
      ObjectNode storage_node = 
          CcdpUtils.getJsonKeysByFilter(CcdpUtils.CFG_KEY_STORAGE);
      
      this.taskingInf = this.factory.getCcdpTaskingInterface(task_msg_node);
      this.tasker = this.factory.getCcdpTaskingController(task_ctr_node);
      this.controller = this.factory.getCcdpResourceController(res_ctr_node);
      this.storage = this.factory.getCcdpStorageControllerIntf(storage_node);
      
    }
    else
    {
      String txt = "Could not find factory.  Please check configuration." +
                   "The key " + CcdpUtils.CFG_KEY_FACTORY_IMPL + " is missing";
      this.logger.error(txt);
      System.exit(-1);
    }
    
    // Starting the minimum number of free resources needed to run
    try
    {
      int free_vms = 
          CcdpUtils.getIntegerProperty(CcdpUtils.CFG_KEY_INITIAL_VMS);
      if( free_vms > 0 )
      {
        this.logger.info("Starting " + free_vms + " free agents");
        int min = free_vms;
        int max = free_vms;
        List<String> launched = this.controller.startInstances(min, max);
        synchronized(this.free_vms)
        {
          for( String id : launched )
          {
            CcdpVMResource resource = new CcdpVMResource(id);
            resource.setStatus(ResourceStatus.LAUNCHED);
            this.free_vms.add(resource);
          }
        }
      }
    }
    catch( Exception e )
    {
      String msg = "Error parsing the integer containing initial agents. "
          + "Message " + e.getMessage();
      this.logger.error(msg);
      e.printStackTrace();
    }
    
    // Now that some resources has been allocated, we can add the tasks
    if( jobs != null )
    {
      this.logger.info("Adding initial jobs");
      for( CcdpThreadRequest request : jobs )
      {
        this.logger.info("Adding Thread Request: " + request.getThreadId() );
        this.onTask(request);
      }
    }
  }


  /**
   * Invoked when the scheduler successfully registers with a Mesos master. 
   * A unique ID (generated by the master) used for distinguishing this 
   * framework from others and MasterInfo with the IP and port of the current 
   * master are provided as arguments.
   * 
   * @param driver - The scheduler driver that was registered.
   * @param frameworkId - The framework ID generated by the master.
   * @param masterInfo - Info about the current master, including IP and port.
   * 
   */
  @Override
  public void registered(SchedulerDriver driver, FrameworkID fmwkId, MasterInfo master)
  {
    this.logger.info("registered: FwkId " + 
                    fmwkId.getValue() + " MasterInfo " + master.toString() );
    
    this.fmwkId = fmwkId;
    this.driver = driver;
    
    this.taskingInf.setTaskConsumer(this);
    this.taskingInf.register(this.fmwkId.getValue());
  }

  /**
   * Invoked when the scheduler re-registers with a newly elected Mesos master. 
   * This is only called when the scheduler has previously been registered. 
   * MasterInfo containing the updated information about the elected master is 
   * provided as an argument.
   * 
   * @param driver - The driver that was re-registered.
   * @param masterInfo - The updated information about the elected master.
   * 
   */
  @Override
  public void reregistered(SchedulerDriver driver, MasterInfo master)
  {
    this.logger.info("reregistered: "+ driver.toString() + 
                     " MasterInfo " + master.toString() );
    this.driver = driver;
  }
  
  /**
   * Invoked when resources have been offered to this framework. A single offer 
   * will only contain resources from a single slave. Resources associated with 
   * an offer will not be re-offered to _this_ framework until either (a) this 
   * framework has rejected those resources (see 
   * SchedulerDriver.launchTasks(Collection<OfferID>, Collection<TaskInfo>, 
   *                             Filters)) 
   * or (b) those resources have been rescinded (see 
   * offerRescinded(SchedulerDriver, OfferID)). Note that resources may be 
   * concurrently offered to more than one framework at a time (depending on 
   * the allocator being used). In that case, the first framework to launch 
   * tasks using those resources will be able to use them while the other 
   * frameworks will have those resources rescinded (or if a framework has 
   * already launched tasks with those resources then those tasks will fail 
   * with a TASK_LOST status and a message saying as much).
   * 
   * @param driver - The driver that was used to run this scheduler.
   * @param offers - The resources offered to this framework.
   * 
   */
  @Override
  public void resourceOffers(SchedulerDriver driver, List<Offer> offers)
  {
    this.printStatus();
    
    boolean done = true;
    synchronized( this.requests )
    {
      for( CcdpThreadRequest req : this.requests )
      {
        if( req.getPendingTasks() > 0 )
        {
          this.logger.debug("Request " + req.getThreadId() + " has pending tasks");
          done = false;
          break;
        }
      }
    }
    
    // declining offers of we have nothing to run
    if( done )
    {
      this.logger.info("No tasks to run, declining offers");
      for( Offer offer : offers )
      {
        OfferID id = offer.getId();
        this.logger.debug("Declining Offer: " + id.toString());
        driver.declineOffer(id);
      }
    }
    else
    {
      this.handleResourceOffering(offers);
    }
  }

  
  /***************************************************************************/
  /***************************************************************************/
  /***************************************************************************/
  
  /**
   * Prints the current Request and Resources Status.  Useful method to take a
   * quick look at what is happening in the system.  This is invoked every
   * time a resource is offered.
   */
  private void printStatus()
  {
    this.logger.debug("******************************************************");
    this.logger.debug("**********    Current Requests Status    *************");
    this.logger.debug("******************************************************");
    for( CcdpThreadRequest req : this.requests )
    {
      this.logger.debug("Request " + req.toString() );
    }
    
    this.logger.debug("******************************************************");
    this.logger.debug("******************************************************");
    
    this.logger.debug("======================================================");
    this.logger.debug("==========    Current Resources    ===================");
    this.logger.debug("======================================================");
    
    for( String sid : this.sessions.keySet() )
    {
      this.logger.debug("----------------------------------------------------");
      this.logger.debug(sid);
      this.logger.debug("----------------------------------------------------");
      for( CcdpVMResource res : this.sessions.get(sid) )
      {
        this.logger.debug(res.toString());
      }
      this.logger.debug("----------------------------------------------------");
      this.logger.debug("----------------------------------------------------");
    }
  }
  
  /**
   * Makes sure that what the Master Mesos believes is the state of the cluster
   * matches to the Scheduler's view.  Messages between framework schedulers 
   * and the Mesos master may be dropped due to failures and network partitions. 
   * This may cause a framework scheduler and the master to have different views 
   * of the current state of the cluster
   */
  public void reconcileTasks()
  {
    this.logger.debug("Reconciling Tasks");
    List<TaskStatus> runningTasks = new ArrayList<>();
    for( CcdpThreadRequest req: this.requests )
    {
      for( CcdpTaskRequest task: req.getTasks() )
      {
        if( task.getState() == CcdpTaskState.RUNNING )
        {
          TaskID id = TaskID.newBuilder()
              .setValue( task.getTaskId() )
              .build();
          SlaveID slaveId = SlaveID.newBuilder()
             .setValue( task.getAgentId() )
             .build();
          
          this.logger.debug("Reconciling Task: " + task.getTaskId() );
          TaskStatus.Builder bldr = TaskStatus.newBuilder();
          bldr.setSlaveId(slaveId);
          bldr.setTaskId(id);
          bldr.setState(TaskState.TASK_RUNNING);
          runningTasks.add( bldr.build() );
        }
      }
    }
    
    this.driver.reconcileTasks(runningTasks);
  }
  
  /**
   * Implementation of the TaskingIntf interface used to receive event 
   * asynchronously.
   * 
   * It checks for available resources to execute this request.  It launches
   * new resources based on the following:
   *  
   * If the request has Session Id
   *    - Moves one Resource from the free pool and assign it to this session
   *    - Matches the minimum number of free VMs to be running at any given
   *      time.
   * 
   * If the request does not have a Session Id:
   *    - Assigns the request to the public-session and re-post it
   *    - If there is at least one public VM running, then it assigns the task
   *      to that VM
   *    - If not then it takes one of the free VM just as described above
   * 
   * @param request the CcdpThreadRequest to execute
   * 
   */
  public void onTask( CcdpThreadRequest request )
  {
    if( request == null )
    {
      this.logger.error("The request cannot be null!!");
      return;
    }
    
    // do we need to assign a new resource to this session?
    this.logger.info("Got a new Request: " + request.toString() );
    String sid = request.getSessionId();
    this.checkResourcesAvailability(sid);
    
    // adding the request
    this.requests.add( request );
  }
  
  /**
   * Checks the available resources for this session and assigns a new resource
   * if required.  If the session ID is null then it uses the PUBLIC_SESSION_ID
   * 
   * @param sid The session id to determine resources requirements
   */
  protected void checkResourcesAvailability( String sid )
  {
    if(sid == null )
    {
      sid = CcdpUtils.PUBLIC_SESSION_ID;
      this.logger.warn("No Session ID found, using public one " + sid);
    }
    
    // Get the resources from the current sessions
    List<CcdpVMResource> resources = this.sessions.get(sid);
    if( this.tasker.needResourceAllocation(resources) )
    {
      this.logger.info("Need to assing more resources to session: " + sid);
      CcdpVMResource resource = this.getVMResource();
      if( resources == null )
      {
        this.logger.info("No resources available for SID " + sid);
        List<CcdpVMResource> list = 
            Collections.synchronizedList(new ArrayList<CcdpVMResource>());
        list.add(resource);
        this.sessions.put(sid, list);
      }
      else
      {
        this.logger.info("Adding a new VM to current list for session " + sid);
        this.sessions.get(sid).add(resource);
      }
    }
  }
  
  /**
   * Checks the available pool of resources.  If there are any available, then
   * it is removed from the pool and the CcdpVMResource object associated to it
   * is returned.  If there is no available resources from the pool a new one is
   * started and the newly created resource information is returned.
   * 
   * Once the assignment is completed, it checks the number of resources in the
   * free pool and makes sure it meets the minimum requirement of resources
   * available at any given time.
   * 
   * @return a resource object associated with the virtual machine
   */
  private CcdpVMResource getVMResource()
  {
    CcdpVMResource resource = null;
    
    int free_sz = this.free_vms.size();
    
    if( free_sz > 0 )
    {
      this.logger.info("Getting VM from available resources");
      resource = this.free_vms.remove(0);
    }
    else
    {
      this.logger.info("Starting new VM, no available resources");
      List<String> vms = this.controller.startInstances(1, 1);
      resource = new CcdpVMResource(vms.get(0));
      resource.setStatus(ResourceStatus.INITIALIZING);
    }
    
    // now let's make sure we have enough free VMs
    int need = CcdpUtils.getIntegerProperty(CcdpUtils.CFG_KEY_INITIAL_VMS);
    int have = this.free_vms.size();
    this.logger.info("Need " + need + " VMS, have " + have + " available");
    
    if( need > have )
    {
      int diff = need - have;
      this.logger.info("Launching " + diff + " new VMS");
      List<String> vms = this.controller.startInstances(diff, diff);
      // Add the newly created VMs to the list of free available VMs
      for( String id : vms )
      {
        this.logger.info("Adding VM " + id + " to Free VMs pool");
        CcdpVMResource res = new CcdpVMResource(id);
        res.setStatus(ResourceStatus.INITIALIZING);
        this.free_vms.add(res);
      }
    }
    
    return resource;
  }
  
  
  /**
   * Invoked when the scheduler becomes "disconnected" from the master (e.g., 
   * the master fails and another is taking over). 
   * 
   * @param driver - The driver that was used to run this scheduler.
   */
   @Override
   public void disconnected(SchedulerDriver driver)
   {
     this.logger.info("disconnected: " + driver.toString() );
   }

   /**
    * Invoked when there is an unrecoverable error in the scheduler or driver. 
    * The driver will be aborted BEFORE invoking this callback.  
    * 
    * @param driver - The driver that was used to run this scheduler.
    * @param error - The error message.
    */
   @Override
   public void error(SchedulerDriver driver, String error)
   {
     this.logger.error("error: " + error + " Driver: " + driver.toString() );
   }

   /**
    * Invoked when an executor has exited/terminated. Note that any tasks running 
    * will have TASK_LOST status updates automagically generated.   
    * 
    * NOTE: This callback is not reliably delivered. If a host or network failure 
    * causes messages between the master and the scheduler to be dropped, this 
    * callback may not be invoked.
    * 
    * @param driver - The driver that was used to run this scheduler.
    * @param executorId - The ID of the executor that was lost.
    * @param slaveId - The ID of the slave that launched the executor.
    * @param status - The exit status of the executor.
    * 
    */
   @Override
   public void executorLost(SchedulerDriver driver, ExecutorID execId, SlaveID slvId,
       int arg3)
   {
     this.logger.error("executorLost: " + " Driver: " + driver.toString() + 
                 " ExecId " + execId + " SlaveId: " + slvId + " Int? " + arg3) ;
   }

   /**
    * Invoked when an executor sends a message. These messages are best effort; 
    * do not expect a framework message to be retransmitted in any reliable 
    * fashion.
    * 
    * @param driver - The driver that received the message.
    * @param executorId - The ID of the executor that sent the message.
    * @param slaveId - The ID of the slave that launched the executor.
    * @param data - The message payload.
    * 
    */
   @Override
   public void frameworkMessage(SchedulerDriver driver, ExecutorID execId,
       SlaveID slvId, byte[] msg)
   {
     this.logger.info("frameworkMessage: " + new String(msg) + " Driver: " + 
             driver.toString() + " ExecId " + execId + " SlaveId: " + slvId);
   }

   /**
    * Invoked when an offer is no longer valid (e.g., the slave was lost or 
    * another framework used resources in the offer). If for whatever reason an 
    * offer is never rescinded (e.g., dropped message, failing over framework, 
    * etc.), a framework that attempts to launch tasks using an invalid offer 
    * will receive TASK_LOST status updates for those tasks (see 
    * resourceOffers(SchedulerDriver, List<Offer>)).
    * 
    * @param driver - The driver that was used to run this scheduler.
    * @param offerId - The ID of the offer that was rescinded.
    * 
    */
   @Override
   public void offerRescinded(SchedulerDriver driver, OfferID offerId)
   {
     this.logger.info("offerRescinded " + driver.toString() + 
                      " OfferId " + offerId.toString() );
   }
   
   /**
    * Invoked when a slave has been determined unreachable (e.g., machine 
    * failure, network partition). Most frameworks will need to reschedule any 
    * tasks launched on this slave on a new slave. NOTE: This callback is not 
    * reliably delivered. If a host or network failure causes messages between 
    * the master and the scheduler to be dropped, this callback may not be 
    * invoked.
    * 
    * @param driver - The driver that was used to run this scheduler.
    * @param slaveId - The ID of the slave that was lost.
    * 
    */
   @Override
   public void slaveLost(SchedulerDriver driver, SlaveID slvId)
   {
     this.logger.info("slaveLost " + driver.toString() + 
                      " SlaveId: " + slvId.toString() );
   }
   
   /**
    * Invoked when the status of a task has changed (e.g., a slave is lost and 
    * so the task is lost, a task finishes and an executor sends a status update 
    * saying so, etc). If implicit acknowledgements are being used, then 
    * returning from this callback _acknowledges_ receipt of this status update! 
    * If for whatever reason the scheduler aborts during this callback (or the 
    * process exits) another status update will be delivered (note, however, 
    * that this is currently not true if the slave sending the status update is 
    * lost/fails during that time). If explicit acknowledgements are in use, the 
    * scheduler must acknowledge this status on the driver.
    * 
    * @param driver - The driver that was used to run this scheduler.
    * @param status - The status update, which includes the task ID and status.
    * 
    */
   public void statusUpdate(SchedulerDriver driver, TaskStatus status)
   {
     String tid = status.getTaskId().getValue();
     this.logger.info("statusUpdate TaskStatus: " + tid );
     this.logger.info("Status: " + status.getState());
     
     synchronized( this.requests )
     {
       Reason reason = status.getReason();
       
       if( reason.equals( Reason.REASON_RECONCILIATION) )
         this.logger.warn("Got a reconciliation, want to do something?");
       // we'll see if we can find a job this corresponds to
       
       List<CcdpThreadRequest> doneThreads = new ArrayList<>();
       for( CcdpThreadRequest req : this.requests)
       {
         List<CcdpTaskRequest> toRemove = new ArrayList<>();
         for( CcdpTaskRequest task : req.getTasks() )
         {
           String jid = task.getTaskId();
           this.logger.debug("Comparing Task: " + tid + " against " + jid);
           if( jid.equals( tid ) )
           {
             this.logger.debug("Found Task I was looking for");
             boolean changed = false;
             switch ( status.getState() )
             {
               case TASK_RUNNING:
                 task.started();
                 changed = true;
                 break;
               case TASK_FINISHED:
                 task.succeed();
                 toRemove.add(task);
                 changed = true;
                 this.logger.debug("Job (" + jid + ") Finished");
                 break;
               case TASK_FAILED:
               case TASK_KILLED:
               case TASK_LOST:
               case TASK_ERROR:
                 task.fail();
                 toRemove.add(task);
                 changed = true;
                 break;
               default:
                 break;
             }// end of switch statement
             
             // if there is a change in the status, send a message back
             if( changed )
             {
               this.logger.debug("Status changed to " + task.getState());
               String channel = task.getReplyTo();
               if( channel == null )
                 channel = req.getReplyTo();
               
               task.setReplyTo(channel);
               // notify the child of changes on a task
               this.handleStatusUpdate(task);
             }
             
           }// found the job
         }// for task loop
         req.removeAllTasks( toRemove );
         if( req.isDone() )
           doneThreads.add(req);
         
       }// end of the thread request loop
       
       // now need to delete all the threads that are done
       this.logger.info("Removing " + doneThreads.size() + " done Threads");
       this.requests.removeAll(doneThreads);
     }// end of synch block
   }
   
   /**
    * Allows the child class to take responsibility and allocate the offers as
    * required without having to worry about the actual Mesos implementation
    * 
    * @param offers a list of resources available to use to assign tasks to them
    */
   public abstract void handleResourceOffering(List<Offer> offers);
   
   /**
    * Updates a single task status.  It is intended to give child classes a way
    * to be notified of changes in the tasking and therefore being able to send
    * a notification to the client about such changes.
    * 
    * @param task the task whose status changed
    */
   public abstract void handleStatusUpdate( CcdpTaskRequest task );
   
}
