Development Setup and Tips & Tricks
===================================

Scott Bennett, scott.bennett@caci.com

The purpose of this document is to make starting new on CCDP less of a pain-staking process, allowing a new developer to be up and running quickly with a good
understanding of the program architecture.

The first section ("System Setup") will take you through the set up process, preparing your system to work and develop in CCDP

The second section ("An In Depth Look into CCDP") will be a high level walk through of the program architecture, introducing components,
their purpose, and how they interface with the rest of the system.

The third section will be a bunch of tips and tricks that I have compiled to hopefully help with debugging and development.

System Setup
------------

Before anything, make sure that all the necessary dependencies are installed:

- Java 12, https://www.oracle.com/technetwork/java/javase/downloads/jdk12-downloads-5295953.html
- Ant 1.10, https://ant.apache.org/bindownload.cgi

After installing the dependencies, programs that are needed for development and debugging are needed. My personal recommendations are:

- Eclipse IDE for Java Developers, https://www.eclipse.org/
- Visual Studio Code, https://code.visualstudio.com/
- Terminator, a better terminal, *yum install terminator*

Now that dependencies and applications are install, it's time to configure the environment. A workspace should be designated for Eclipse, so use the following
commands to make a new directory for the workspace:

```shell
# Go to home directory
ps1:~$: cd ~

# Make a new directory for the workspace
ps1:~: mkdir workspace
```

I recommend always launching Eclipse from terminal. This allows Eclipse to use your environment variables set in your *.bashrc* file to be actived inside Eclipse.

The following is a list of aliases located in my *.bash_aliases* file, that gets run at a point in my *.bashrc* file. This sets a bunch of keyboard
shortcuts and environment variabes:

```bash
export JAVA_HOME="/usr/java/latest" 
# Latest is a soft link to the latest version of JDK, 12 in my case

export ANT_HOME="{Path to Ant Home}/apache-ant"

export PATH="${JAVA_HOME}/bin:${ANT_HOME}/bin:$PATH"
export CCDP_HOME=~/workspace

# I installed eclipse at /opt
alias ejava='{Path to Eclipse}/eclipse/eclipse & disown'
```

The above code block can be copy and pasted into your *.bashrc* file. You can then use the alias set above to open Eclipse.

```shell
ps1:~$ ejava
```

When Eclipse opens, it asks for your project workspace, at which time you can designate the workspace folder we just created.

The final step to set up is to download the CCDP Client jar file. After you create your own Java project, you will be able to add the *ccdp-client.jar* file to
your class path by:

Select 'Project' from the top bar on Eclipse -> Properties -> Java Build Path -> Add External JARs -> Add *ccdp-client.jar* to ClassPath

At this point, you are ready to start using adding your own features to the CCDP tool, allowing easy resource monitor and task allocation for large systems.

An In Depth Look into CCDP
--------------------------

![alt text](./CcdpArchitecture.png "CCDP Engine Architecture")

In order to give reference and have a concrete example to refer back to, I'm going to give the following information:

- When developing the Engine, the types of resources (VMs) that I used for testing were Docker and AWS EC2. For the majority of this section,
I will refere to them as the resources, but the same concepts can be derived for whatever resource you are implementing CCDP Engine support.

For reference, I used the following services to implement interfaces:

- Active MQ for the Connection Interface
- MongoDb for the Database Interface
- AWS EC2 and Docker for the Resources *(Each resource needs a resource controller)*
- AWS Lambda and Local Bash Session for Serverless Interfaces
- Two different task allocation controllers, used to determine how tasks are distributed to the engine's resources

All interfaces are required to be implemented in at least one way or the engine will work unexpectedly. Before defining the connections,
I'm going to define a few terms that I will use to explain system components:

- Resource: An instance of a server-bound or server-free target that can execute tasks assigned by the Engine.
- Agent: A server-bound resource that requires a host, local or remote, to run tasks
- Serverless: A server-free resource that doesn't need a host to execute *(AWS Lambda for example)*

