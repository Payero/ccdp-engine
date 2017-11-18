Setting up an external Zookeeper with NiFi
--------------------------------------------------

For each Zookeeper host server you need, you want to create a copy of the zookeeper directory.

-------------
To Download:
-------------
Find a mirror from the following site, download, and unzip it:
  http://www.apache.org/dyn/closer.cgi/zookeeper/

    ex::    wget http://ftp.wayne.edu/apache/zookeeper/stable/zookeeper-3.4.10.tar.gz 

-------------
Zookeeper Setup
-------------

Go the the 'conf' directory in your zookeeper folder. Create a new file called 'zoo.cfg'.
    ex::    cd zookeeper-3.4.10/conf
            vi zoo.cfg

Inside the 'zoo.cfg' file, enter the following:

    tickTime=2000
    dataDir=/usr/local/zookeeper
    clientPort=2181
    initLimit=5
    syncLimit=2
    server.1=DNSONE:2888:3888
    server.2=DNSTWO:2889:3889
    server.3=DNSTHREE:2890:3890

For each Zookeeper server you want, add a "server.#=DNSNUM:2888:3888" line with the appropriate
number and DNS name. Use the full public DNS name when using an AWS instance.

      Click on link at end of these notes for more info of the configuration parameters.
          tickTime    --  length of one tick (zookeeper's time unit) in milliseconds 
          dataDir     --  where zookeeper will store database snapshots and log updates
          clientPort  --  the port clients try to connect to
          initLimit   --  number of 'ticks' to let followers connect/sync to leader
          syncLimit   --  number of 'ticks' to let followres sync with zookeeper

If you only want one Zookeeper server, you only need to include the first 3 lines up to 'clientPort'.

For each "server.#" line, create a file called 'myid' in the corresponding server's dataDir (so here
this would be '/usr/local/zookeeper'). Inside the 'myid' file, write the number of the server. For
instance, if we are in "server.3", we would put '3' in the 'myid' file.
    ex::    sudo -i
            cd /usr/local
            sudo mkdir zookeeper
            echo 1 > ./zookeeper/myid

Now we're done with the Zookeeper setup. Go to each Zookeeper server and enter the 'bin' directory.
Run the following command to start up each server.
    ex::    ./zkServer.sh start

-------------
NiFi Setup
-------------
Follow the same instructions as in the other document to set up a NiFi cluster. The main difference
in the instructions is you want the 'nifi.state.management.embedded.zookeeper.start=false' to be set
to false, instead of true since we're using an external zookeeper.

You can SKIP the instructions that tell you to modify the 'zookeeper.properties' file and create a 
'myid' file in the 'state' folder (since these instructions are for embedded zookeeper).

Also, in order for all NiFi nodes to fail over to another Zookeeper server if one goes down, all
nodes needs to have all the servers listed in the 'nifi.zookeeper.connect.string'.
    ex::    nifi.state.management.embedded.zookeeper.start=false
            nifi.zookeeper.connect.string=DNSONE:2181,DNSTWO:2181,DNSTHREE:2181




Useful Links:
--------------------
Configuring external zookeeper all on localhost - Useful to follow along with my instructions:
https://community.hortonworks.com/articles/135820/configuring-an-external-zookeeper-to-work-with-apa.html

Installing Zookeeper:
http://docs.electric-cloud.com/eflow_doc/7_2/Install/HTML/Content/Install%20Guide/horizontal_scalability/9InstallZooKeeper.htm

Zookeeper Parameter/Config meanings:
https://zookeeper.apache.org/doc/r3.1.2/zookeeperAdmin.html#sc_configuration

