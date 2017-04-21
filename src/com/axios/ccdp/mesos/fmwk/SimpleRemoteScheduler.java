/**
 * 
 */
package com.axios.ccdp.mesos.fmwk;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.apache.log4j.Logger;
import org.apache.mesos.Protos.ExecutorID;
import org.apache.mesos.Protos.ExecutorInfo;
import org.apache.mesos.Protos.FrameworkID;
import org.apache.mesos.Protos.MasterInfo;
import org.apache.mesos.Protos.Offer;
import org.apache.mesos.Protos.OfferID;
import org.apache.mesos.Protos.Resource;
import org.apache.mesos.Protos.SlaveID;
import org.apache.mesos.Protos.Status;
import org.apache.mesos.Protos.TaskID;
import org.apache.mesos.Protos.TaskInfo;
import org.apache.mesos.Protos.TaskState;
import org.apache.mesos.Protos.TaskStatus;
import org.apache.mesos.Protos.TaskStatus.Reason;
import org.apache.mesos.Scheduler;
import org.apache.mesos.SchedulerDriver;
import org.apache.mesos.Protos.Attribute;

import com.amazonaws.services.elastictranscoder.model.Warning;
import com.axios.ccdp.connections.intfs.CcdpObjectFactoryAbs;
import com.axios.ccdp.connections.intfs.CcdpTaskConsumerIntf;
import com.axios.ccdp.connections.intfs.CcdpTaskingIntf;
import com.axios.ccdp.mesos.fmwk.CcdpJob.JobState;
import com.axios.ccdp.tasking.CcdpTaskRequest;
import com.axios.ccdp.tasking.CcdpThreadRequest;
import com.axios.ccdp.utils.CcdpUtils;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * @author oeg
 *
 */
public class SimpleRemoteScheduler implements Scheduler, CcdpTaskConsumerIntf
{
  /**
   * Stores the Framework Unique ID this Scheduler is running under
   */
  private FrameworkID fmwkId;
  /**
   * Stores the Executor responsible for running the tasks
   */
  private ExecutorInfo executor;
  /**
   * Stores the scheduler driver requesting the command execution
   */
  private SchedulerDriver driver = null;
  /**
   * Generates debug print statements based on the verbosity level.
   */
  private Logger logger = 
      Logger.getLogger(SimpleRemoteScheduler.class.getName());
  /**
   * Stores all the jobs assigned to this Scheduler
   */
  private List<CcdpJob> jobs = new ArrayList<CcdpJob>();
  /**
   * Stores the object responsible for creating all interfaces
   */
  private CcdpObjectFactoryAbs factory = null;
  /**
   * Stores the object responsible for sending and receiving tasking information
   */
  private CcdpTaskingIntf taskingInf = null;
  
  /**
   * Instantiates a new executors and starts the jobs assigned as the jobs
   * argument.  If the jobs is null then it ignores them
   * 
   * @param execInfo the name of the executor to use to execute the tasks
   * @param jobs an optional list of jobs
   */
  public SimpleRemoteScheduler( ExecutorInfo execInfo, List<CcdpJob> jobs)
  {
    this.logger.debug("Creating a new CCDP Remote Scheduler");
    this.executor = execInfo;
    
    if( jobs != null )
    {
      this.jobs = jobs;
      
      for( CcdpJob job : jobs )
      {
        this.logger.debug("Tasked with " + job.toString());
      }
    }
    
    // creating the factory that generates the objects used by the scheduler
    String clazz = CcdpUtils.getProperty("factory.interface.impl");
    if( clazz != null )
    {
      ObjectNode task_msg_node = 
          CcdpUtils.getJsonKeysByFilter(CcdpUtils.CFG_KEY_TASK_MSG);
      
      this.factory = CcdpObjectFactoryAbs.newInstance(clazz);
      this.taskingInf = this.factory.getCcdpTaskingInterface(task_msg_node);
    }
    else
    {
      String txt = "Could not find factory.  Please check configuration." +
                   "The key factory.interface.impl is missing";
      this.logger.error(txt);
    }
  }