Its important to note that agents are physical machine running tasks, while serverless operations don't require a host to execute code.

### The Configuration File and Supporting Classes

Before I can effectively describe the Interfaces, I will need to introduce the configuration file and the supporting classes that are used
by the interfaces to communicate.

#### The Configuration File

The configuration file is essential to CCDP. It dictates how every interface interacts and lets the engine know how to configure elements in the system.
In this section, I'm going to cover all the necessary fields for the configuration file to get CCDP up and running.

##### Logging

This first section of the configuration file is logging, which gives the locations of the logs directory and the logging configuration file.
For development, I used the Log4j logger, so I set the following in my configuration file.

```json
"logging": {
    "config-file": "${CCDP_HOME}/config/log4j.properties",
    "logs-dir": "${CCDP_HOME}/logs"
}
```

##### Credentials

The next section is a later addition to the configuration file: credentials. This section is used to hold the credentials for different service you wish
to allow CCDP to communicate with. The example I will be using is AWS, which requires a credentials file and credentials profile. I added this to my
configuration file like this:

```json
"credentials": {
    "AWS":{
        "credentials-file": "{My_HOME_PATH}/.aws/credentials",
        "credentials-profile-name": "default"
    }
```

Fun fact: This was added to the configuration after serverless support was implemented into CCDP and I said "Wait, it doesn't make sense to use an EC2
class to pull credentials from for a AWS Lambda controller."

##### Engine

This section of the configuration file is used to configure the basic main application's parameters for operation. The fields that I
used for this section are:

```json
"engine": {
    "resources-check-cycle": 5,
    "do-not-terminate": [
      "i-0fa470f3da73d8ac0",
      "i-0a1679a0cb4aba5dd"
    ],
    "skip-heartbeats": false,
    "heartbeat-req-secs": 5
```

This set the frequency for which the Engine queries that database for an update and sets a list of unique identifiers corresponding to resources
(AWS EC2 instances in this case) that are to not be terminated by the Engine.

##### Interface Implementations

The next section of the file outlines how all of the required interfaces that **aren't** resource specific should be implemented.
This list of interfaces includes: connection, task allocation and database. As stated previously, I used AMQ for my connection
interface, MongoDb for my database interface, and wrote a task allocator that bases task allocation on the number of jobs a
resource has been assigned while still taking specific hardware consumption into consideration (See "Task Allocator"). To add these to my
configuration file, I used the following:

```json
"interface-impls": {
    "connection": {
      "classname": "com.axios.ccdp.impl.connections.amq.AmqCcdpConnectionImpl",
      "broker": "failover://tcp://localhost.com:61616",
      "main-queue": "CCDP-Engine"  
    },
    "task-allocator": {
      "classname": "com.axios.ccdp.impl.controllers.NumberTasksControllerImpl",
      "allocate": {
        "avg-cpu-load": 80,
        "avg-mem-load": 15,
        "avg-time-load": 2,
        "no-more-than":  5
      },
      "deallocate": {
        "avg-cpu-load": 20,
        "avg-mem-load": 20,
        "avg-time-load": 2
      }
    },
    "database": {
      "classname": "com.axios.ccdp.impl.db.mongo.CcdpMongoDbImpl",
      "db-host": "localhost",
      "db-port": 27017,
      "db-name": "CCDP",
      "db-resources-table": "ResourceStatus"
    }
  }
```

It's important to note some of the fields seen under the various sections. My task allocator required CPU and memory load statistics to make
resource allocation and deallocation decisions, so I added fields to the configuration file and wrote appropriate getters to fetch the
information. For the database, I used a MongoDb collection named ResourceStatus inside of the CCDP container. **ALL** information
needed for configuring the resource-agnostic interfaces (Connection, Task Allocator, and Database) is placed in this section.

##### Resource Provisioning

