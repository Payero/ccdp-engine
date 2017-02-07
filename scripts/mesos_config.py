#!/usr/bin/env python

import os, sys, json, time, math, socket, uuid
from pprint import pprint, pformat
from string import Template

def run_config(cli_data):
  """
  Attempts to set all the running configuration for Mesos.  This is needed so it can be executed
  after a new Image is deployed.  It alters the network configuration files as well as any other
  Mesos related file.  The IP Address is obtained from the metadata service.

  """
  print "Configuring Mesos Environment"

  if not cli_data.has_key('mesos-type'):
    print "ERROR: The mesos-type (MASTER or SLAVE) is required "
    sys.exit(-1)


  is_aws = True
  try:
    skt = socket.create_connection(("169.254.169.254", 80), 1)
    is_aws = True
  except:
    print "Could not connect to 169.254.169.254:80, not running on AWS"
    is_aws =  False

  if is_aws:
    cmd = "wget -O /tmp/document http://169.254.169.254/latest/dynamic/instance-identity/document"
    os.system(cmd)
    obj = file('/tmp/document', 'r')
    data = obj.read()
    config = json.loads(data)
  else:
    print "Running outside of AWS"
    # generates a uuid and takes only the last 2 parts to generate i-aaaa-bbbbbcccc
    iid =  "i-%s" % "-".join(str(uuid.uuid1()).split('-')[3:])
    ip = str([l for l in ([ip for ip in socket.gethostbyname_ex(socket.gethostname())[2] if not ip.startswith("127.")][:1], [[(s.connect(("8.8.8.8", 53)), s.getsockname()[0], s.close()) for s in [socket.socket(socket.AF_INET, socket.SOCK_DGRAM)]][0][1]]) if l][0][0])

    config = {"instanceId": iid,
              "imageId": "ami-417e6156",
              "privateIp": ip}


  cli_data['instance-id'] = config.get('instanceId')
  cli_data['image-id']    = config.get('imageId')
  cli_data['ip-address']  = config.get('privateIp')

  print "Setting up environment using: %s" % pformat(cli_data)


  # Replaces the hostname and hosts file using the private IP Address
  set_network( cli_data['ip-address'], is_aws )
  cli_data['zk'] = set_zookeeper( cli_data, is_aws )


  print "***************** Removing Mesos latest  *****************"
  cmd = "rm -f /var/lib/mesos/meta/slaves/latest"
  print "Executing: %s" % cmd
  os.system(cmd)


  # need to set the slave first as it stops the master and zookeeper
  set_slave( cli_data, is_aws )

  # If this is to run as a master then, need to changes the ZooKeeper
  # configuration and start services
  if cli_data['mesos-type'] == 'MASTER':
    print "Setting Mesos Master"
    set_master( cli_data, is_aws )

    print "Starting ZooKeeper"
    os.system("stop zookeeper")
    os.system("start zookeeper")

    time.sleep(1)
    print "Starting Mesos Master"
    os.system("stop mesos-master")
    os.system("start mesos-master")

    time.sleep(1)
    print "Starting Marathon"
    os.system("stop marathon")
    os.system("start marathon")

  

  time.sleep(1)
  print "Starting Mesos Slave"
  os.system("stop mesos-slave")
  os.system("start mesos-slave")



def set_network( ip, is_aws ):
  """
  Because it starts from an image, the /etc/hosts file has the original 
  IP Address rather than the one from this VM.  The same applies for
  the /etc/hostname file.  This method makes a copy of the files and replaces
  their contents with the appropriate entries

    ip: The IP Address to use
    is_aws: Flag indicating whether we are running this script from an AWS
            instance or not
    
  """
  if not is_aws:
    print "Running from an external source, skipping network settings"
    return

  print "Setting Network Environment"
  cmd = "mv -f /etc/hostname /etc/hostname_ORIG"
  print "Executing cmd: %s" % cmd
  os.system(cmd)
  cmd = 'echo "%s" >> /etc/hostname' % ip
  os.system(cmd)

  cmd = "mv -f /etc/hosts /etc/hosts_ORIG"
  print "Executing cmd: %s" % cmd
  os.system(cmd)

  txt = """
127.0.0.1  localhost
127.0.0.1  %s

# The following lines are desirable for IPv6 capable hosts
::1 ip6-localhost ip6-loopback
fe00::0 ip6-localnet
ff00::0 ip6-mcastprefix
ff02::1 ip6-allnodes
ff02::2 ip6-allrouters
ff02::3 ip6-allhosts
        """ % ip

  cmd = 'echo "%s" >> /etc/hosts' % txt
  print "Executing: %s" % cmd
  os.system(cmd)
  os.system("hostname %s" % ip )


