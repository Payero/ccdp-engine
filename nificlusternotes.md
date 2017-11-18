To Create NiFi Cluster (localhost):
------------------------------------

For X nodes, extract NiFi folder into own directory. I recommend 2 nodes if running everything locally,
otherwise computer will run very slowly or freeze.

Enter "conf" directory and edit "nifi.properties"
  ex:::      cd nifi-1.3.0/conf

Change the following lines in "nifi.properties":

  # web properties #
  nifi.web.http.host=node                 (can leave blank for localhost, or some other name,
                                           I changed my /etc/hosts file to include node as localhost.
                                           For AWS, you need to use the full public DNS name)
  nifi.web.http.port=10000                (whatever port # you want that is > 1024)

  # cluster node properties (only configure for cluster nodes) #
  nifi.cluster.is.node=true               (set to true)
  nifi.cluster.node.address=node          (give it a name)
  nifi.cluster.node.protocol.port=9998    (choose some port #, must be diff from other nodes if run locally)

After changing each node's "nifi.properties" file, go to NiFi's bin directory and run each instance
  ex:::     cd nifi-1.3.0/bin
  ex:::     ./nifi.sh start

Open up each node in web browser and you should see a 2/2 in the upper left corner indicating the number of
nodes in your cluster.
          node:10000/NiFi

-----------
Embedded Zookeeper
-----------

To set up embedded zookeeper on NiFi cluster, for each node you need to add a line in 'zookeeper.properties'.
  ex:::     server.1=node:2888:3888
            server.2=nodetwoDNS:2888:3888

Then, if you look at the 'dataDir' field in 'zookeeper.properties', you need to create a file named 'myid'
in each NiFi node. This is typically under the '/state/zookeeper' folder. In the 'myid' file you want to 
put the id number of the server. This is the line number you listed in 'zookeeper.properties'.

Let's say we are on the nodetwo NiFi instance.
  ex:::    cd nifi-1.3.0
           mkdir ./state
           mkdir ./state/zookeeper
           echo 2 > ./state/zookeeper/myid                  (if we are on the third node, "server.3=xxx" 
                                                             we would put '3')

To set up the NiFi node, edit 'nifi.properties' and change the following lines:
  nifi.state.management.embedded.zookeeper.start=true

  # zookeeper properties, used for cluster management #
  nifi.zookeeper.connect.string=node:2181,nodee:2181     (needs to match first node's string)

  nifi.remote.input.host=node                       (set to the DNS host name)
  nifi.remote.input.socket.port=9998                (set to a different port from the protocol port #, if
                                                     running locally make sure they are all different)

Set up is completed and the cluster should be set up properly if you run each NiFi node (any order).


Some useful links to setup cluster (some steps unnecessary):
-------------------
Setting up NiFi Cluster with embedded Zookeeper:
http://nixtutorials.com/2017/01/11/setup-apache-nifi-cluster/
https://pierrevillard.com/2016/08/13/apache-nifi-1-0-0-cluster-setup/

Secure Cluster:
https://pierrevillard.com/2016/11/30/scaling-updown-a-nifi-cluster/


Future potentially useful links:
--------------------
Load-balance and docker-compose:
http://ijokarumawak.github.io/nifi/2016/11/01/nifi-cluster-lb/

Site-to-site data distribution
https://community.hortonworks.com/articles/16120/how-do-i-distribute-data-across-a-nifi-cluster.html

Haproxy load balance
https://pierrevillard.com/2017/02/10/haproxy-load-balancing-in-front-of-apache-nifi/