The final section of the configuration file is the resources provisioning section, which outline how the resource-specific interfaces will be
implemented, like the resource controllers and monitors. I'll display how my AWS EC2 controller and AWS Lambda controller was added my the configuration file.

```json
"resource-provisioning": {
    "resources": {
      "EC2": {
        "classname": "com.axios.ccdp.impl.image.loader.EC2ImageLoaderImpl",
        "image-id":"{AWS AMI HERE}",
        "resource-controller": "com.axios.ccdp.impl.cloud.aws.AWSCcdpVMControllerImpl",
        "resource-monitor":"com.axios.ccdp.impl.monitors.LinuxResourceMonitorImpl",
        "monitor-units": "MB",
        "resource-storage": "com.axios.ccdp.impl.cloud.aws.AWSCcdpStorageControllerImpl",
        "min-number-free-agents": 0,
        "security-group": "sg-54410d2f",
        "subnet-id": "subnet-d7008b8f",
        "key-file-name": "aws_serv_server_key",
        "tags": {
            "session-id": "Service-Node", "Name": "SRB-Test-EC2"
          },
        "startup-command": [
            "/data/ccdp/ccdp_install.py",
            "-a",
            "download",
            "-d",
            "s3://ccdp-dist/ccdp-engine.tgz",
            "-w",
            "-t",
            "/data/ccdp/",
            "-n",
            "EC2"
          ],
        "assignment-command": "touch ~/hi.txt",
        "region": "us-east-1",
        "role-name": "EMR_EC2_DefaultRole",
        "proxy-url": "",
        "__proxy-url": "my-url-to-ge/to/the/proxy",
        "proxy-port": -1
      }
    },
   "serverless":{
       "AWS Lambda":{
            "name": "AWS Lambda",
            "serverless-controller":"com.axios.ccdp.impl.cloud.aws.AWSLambdaController",
            "credentials-file": "/nishome/srbenne/.aws/credentials"
        }
    }
}
```

The only required fields for agents are: classname, resource-controller, resource-monitor, resource-units, resource-storage, and min-number-free-agents.

#### The Supporting Classes

CCDP uses a couple different parent classes to standardize components of the system to allow them to be manipulated by the Engine.
In this section, I will describe these classes so they can be used for the remainder of this document.

##### CCDP VM Resource 

A CcdpVMResource is a class used to represent server-bound resources spawned by the Engine. It is a extension of the CcdpResourceAbs class, which represents all resources that
CCDP can monitor and control. Every instance of an agent has its own CcdpVMResource member variable.
Whenever changes are made or tasks get assigned to an agent, the member variable is updated. Think of the CcdpVMResource class as a container
for information to be used in data processing. This class is the actual class that gets "jsonified" and placed into the database.
The members of the CcdpVMResource class include:

```java
// The status of the resource *("Launched", "Running", "Shutting Down", etc)*
String status;

// The hostname of the resource
String hostname;

// Whether the resource is a serverless resource or not *(should be false for agents)*
final boolean isServerless = false;

// A list of task requests assigned to the resource
List<CcdpTaskRequest> tasks;

// A unique identifier applied to the resource
String instanceId;

// The session that the Engine assigned to the resource
String assignedSession;

// The assigned CPU to the resource by the allocator
int assignedCPU;

// The assigned memory (RAM) to the resource by the allocator
int assignedMEM;

// The assigned disk space to the resource by the allocator
int assignedDisk;

// The total CPU available to the resource
int totalCPU;

// The total memory (RAM) available to the resource
int totalMEM;

// The total disk space available to the resource
int totalDisk;

// The current free memory (RAM) available to the resource
int freeMem;

// The current CPU load used by the resource
int cpuLoad;

// The current free disk space available to the resource
int freeDisk;

// Whether the resource is single tasked*
boolean isSingleTasked;

// The task that is causing the resource to be single tasked, null if not single tasked
CcdpTaskRequest singleTask;

// The last time the resource was updated by an Engine probe
int lastUpdated;

// The last time the resource was assigned a tasked by the task allocator
int lastAssignment;

// Additional tags to add to the resource
Map<String, String> tags;
```

