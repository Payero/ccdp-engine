/**
 * 
 */
package com.axios.ccdp.mesos.fmwk;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;

import org.apache.mesos.Protos.Attribute;
import org.apache.mesos.Protos.CommandInfo;
import org.apache.mesos.Protos.ExecutorInfo;
import org.apache.mesos.Protos.Offer;
import org.apache.mesos.Protos.OfferID;
import org.apache.mesos.Protos.Resource;
import org.apache.mesos.Protos.SlaveID;
import org.apache.mesos.Protos.Status;
import org.apache.mesos.Protos.TaskID;
import org.apache.mesos.Protos.TaskInfo;
import org.apache.mesos.Protos.TaskState;
import org.apache.mesos.Protos.TaskStatus;
import org.apache.mesos.Protos.Value;
import org.apache.mesos.Protos.TaskStatus.Reason;
import org.apache.mesos.Protos;
import org.apache.mesos.SchedulerDriver;

import com.axios.ccdp.mesos.fmwk.CcdpJob.JobState;
import com.axios.ccdp.mesos.resources.CcdpVMResource;
import com.axios.ccdp.mesos.tasking.CcdpTaskRequest;
import com.axios.ccdp.mesos.tasking.CcdpThreadRequest;
import com.axios.ccdp.mesos.utils.CcdpUtils;
import com.axios.ccdp.mesos.utils.ThreadController;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.protobuf.ByteString;

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
public class CcdpRemoteScheduler extends CcdpMesosScheduler
{
  /**
   * Stores all the VMS or resources that have not been allocated to a 
   * particular session
   */
  private ConcurrentLinkedQueue<CcdpVMResource> free_vms; 
  /**
   * Stores all the VMs allocated to different sessions
   */
  private Map<String, ConcurrentLinkedQueue<CcdpVMResource>> sessions = null;
  
  /**
   * Instantiates a new executors and starts the jobs assigned as the jobs
   * argument.  If the jobs is null then it ignores them
   * 
   * @param execInfo the name of the executor to use to execute the tasks
   * @param jobs an optional list of jobs
   */
  public CcdpRemoteScheduler( ExecutorInfo execInfo, List<CcdpThreadRequest> jobs )
  {
    super(execInfo, jobs);
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
    this.logger.info("resourceOffers: Got some resource Offers" );
    
    Iterator<Offer> items = offers.iterator();
    // generates a list of resources using the offers
    while( items.hasNext() )
    {
      Offer offer = items.next();
      this.logger.debug("Offer: " + offer.toString());
      String id = offer.getSlaveId().toString();
      String sid = null;
      for( Attribute attr : offer.getAttributesList() )
      {
        String name = attr.getName();
        if( name.equals(CcdpUtils.KEY_INSTANCE_ID) )
          id = attr.getText().getValue();
        
        if( name.equals(CcdpUtils.KEY_SESSION_ID) )
          sid = attr.getText().getValue();
      }
      
      CcdpVMResource vm = new CcdpVMResource(id);
      vm.setAssignedSession(sid);
      
      // We always need to extract the resource info from the offer
      for( Resource r : offer.getResourcesList() )
      {
        if( r.getName().equals("cpus") )
          vm.setCPU(r.getScalar().getValue());
        else if( r.getName().equals("mem") )
          vm.setMEM(r.getScalar().getValue());
      }// end of resources loop

      
      // if it has not been assigned, then check free
      if( sid != null && !this.updateResource( vm, this.sessions.get(sid) ) )
      {
        // if is not in the free list add it
        if( !this.updateResource( vm, this.free_vms ) )
        {
          this.logger.info("Could not find resource, adding it to unallocated");
          this.free_vms.add(vm);
        }
      }// it does not have a session id
    }// end of the offer loop
    
    this.assignTasks();
  }
  
  /**
   * Updates the item in the list if the instance id matches one of the free
   * resources.
   * 
   * @param vm the resource to compare
   * @param list the list of resources to search for the list
   * 
   * @return true if the item was found and updated false otherwise
   */
  public boolean updateResource( CcdpVMResource vm, 
                                 ConcurrentLinkedQueue<CcdpVMResource> list)
  {
    Iterator<CcdpVMResource> vms = list.iterator();
    // checking every item in the list
    while(vms.hasNext())
    {
      CcdpVMResource resource = vms.next();
      if( resource.equals(vm))
      {
        this.logger.debug("Reosurce found (" + vm.getInstanceId() + ")");
        resource = vm;
        return true;
      }
    }
    
    return false;
  }

  /***************************************************************************/
  /***************************************************************************/
  /***************************************************************************/
  