def set_zookeeper( data, is_aws ):
  """
  Sets the /etc/mesos/zk file properly by making sure there is no reference
  to localhost or 127.0.0.1.  It also adds the host's IP Address if missing

    ip:   The IP address to use
    is_aws: Flag indicating whether we are running this script from an AWS
            instance or not
  """
  print "Settting Up ZooKeeper"
  
  ip = data['ip-address']

  if data.has_key('masters'):
    print "Using multiple masters"
    # First add all masters
    zk = "zk://"
    for master in data['masters']:
      print "Adding Master: %s" % master
      zk = "%s%s:2181," % (zk, master['ip-address'])

    # Checks if is already there or not
    if zk.find(ip) < 0 and data['mesos-type'] == 'MASTER':
      zk = "%s%s:2181/mesos" % (zk, ip)
    else:
      # need to remove the comma prior adding the endpoint
      zk = "%s/mesos" % zk[:-1]

  else:
    print "First or only Master"
    if data['mesos-type'] == 'MASTER':
      zk = "zk://%s:2181/mesos" % ip
    else:
      print "ERROR: Attempting to set a SLAVE before a single MASTER is deployed"
      sys.exit(-1)

  print "Generated zk: %s" % zk
  os.system("rm -f /etc/mesos/zk_ORIG")
  os.system("mv -f /etc/mesos/zk /etc/mesos/zk_ORIG")
  cmd = "echo %s >> /etc/mesos/zk" % zk
  print "Executing: %s" % cmd
  os.system(cmd)

  return zk


def set_master( data, is_aws ):
  """
  Modifies all the files required to add this node as a Master node.

  Files Affected:
    /etc/zookeeper/conf/myid
    /etc/zookeeper/conf/zoo.cfg
    /etc/mesos-master/quorum
    /etc/mesos-master/ip
    /etc/mesos-master/hostname
    /etc/marathon/conf/hostname
    /etc/marathon/conf/master
    /etc/marathon/conf/zk
    /etc/init/mesos-slave.override



    data:   Dictionary containing the information required to set the 
            master node
    is_aws: Flag indicating whether we are running this script from an AWS
            instance or not  
  """
  print "Setting Up a Mesos Master"
  if not data.has_key('server-id'):
    print "ERROR: If setting a Master then a server-id (1 - 255) is required"
    sys.exit(-1)

  print "***************** Modifying myid  *****************"
  cmd = "rm -f /etc/zookeeper/conf/myid"
  print "Executing: %s" % cmd
  os.system(cmd)

  cmd = "echo %s >> /etc/zookeeper/conf/myid" % data['server-id']
  print "Executing: %s" % cmd
  os.system(cmd)

  print "***************** Modifying zoo.cfg  *****************"
  cmd = "rm -f /etc/zookeeper/conf/zoo.cfg"
  print "Executing: %s" % cmd
  os.system(cmd)

  found_template = False
  if os.path.isfile('/home/ubuntu/ZOO_CFG'):
    file_in = open('/home/ubuntu/ZOO_CFG', 'r')
    found_template = True
  else:
    if os.path.isfile('./ZOO_CFG'):
      file_in = open('./ZOO_CFG', 'r')
      found_template = True

  if not found_template:
    print ""
    print "ERROR:  Could not find the ZOO_CFG Template returning"
    print ""
    return

  src = Template( file_in.read() )

  servers = []
  num_masters = 1
  my_port = "2888:3888"
  if data.has_key('masters'):
    print "Adding this master to list"
    num_masters = len(data['masters']) + 1

    for master in data['masters']:
      id = master['id']
      ip = master['ip-address']
      port = master['port']
      server = "server.%s=%s:%s" % (id, ip, port)
      servers.append(server)
      # using same configuration
      my_port = port 

  me = "server.%s=%s:%s" % (data['server-id'], data['ip-address'], my_port)
  print "Adding this server: %s" % me
  servers.append(me)

  d = {"ZOO_SERVERS": "\n".join(servers)}
  zoo_cfg = src.substitute(d)
  out = open('/etc/zookeeper/conf/zoo.cfg', 'w')
  out.write(zoo_cfg)
  out.flush()
  out.close()

  print "***************** Modifying Quorum  *****************"
  cmd = "rm -f /etc/mesos-master/quorum "
  print "Executing: %s" % cmd
  os.system(cmd)

  # The quorum is majority of the total number of servers
  quorum = int(math.ceil(num_masters/2))
  if quorum == 0:
    quorum = 1

  cmd = "echo %s >> /etc/mesos-master/quorum" % quorum
  print "Executing: %s" % cmd
  os.system(cmd)

  print "***************** Configuring IP and Hostname  *****************"
  cmd = "rm -f /etc/mesos-master/ip"
  print "Executing: %s" % cmd
  os.system(cmd)
  
  cmd = "rm -f /etc/mesos-master/hostname"
  print "Executing: %s" % cmd
  os.system(cmd)

  cmd = "echo %s >> /etc/mesos-master/ip" % data['ip-address']
  print "Executing: %s" % cmd
  os.system(cmd)  

  cmd = "echo %s >> /etc/mesos-master/hostname" % data['ip-address']
  print "Executing: %s" % cmd
  os.system(cmd)

  print "***************** Configuring Marathon  *****************"
  path = "/etc/marathon/conf"
  if not os.path.isdir(path):
    cmd = "mkdir -p %s" % path
    print "Executing: %s" % cmd
    os.system(cmd)

  cmd = "cp -f /etc/mesos-master/hostname /etc/marathon/conf"
  print "Executing: %s" % cmd
  os.system(cmd)  

  cmd = "cp -f /etc/mesos/zk /etc/marathon/conf/master"
  print "Executing: %s" % cmd
  os.system(cmd)

  cmd = "rm -f /etc/marathon/conf/zk"
  print "Executing %s" % cmd
  os.system(cmd)

  zk = data['zk']
  n = zk.rfind('/')
  if n > 0:
    marathon = "%s/marathon" % zk[:n]

    cmd = "echo %s >> /etc/marathon/conf/zk" % marathon
    print "Executing: %s" % cmd
    os.system(cmd)
  else:
    print "ERROR: Could not find /mesos in zk"

  print "***************** Modifying slave.override  *****************"
  cmd = "rm -f /etc/init/mesos-slave.override"
  print "Executing: %s" % cmd
  os.system(cmd)

  # cmd = "echo manual >> /etc/init/mesos-slave.override"
  # print "Executing: %s" % cmd
  # os.system(cmd)  