*\* Note: a task makes a resource "single tasked" if the task is given all of the agent's allocated CPU processing power*

Each member has Json getters and setters to allow and the class itself has a private method for creating a JsonNode from the members.

##### CCDP Serverless Resource

A CcdpServerlessResource is a class used to represent serverless controllers spawned by the Engine.
It is a extension of the CcdpResourceAbs class, which represents all resources that
CCDP can monitor and control. Every instance of a serverless controller has its own CcdpServerlessResource member variable.
The reason for why serverless controllers are represented rather than the running "resources" is simple: They don't actually exist.
Serverless controllers are simply a means for the consumer to run a task where the execution of code is done without a host.
The example of this in my development environment is AWS Lambda.
There is a single AWS Lambda controller which handles every task assigned to AWS Lambda.
Having every Runner (the element that actually runs the execution request/serverless action) communicate with the Engine and
database would be unnecessarily stressful on the Engine's host machine.
Whenever changes are made or tasks get assigned to a serverless controller, the member variable is updated.
Think of the CcdpServerlessResource class as a container
for information to be used in data processing. This class is the actual class that gets "jsonified" and placed into the database.
The members of the CcdpServerlessResource class include:

```java
// Whether the resource is a serverless resource or not *(should be true for serverless resources)*
final boolean isServerless = true;

// A list of task requests assigned to the resource
List<CcdpTaskRequest> tasks;

// The last time the resource was assigned a tasked by the task allocator
int lastAssignment;

// Additional tags to add to the resource
Map<String, String> tags;
```

Each member has Json getters and setters to allow and the class itself has a private method for creating a JsonNode from the members.

#### CCDP Task Requests

A CcdpTaskRequest is a class used to represent all task request that are sent throughout the system. It is similar in concept to the
previously discussed CcdpVMResource and CcdpServerlessResource classes in the sense that it is a container for hold information used
in CCDP data processing. Task requests are the containers for holding tasks that are sent and assigned to VMs, and they are stored in
a way that resources and the Engine can interpret and comprehend the information. The members of the CcdpTaskRequest class are as follows:

```java
// Generates all the JSON objects for this class
private ObjectMapper mapper = new ObjectMapper();

// All the different states the job can be at any given time
public enum CcdpTaskState { PENDING, ASSIGNED, STAGING, RUNNING, SUCCESSFUL,
                              FAILED, KILLED }

// Stores the unique identifier for this task
private String taskId;

// The human readable name of the Task
private String name;

// An optional description of this task 
private String description;

// Sets the current state of this task, required for controlling
private CcdpTaskState state = CcdpTaskState.PENDING;

// Stores the class name of a module to execute if needed
private String className;

// Indicates the node type where this task needs to run
private String nodeType;

// The destination or entity to notify this task has change state 
private String replyTo = "";

// The unique identifier of the Agent responsible for the task
private String hostId;

//The IP address or hostname of the node where this task is running
private String hostname;

// The number of times this task needs to be executed before set it as failed
private int retries = 0;

// Indicates whether or not this task has been submitted for processing
private boolean submitted = false;

// The amount of CPU this task requires to execute
private double cpu;

// The amount of memory this task requires to execute
private double mem = 0.0;

// A list of arguments used to generate the command to be executed by the agent
private List<String> command;

// A map of configuration to be used by the agent
private Map<String, String> configuration;

// A list of incoming data from the previous task
private List<CcdpPort> inputPorts;

// A list of ports to forward the data one is processed so they can be 
// executed by the next task
private List<CcdpPort> outputPorts;

// The session this task belongs to
private String sessionId;

//A boolean to represent the serverless mode
private boolean isServerless;

// A map for the serverless configuration
private Map<String, String> serverlessCfg;

// A list for the serverless Args
private List<String> serverlessArgs;

// The time this task was launched
private long launchedTime = 0;
```