  /**
   * Invoked when the scheduler successfully registers with a Mesos master. 
   * A unique ID (generated by the master) used for distinguishing this 
   * framework from others and MasterInfo with the IP and port of the current 
   * master are provided as arguments.
   * 
   * @param driver - The scheduler driver that was registered.
   * @param fmwkId - The framework ID generated by the master.
   * @param master - Info about the current master, including IP and port.
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
   * @param master - The updated information about the elected master.
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
   * SchedulerDriver.launchTasks(Collection&lt;OfferID&gt;, Collection&lt;TaskInfo&gt;, 
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
    
    for( Offer offer : offers )
    {
      StringBuffer buf = new StringBuffer();
      buf.append("\n\tSlave Id ");
      buf.append(offer.getSlaveId().getValue());
      buf.append("\n");
      
      buf.append("\t\tHostname ");
      buf.append(offer.getHostname());
      buf.append("\n");
      
      for( Attribute attr : offer.getAttributesList() )
      {
        buf.append("\t\t");
        buf.append(attr.getName());
        buf.append(" ");
        buf.append(attr.getText().getValue());
        buf.append("\n");
      }
      this.logger.debug(buf.toString());
    }
    
    synchronized( this.jobs )
    {
      List<CcdpJob> pendingJobs = new ArrayList<>();
      for( CcdpJob j : this.jobs )
      {
        this.logger.debug("Checking Job: " + j.getId());
        if( !j.isSubmitted() )
        {
          this.logger.debug("Adding Job: " + j.getId());
          pendingJobs.add(j);
        }
      }
      this.logger.info("Jobs in Queue: " + this.jobs.size());
      this.logger.info("Pending Jobs in Queue: " + pendingJobs.size());
      
      for( Offer offer : offers )
      {
        if( pendingJobs.isEmpty() )
        {
          this.logger.info("No Pending Jobs, declining offer");
          driver.declineOffer( offer.getId() );
          break;
        }
        List<OfferID> list = Collections.singletonList( offer.getId() );
        this.logger.info("Have " + pendingJobs.size() + " pending jobs");
        List<TaskInfo> tasks = this.doFirstFit( offer, pendingJobs );
        Status stat = driver.launchTasks(list, tasks );
        this.logger.debug("Task Launched with driver status of: " + stat.toString() );
      }
    }// end of synch block
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
  @Override
  public void statusUpdate(SchedulerDriver driver, TaskStatus status)
  {
    this.logger.info("statusUpdate TaskStatus: " + status.getTaskId().getValue() );
    this.logger.info("Status: " + status.getState());
    List<CcdpJob> toRemove = new ArrayList<>();
    
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
              toRemove.add(job);
              break;
            case TASK_FAILED:
            case TASK_KILLED:
            case TASK_LOST:
            case TASK_ERROR:
              job.fail();
              if( job.getStatus().equals(JobState.FAILED) )
              {
                this.logger.warn("Job FAILED, removing it");
                toRemove.add(job);
              }
              break;
            default:
              break;
          }// end of switch statement
        }// found the job
      }// for loop
      
      this.logger.info("Removing " + toRemove.size() + " Jobs");
      this.jobs.removeAll(toRemove);
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
  
  /**
   * Notifies the object that a new task request has occurred
   * 
   * @param request the actual request triggering this method
   */
  public void onTask( CcdpThreadRequest request )
  {
    this.logger.info("Got a new Thread Request: " + request.getThreadId() );
    List<CcdpTaskRequest> tasks = request.getTasks();
    for( CcdpTaskRequest task : tasks )
    {
      CcdpJob job = new CcdpJob();
      job.setCpus(task.getCPU());
      job.setMemory(task.getMEM());
      job.setCommand( String.join(" ", task.getCommand() ) );
      this.logger.debug("Adding Job: " + job.toString());
      this.jobs.add(job);
    }
  }
  
  /**
   * Checks the amount of CPU and memory available in the offer and starts 
   * launching jobs that has not been submitted before and requires less 
   * resources than the ones available in the Offer.
   * 
   * @param offer the processing node available to execute the job
   * @param jobs as list of jobs or tasks to run
   * 
   * @return a list of TaskInfo objects that were launched on this offer
   */
  public List<TaskInfo> doFirstFit( Offer offer, List<CcdpJob> jobs )
  {
    this.logger.debug("Running First Fit");
    List<TaskInfo> toLaunch = new ArrayList<>();
    List<CcdpJob> launchedJobs = new ArrayList<>();
    
    double offerCpus = 0;
    double offerMem = 0;
    
    // We always need to extract the resource info from the offer
    for( Resource r : offer.getResourcesList() )
    {
      if( r.getName().equals("cpus") )
      {
        double val = r.getScalar().getValue();
        this.logger.debug("Got " + val + " CPU Offered");
        offerCpus += val;
      }
      else if( r.getName().equals("mem") )
      {
        double val = r.getScalar().getValue();
        this.logger.debug("Got " + val + " MEM Offered");
        offerMem += val;
      }
    }// end of resources loop
    
    
    String str = 
          String.format("Offer CPUs: %f, Memory: %f", offerCpus, offerMem);
    this.logger.debug(str);
    
    // Now we will pack jobs into the offer
    for( CcdpJob job : jobs )
    {
      if( job.isSubmitted() )
      {
        this.logger.debug("Job already submitted, skipping it");
        continue;
      }
      
      double jobCpus = job.getCpus();
      double jobMem = job.getMemory();
      this.logger.debug("Job Cpus: " + jobCpus + " Job Mem: " + jobMem);
      // does the offer has more resources than needed?
      if( jobCpus <= offerCpus && jobMem <= offerMem )
      {
        this.logger.info("Enough resources for a new Job");
        offerCpus -= jobCpus;
        offerMem -= jobMem;
        
        TaskInfo task = job.makeTask( offer.getSlaveId(), this.executor );
        
        toLaunch.add( task );
        job.setSubmitted(true);
        launchedJobs.add( job );
      }
    }
    // launch each task
    for( CcdpJob job : launchedJobs )
    {
      this.logger.info("Launching " + job );
      job.launch();
    }
    // clean the jobs 
    jobs.removeAll(launchedJobs);
    return toLaunch;
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
    * @param execId - The ID of the executor that was lost.
    * @param slvId  - The ID of the slave that launched the executor.
    * @param status - The exit status of the executor.
    * 
    */
   @Override
   public void executorLost(SchedulerDriver driver, ExecutorID execId, SlaveID slvId,
       int status)
   {
     this.logger.error("executorLost: " + " Driver: " + driver.toString() + 
                 " ExecId " + execId + " SlaveId: " + slvId + " Int? " + status) ;
   }

   /**
    * Invoked when an executor sends a message. These messages are best effort; 
    * do not expect a framework message to be retransmitted in any reliable 
    * fashion.
    * 
    * @param driver - The driver that received the message.
    * @param execId - The ID of the executor that sent the message.
    * @param slvId  - The ID of the slave that launched the executor.
    * @param msg    - The message payload.
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
    * resourceOffers(SchedulerDriver, List&lt;Offer&gt;)).
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
    * @param slvId - The ID of the slave that was lost.
    * 
    */
   @Override
   public void slaveLost(SchedulerDriver driver, SlaveID slvId)
   {
     this.logger.info("slaveLost " + driver.toString() + 
                      " SlaveId: " + slvId.toString() );
   }
   
}






