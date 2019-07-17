# Cloud Computing Data Processing (CCDP)

## Processing Engine

### Overview
Cloud Computing Data Processing (CCDP) is an IRAD whose intention is to create a 
framework to easily interact with the most common services offered by the cloud 
provider for data processing.  The framework takes the burden away from 
knowledge about the cloud provider from the processing modules.  It aims to 
facilitate an environment that is dynamically modified be either allocating or 
deallocating resources as needed.  This dynamic environment will allow 
maximizing the resources usage without compromising processing threads.  A 
processing thread is a sequence of processing modules used to generate some 
results.  The framework provides a way to allow communications between the 
modules

   
### Minimum Requirements:

- Java SDK <s>8</s> 12
- Ant 1.7

### Installing

- Decompress the file containing all the source code
- cd to the root directory
- Type ant and hit enter


The engine has two main components: the main application and the agent.  The 
main application is the one resposible for the tasking and processing 
coordination.  There should be only one instance of this process per system.  
The agent is responsible for receiving tasking and executing them.  The number
of agents is not limited.

### Running the Main Application
- Set the environment variable CCDP_HOME to the appropriate path
- run ${CCDP_HOME}/bin/ccdp_app.sh


### Running the Agent
- Set the environment variable CCDP_HOME to the appropriate path
- run ${CCDP_HOME}/bin/ccdp_agent.sh



### To Do:
- Add running on a single VM to the Thread Request
- Need to add different images based on node types!!
- How to handle lambda tasks
- Add launching jar files using lambda
- <s>Integrate NiFi into it</s>
- If a task fails, it retries properly and eventually fails, but it never gets removed
- Use the Abstract Factory approach to allocating the resource manager, monitor and storage