  public void assignTasks( )
  {
    
    Iterator<CcdpThreadRequest> threads = this.requests.iterator();
    
    while(threads.hasNext())
    {
      CcdpThreadRequest req = threads.next();
      String id = req.getThreadId();
      String sid = req.getSessionId();
      
      if( sid == null )
      {
        this.logger.error("The Thread " + id + " does not have Session ID");
        continue;
      }
      
      ConcurrentLinkedQueue<CcdpVMResource> resources = this.sessions.get(sid); 
      if( resources != null  )
      {
        if( req.threadRequestCompleted() )
        {
          this.logger.info("Thread " + id + " Done, removing it");
          this.sessions.remove(sid);
        }
        else
        {
          this.logger.info("Found A session");
          CcdpTaskRequest task = req.getNextTask();
          this.launchTask( task, resources);
        }
      }
    }
      
      
      
      
//    synchronized( this.jobs )
//    {
//      List<CcdpJob> pendingJobs = new ArrayList<>();
//      for( CcdpJob j : this.jobs )
//      {
//        this.logger.debug("Adding Job: " + j.getId());
//        if( !j.isSubmitted() )
//          pendingJobs.add(j);
//      }
//      
//      for( Offer offer : offers )
//      {
//        if( pendingJobs.isEmpty() )
//        {
//          this.logger.info("No Pending Jobs, declining offer");
//          driver.declineOffer( offer.getId() );
//          break;
//        }
//        List<OfferID> list = Collections.singletonList( offer.getId() );
//        List<TaskInfo> tasks = this.doFirstFit( offer, pendingJobs );
//        Status stat = driver.launchTasks(list, tasks );
//        this.logger.debug("Task Launched with status of: " + stat.toString() );
//      }
//    }// end of synch block
  }
  
  
  public void launchTask( CcdpTaskRequest task, 
                          ConcurrentLinkedQueue<CcdpVMResource> resources )
  {
    double cpu = task.getCPU();
    
    if( cpu == 0 )
    {
      this.logger.info("CPU = " + cpu + " Assigning Task based on session");
      Iterator<CcdpVMResource> list = resources.iterator();
      
    }
    else if( cpu >= 100 )
    {
      this.logger.info("CPU = " + cpu + " Assigning a Resource just for this task");
    }
    else
    {
      this.logger.info("CPU = " + cpu + " Assigning Task using First Fit");
    }
  }
  
  
  /**
   * Generates a TaskInfo object that will be executed by the appropriate Mesos 
   * Agent.
   * 
   * @param targetSlave the Unique identifier of the Mesos Agent responsible for
   *        running this task
   * @param exec The mesos executor to use to execute the task
   * 
   * @return a TaskInfo object containing all the information required to run
   *         this job
   */
  public TaskInfo makeTask(CcdpTaskRequest task, SlaveID targetSlave, ExecutorInfo exec)
  {
    this.logger.debug("Making Task at Slave " + targetSlave.getValue());
    TaskID id = TaskID.newBuilder().setValue(task.getTaskId()).build();
    
    Protos.TaskInfo.Builder bldr = TaskInfo.newBuilder();
    bldr.setName("task " + id.getValue());
    bldr.setTaskId(id);
    // Adding the CPU
    Protos.Resource.Builder resBldr = Resource.newBuilder();
    resBldr.setName("cpus");
    resBldr.setType(Value.Type.SCALAR);
    resBldr.setScalar(Value.Scalar.newBuilder().setValue(task.getCPU()));
    Resource cpuRes = resBldr.build();
    bldr.addResources(cpuRes);
    this.logger.debug("Adding CPU Resource " + cpuRes.toString());
    
    // Adding the Memory
    resBldr.setName("mem");
    resBldr.setType(Value.Type.SCALAR);
    resBldr.setScalar(Value.Scalar.newBuilder().setValue(task.getMEM()));
    Resource memRes = resBldr.build();
    bldr.addResources(memRes);
    this.logger.debug("Adding MEM Resource " + memRes.toString());
    
    Protos.CommandInfo.Builder cmdBldr = CommandInfo.newBuilder();
    String cmd = String.join(" ", task.getCommand());
    cmdBldr.setValue( cmd );
    this.logger.debug("Running Command: " + cmd);
    
    bldr.setSlaveId(targetSlave);
    
    bldr.setExecutor(exec);
    JsonObject json = new JsonObject();
    json.addProperty("cmd", cmd);
    
    // if there is a configuration, add it
    if( task.getConfiguration() != null )
    {
      Gson gson = new Gson();
      JsonObject config = 
          gson.toJsonTree(task.getConfiguration()).getAsJsonObject();
      json.add("cfg", config);
    }
    
    bldr.setData(ByteString.copyFrom(json.toString().getBytes()));
    return bldr.build();
  }
  
  /***************************************************************************/
  /***************************************************************************/
  /***************************************************************************/
  
  
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
  @Override
  public void statusUpdate(SchedulerDriver driver, TaskStatus status)
  {
    this.logger.info("statusUpdate TaskStatus: " + status.getTaskId().getValue() );
    this.logger.info("Status: " + status.getState());
    synchronized( this.jobs )
    {
      Reason reason = status.getReason();
      
      if( reason.equals( Reason.REASON_RECONCILIATION) )
        this.logger.warn("Got a reconciliation, want to do something?");
      // we'll see if we can find a job this corresponds to
      for( CcdpJob job : this.jobs )
      {
        String jid = job.getId();
        String tid = status.getTaskId().getValue();
        this.logger.debug("Comparing Task: " + tid + " against " + jid);
        if( job.getId().equals( status.getTaskId().getValue() ) )
        {
          switch ( status.getState() )
          {
            case TASK_RUNNING:
              job.started();
              break;
            case TASK_FINISHED:
              job.succeed();
              this.logger.debug("Job (" + job.getId() + ") Finished");
              break;
            case TASK_FAILED:
            case TASK_KILLED:
            case TASK_LOST:
            case TASK_ERROR:
              job.fail();
              break;
            default:
              break;
          }// end of switch statement
        }// found the job
      }// for loop
    }// end of synch block
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
    List<TaskStatus> runningTasks = new ArrayList<>();
    for( CcdpJob job : this.jobs )
    {
      if( job.getStatus() == JobState.RUNNING )
      {
        TaskID id = TaskID.newBuilder()
            .setValue( job.getId())
            .build();
        SlaveID slaveId = SlaveID.newBuilder()
           .setValue( job.getSlaveId().toString() )
           .build();
        
        this.logger.debug("Reconciling Task: " + job.getId() );
        TaskStatus.Builder bldr = TaskStatus.newBuilder();
        bldr.setSlaveId(slaveId);
        bldr.setTaskId(id);
        bldr.setState(TaskState.TASK_RUNNING);
        runningTasks.add( bldr.build() );
      }
    }
    
    this.driver.reconcileTasks(runningTasks);
  }
  
}