def set_slave( data, is_aws ):
  """
  Modifies all the files required to add this node as a Slave node

    is_aws: Flag indicating whether we are running this script from an AWS
            instance or not  
  """
  print "Setting Up a Mesos Slave"

  print "***************** Stopping Zoo and Master  *****************"
  cmd = "stop zookeeper"
  print "Executong %s" % cmd
  os.system(cmd)

  cmd = "stop mesos-master"
  print "Executong %s" % cmd
  os.system(cmd)


  print "***************** Modifying zookeeper.override  *****************"
  cmd = "rm -f /etc/init/zookeeper.override"
  print "Executing: %s" % cmd
  os.system(cmd)
  # cmd = "echo manual >> /etc/init/zookeeper.override"
  # print "Executing: %s" % cmd
  # os.system(cmd)

  print "***************** Modifying master.override  *****************"
  cmd = "rm -f /etc/init/mesos-master.override"
  print "Executing: %s" % cmd
  os.system(cmd)

  # cmd = "echo manual >> /etc/init/mesos-master.override"
  # print "Executing: %s" % cmd
  # os.system(cmd) 

  print "***************** Configuring IP and Hostname  *****************"
  cmd = "rm -f /etc/mesos-slave/ip"
  print "Executing: %s" % cmd
  os.system(cmd)
  
  cmd = "rm -f /etc/mesos-slave/hostname"
  print "Executing: %s" % cmd
  os.system(cmd)

  cmd = "echo %s >> /etc/mesos-slave/ip" % data['ip-address']
  print "Executing: %s" % cmd
  os.system(cmd)  

  cmd = "echo %s >> /etc/mesos-slave/hostname" % data['ip-address']
  print "Executing: %s" % cmd
  os.system(cmd)

  print "***************** Adding Attributes  *****************"
  cmd = "rm -f /etc/mesos-slave/attributes"
  print "Executing: %s" % cmd
  os.system(cmd)
  
  iid = data['instance-id']
    
  if data.has_key('session-id'):
    sid = data['session-id']
    print "Setting attributes IID: %s and SID: %s" % (iid, sid)
    cmd = 'echo "instance-id:%s;session-id:%s" >> /etc/mesos-slave/attributes' % (iid, sid)
  else:
    print "Setting attributes IID: %s" % (iid)
    cmd = "echo instance-id:%s >> /etc/mesos-slave/attributes" % iid
  
  print "Executing: %s" % cmd
  os.system("%s" % cmd) 


if __name__ == '__main__':
  args = sys.argv[1:]
  sz = len(args)

  _DATA = None

  print args
  if sz == 1:
    print "The Configuration: %s" % args[0]
    _DATA = json.loads(args[0])

  else:
    print "ERROR: Need to pass the configuration parameters such as:"
    print "       mesos-type: Either SLAVE or MASTER"
    print "    If setting up as master:"
    print "       server-id: The server number (1 - 255)"
    print "       masters:   a list of dictionaries"
    print "                  [{'id': 1, 'ip-address': '10.0.2.1', 'port':'2888:3888'}]"
    print ""
    print "Make sure the fields inside the JSON uses double-quote and the"
    print "outer quotes are single, otherwise an error is thrown"
    print ""
    print "This script needs to be executed by root"
    sys.exit(-1)

  if os.geteuid() != 0:
    print ""
    print "    ERROR:  This script must be executed by root"
    print ""
    sys.exit(-2)

  run_config(_DATA)


