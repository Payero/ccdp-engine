Development Setup and Tips & Tricks
===================================

Scott Bennett, scott.bennett@caci.com

The purpose of this document is to make starting new on CCDP less of a pain-staking process, allowing a new developer to be up and running quickly with a good understanding of the program architecture.

The first section will take you through the set up process, preparing your system to work and develop in CCDP

The second section will be a high level walk through of the program architecture, introducing components, their purpose, and how they interface with the rest of the system.

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

Now that dependencies and applications are install, it's time to configure the environment.

- I recommend always launching Eclipse from terminal. This allows Eclipse to use your environment variables set in your *.bashrc* file to be active inside Eclipse. To ensure this also happens, add the line
*source /etc/environment*
to your *.bashrc* file. This forces the variables to be sourced.

- The following is a list of aliases located in my *.bash_aliases* file, that gets run at a point in my *.bashrc* file. This sets a bunch of keyboard shortcuts and environment variabes:

```bash
export JAVA_HOME="/usr/java/latest"
export ANT_HOME="/projects/users/srbenne/apps/apache-ant"

export PATH="${JAVA_HOME}/bin:${ANT_HOME}/bin:$PATH"
export CCDP_HOME=/nishome/srbenne/workspace/engine

# I installed eclipse at /opt
alias ejava='{Path to Eclipse}/eclipse/eclipse & disown'
```

The above code block can be copy and pasted into your *.bashrc* file. Next, a workspace should be designated for Eclipse, so use the following commands to make a new directory for the workspace and finally clone the repo:

```bash
# Go to home directory
cd ~

# Make a new directory for the workspace
mkdir workspace
cd workspace

# Open Eclipse with the alias from earlier
ejava
```

When Eclipse opens, it asks for your project workspace, at which time you can designate the workspace folder we just created.

The final step to set up is to download the CCDP Client jar file. After you create your own Java project, you will be able to add the *ccdp-client.jar* file to your class path by:
Select 'Project' from the top bar on Eclipse -> Properties -> Java Build Path -> Add External JARs -> Add *ccdp-client.jar* to ClassPath
At this point, you are ready to start using adding your own features to the CCDP tool, allowing easy resource monitor and task allocation for large systems.

An In Depth Look into CCDP
--------------------------