#### CCDP Thread Request

A CcdpThreadRequest is another container class who's main purpose is to serve as a way to store a list of associated tasks that will be
ran sequentially. It helps with coordinating the launching of tasks and tracking execution. Thread requests are conceptualized as execution threads
in the CCDP framework. A simple requirement placed on Thread requests is that **ALL** tasks that are to be apart of the same thread **MUST** be of
the same session. If tasks with different session IDs are detected, then the first task dictates the thread request session, and every following
task **MUST** have the **SAME** session. Note that only matching **SESSIONS**, not matching **RESOURCE TYPES**, are required. The CcdpThreadRequest
class has the following members:

```java
// Determines whether to run all the tasks in this thread in parallel or in
// sequence mode.
public enum TasksRunningMode { PARALLEL, SEQUENCE, SEQUENTIAL }

// Generates all the JSON objects for this thread
private ObjectMapper mapper = new ObjectMapper();

// Unique identifier for this thread
private String threadId;

// The session this task belongs to
private String sessionId = null;

// A human readable name to identify this thread
private String name;

// A description to store any help users want to provide
private String description;

// A way to communicate back to the sender events about tasking status change
private String replyTo = "";

// A list of tasks to run in order to execute this processing job
private List<CcdpTaskRequest> tasks;

// Indicates how to run all the tasks, one at the time or all of them
// Defaults to Parallel
private TasksRunningMode runningMode = TasksRunningMode.PARALLEL;

// Indicates whether or not all the tasks have been submitted for this thread
// Defaults to false
private boolean tasksSubmitted = false;

// Determines whether or not all the tasks need to run on a single processing node
private boolean runSingleNode = false;
```

#### CCDP Messages and Message Types

In order to integrate all the components of CCDP together, a communication connection in the form of the Connection Interface is used. But what
gets sent over this communication link? The answer is CcdpMessages. A CcdpMessage is a cloneable abstract class that is the most generic form of
message. All specific message types extend this class and all message types have their own designated response to being received on either the
resource side of the engine side. The specific message types are:

- Assign Session: Used when changing the session of a resource
- End Session: Used when a session is to be removed
- Error: Used when an error in execution is encountered
- Kill Task: Used to kill a task running on a resource
- Pause Thread: Used to pause an execution thread within CCDP
- Run Task: Used to initiate a new task on a resource
- Shutdown: Used when the resource is to be shutdown
- Start Session: Used when a new session is to be started
- Start Thread: Used when a new thread is to be started
- Stop Thread: Used when a threads execution is to be stopped
- Task Update: Used to send task updates between a resource and the Engine
- Thread Request: Used when a new thread request is created and needs to be processed by the Engine
- Undefined: Used when the content of a message doesn't fit into one of the previously stated categories

Classes that need to send and/or receive messages should implement the CcdpMessageConsumerIntf interface and add a private CcdpConnectionIntf member.
The CcdpConnectionIntf member is an interface for configuring consumers and senders. This is explained in more depth in the next section.
The CcdpMessageConsumerIntf interface will force a

```java
public void onCcdpMessage ( CcdpMessage message);
```

to be implemented, which will outline what the resource will do when a CcdpMessage is consumed. I recommend getting the type from the CcdpMessage and then
using a switch case on the message type to dictate what happens for the types of messages you care about. All other received messages can run through a
default case that can be used as a log.

### The Connection Interface

The purpose of the connection interface is to provide a method for the main controller, or engine, to communicate with the agents and serverless controllers.
This allows the engine to send tasks and receive updates from the agents and serverless controllers.

The architecture for message sending, from the perspective of the Engine is simple:

- incoming messages are a 'one to many' relationship
- outgoing messages are a 'one to one' relationship.

In other words, there is a single message queue that the Engine listens to for updates and messages from all agents and serverless controllers.
When a new message is received, it is dealt with according to the message type.
When the Engine wants to send a message, it sends the message to a message queue exclusive to the Engine and the desired target.

**PICTURE HERE**

#### Implementation Example:

In my development, I used Active MQ. The Engine's queue to consume from was named 'ccdp-engine' and all agents and serverless controllers
were configured to send their messages there.
When a resource or serverless controller allocated a new resource, a unique identifier was given to it, the Engine was configured to
produce message to the channel, and the newly created resource would be configured, on creation, to consume on that channel.

### The Database Interface

The purpose of the Database interface is to provide a persistent method of system status monitoring in case of unexpected crashes.
It is also a powerful tool for debugging the system when resource tasking isn't behaving as expected.
Every resource that is created by the Engine has an entry in the database as long as the resource is still active.
If a resource is terminated by the Engine, its database entry is deleted.
The Database interface is designed to hold CcdpResourceAbs objects, the abstract super class to all types of resources that can be spawned by CCDP.

#### Implementation Example:

As stated earlier, I used MongoDb to implement the Database interface with my database entries looking like the following:
![alt-text](./DatabaseSS.png)

In this example, I have a single serverless resource (the AWS Lambda controller) and a single server-bound resource (the AWS EC2 instance).
Before running the Engine, my database was empty. Upon starting the Engine, the serverless controller was created and added to the database
(Serverless controllers get logged in the database, but not server-bound controllers. This is discussed in detail later.
See "Serverless" and "Resource controllers)
Later in execution, the Engine spawns an Amazon EC2 instance and upon creation, the agent records its information into the database.
It starts in the "LAUNCHED" state, and when the agent on EC2 is configured and ready to go, it changes this status to "RUNNING".
As either resource is allocated tasks, their respective entries will be updated to contain the tasks assigned.
They will appear as another JSON level and contain information about the task (See "Tasking").

### Resource Controllers

Resource controllers behave just as expected, they **control** the resource.
These resources can be either serverless or server-bound, but I'll mainly focus on server-bound in this section.
There are two important "types" of controllers: The master controller and the controller interface.
On Engine start up, the master controller is created and, using the resources designated in the ccdp-config.json configuration file,
creates precisely **ONE** controller required by the different resource type.
**If multiple resource types use the SAME controller, only ONE controller will be created, which will service both of those resource types.**
After the master controller determines what kind of controllers are needed, a map is created to correlate resource types to controllers.
In order for your controllers to be used, you **MUST** implement the CcdpVMControllerIntf interface.
If you don't implement the interface, the Engine will not know how to interact with the controller, making CCDP ineffective.

When a request for an agent is received, the master controller uses a locally stored map, which maps node types to controllers. Then, the correct controller
decided, comparing the request node type to the map. After determining the controller, the correct resource controller can talk to the program running on the
agent using the Connection interface.

The methods that must be implemented by developed controllers are as follows:

```java
// Takes the configuration for the resource type from the ccdp-config.json file and uses it
// to prepare the controller for use
//
// @param config The configuration for the controller in JsonNode form
public void configure ( JsonNode config );

// Starts one or more instances of the resource using an image generated from the ccdp-config.json file
//
// @param imgCfg The CcdpImageInfo used to spawn instances
// @return A list of the unique identifiers for resources spawned
public List<String> startInstances ( CcdpImageInfo imgCfg );

// Stops each of the instances included in the list of IDs
//
// @param instIDs A list of unique identifiers representing the VMs that are to be stopped
// @return true if all the VMs in the list were stopped, false otherwise
public boolean stopInstances ( List<String> instIDs );

// Terminates each of the instances included in the list of IDs
//
// @param instIDs A list of unique identifiers representing the VMs that are to be terminated
// @return true if all the VMs in the list were terminated, false otherwise
public boolean terminateInstances ( List<String> instIDs );

// Gets all the instances' statuses that are currently available
//
// @return A list of CcdpVMResources, each CcdpVMResources being one instance available
public List<CcdpVMResource> getAllInstanceStatus ();

// Gets the current instance state of the resource with the given ID
//
// @param id The unique identifer for the requested VM
// @return the state of the resource, represented by an enumerator
public ResourceStatus getInstanceState ( String id );

// Returns a list of CcdpVMResources that match the provided filter
//
// @param filter A JsonNode containing the fields to match to resources
// @return A list of resources that match the given filter
public List<CcdpVMResource> getStatusFilteredByTags( JsonNode filter );

// Returns information about the instance matching the unique ID given as the argument
//
// @param uuid The unique identifier referring to the desired resource
// @return the resource whose unique identifier matches the given parameter
public CcdpVMResource getStatusFilteredById ( String uuid );
```

*\*Note: CcdpVMResource and CcdpImageInfo is discussed in the "Configuration File" section*

### Serverless Controllers

Serverless controllers, similarly to resource controllers, behave the way that you would expect. In the same manner as the serverless controllers,
the serverless controllers are allocated by a master controller on Engine start up, allocating exactly **one** controller per service. Where these
serverless controller differentiate from their server-bound counterparts is how they handle requests.

When Serverless controllers receive requests, the master controller maps the node type to the correct serverless controller, just like in server-bound
resource controller. The difference is in the next step: the serverless controller processes the information in the request and prepares it to be executed.
When the request is prepared, a Task Runner is used to actually perform the serverless operation and get the result back. To explain this better, I'll use
an example that I implemented in testing, AWS Lambda. To complete a Lambda request, I use the *curl* command to POST to a webhook with data included in the
POST request. My AWS Lambda function listens to that webhook and when it receives a POST, it will take the request and process it,
allowing me to use the arguments provided by the Engine from the task request to execute code remotely and without allocating a host.

A major difference to take note of between Serverless and server-bound controllers is that Serverless controllers themselves appear in the database and
communicate with the Engine while server-bound controllers just allow reference to the agent itself, which appears in the database and talks to the Engine.

In order for your controllers to be used, you **MUST** extend the CcdpServerlessControllerAbs abstract class.
If you don't extend the class, the Engine will not know how to interact with the controller, making CCDP ineffective. 
The methods that need extended are:

```java
// Uses the information in the task parameter and processes it, sending the processed information 
// to a task runner on its own thread
//
// @param task A CcdpTaskRequest to run
public void runTask ( CcdpTaskRequest task );

// Handles the result of the task by allowing an abstract implementation
//
// @param result The task result in JsonNode format
// @param task The CcdpTaskRequest that ran
// @param controller_name The string representation of the controller that ran the task
public void handleResult ( JsonNode result, CcdpTaskRequest task, String controller_name );
```

The methods that are implemented in the abstract class CcdpServerlessControllerAbs are:

```java
//This method configures the abstract class and prepares it for use.
//
// @param config the JsonNode config to use to configure the class
public void configure ( JsonNode config );

// Updates the task status in CCDP and allows developer to implement handling result
//
// @param task The CcdpTaskRequest that was completed
// @param result The task result in JsonNode format
public void completeTask ( CcdpTaskRequest task, JsonNode result );

// An implementation of saving the result of the serverless task locally
//
// @param result The result of the task
// @param localSaveLocation The location to save the result on disk
// @param cont_name The name of the controller that completed the task
protected void localSave ( JsonNode result, String localSaveLocation, String cont_name );
```

### Task Allocator

The task allocator is essential to agent performance in CCDP. The main purpose of the ask allocator is to determine which agents get
assigned tasks, when agents and get spawned and de-spawned, and much more. Task allocators are implementations of the CcdpTaskingControllerIntf
interface. Using the abstract class allows for multiple Task Allocators to be interchanged seamlessly. For my implementation,
I extended the abstract class and wrote a task allocator which bases the allocation of tasks on the number of tasks agents currently have.
I would never allow agents go above a number of tasks dictated in my configuration file.
I also used system statistics to determine if the current tasks were too much for a single VM, allowing to allocate more resources
for tasks as VM resources were consumed.

The methods that need to be implemented for the CcdpTaskingControllerIntf interface are:

```java
// Sets all the parameters required for this object to determine resource
// allocation and deallocation.
//
// @param config the object containing all the configuration parameters
public  void configure ( JsonNode config );

// Assigns all the tasks in the given list to the target VM based on
// resources availability and other conditions
//
// @param tasks a list of tasks to consider running in the intended VM
// @param target the VM candidate to run the tasks
// @param considering all available resources that could be potentially
//        used to run this tasks
//
// @return a list of tasks that could be assigned to the target resource
//
public  List<CcdpTaskRequest> assignTasks ( List<CcdpTaskRequest> tasks,
                                            CcdpVMResource target,
                                            List<CcdpVMResource> considering );

// Determines whether or not additional resources are needed based on
// the utilization level of the given resources.  If the resources combined
// reaches the threshold then it returns true otherwise it returns false.
//
// @param resources the list of resources to test for need of additional
//        resources
//
// @return true if more resources need to be allocated or false otherwise
public  boolean needResourceAllocation ( List<CcdpVMResource> resources );

// Determines whether or not VM resources need to be terminated due to
// low utilization or need.  If one or more of the current RUNNING resources
// falls below the threshold then is added to the list.
//
// @param resources the list of resources to test for need to deallocate
//
// @return a list of resources that need to be terminated
public  List<CcdpVMResource> deallocateResource ( List<CcdpVMResource> resources );
```

### Resource Monitor

The final interface to be discussed is the SystemResourceMonitorAbs interface. This is a per-resource interface used to obtain
VM resource usage statistics. The Engine does not use this interface, but rather it is implemented in each agent. The usage
information provided by the monitor can be used for many different purposes, including debugging and task allocation (as previously
mentioned). Resource monitors must extend the SystemResourceMonitorAbs abstract class or CCDP won't be able to report usage.

In my production implementation, I created a Linux resource monitor for my AWS EC2 instances. This is because the agent was installed on
an EC2 image whose base was Ubuntu Linux. Essentially, my EC2 agents were Ubuntu machines that downloaded and ran my CCDP Agent program.

The methods that need to be implemented for the SystemResourceMonitorAbs are:

```java
// Configures the running environment and/or connections required to perform
// the operations.  The JSON Object contains all the different fields
// necessary to operate.  These fields might change on each actual
// implementation
//
// @param config a JSON Object containing all the necessary fields required
//        to operate
public abstract void configure( JsonNode config );
  
// Gets all the different file system storage names such as ext3, ext4, NTFS,
// etc.
//
// @return all the different file system storage names such as ext3, ext4,
//         NTFS, etc
protected abstract List<String> getFileSystemTypes();
```

In addition, there a number of getters and setters for the following fields pertaining to resource monitors:

```java
// Retrieves all the information possible for this node
private SystemInfo system_info = new SystemInfo();

// Handles all the OS information queries to the node
private OperatingSystem os = null;
  
// Handles all the hardware information queries to the node
private HardwareAbstractionLayer hardware = null;
  
// Handles all CPU related information
private CentralProcessor processor = null;
  
// Handles all the memory related information
private GlobalMemory memory = null;

// Stores the default units base to use
protected long units = 1L;
  
// Stores previous ticks to calculate CPU load
private long[] prevTicks;

// Stores previous ticks to calculate CPU load for each one of the cores
private long[][] prevProcTicks;
  
// Stores all the different file system types to include
private List<String> fileSystemTypes = null;
```

By default in the abstract SystemResourceMonitorAbs class, these set using the operating system that the agent is being run on. For example, getting the
system memory is done in the following code.

```java
this.hardware = this.system_info.getHardware();
this.memory = this.hardware.getMemory();
```

From there, the *memory* class member can be used to get the physical and virtual memory statistics of the machine that the agent is run on. A similar
process can be followed to do this for all system components.
