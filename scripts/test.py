#!/usr/bin/env python

import os, sys, json, time, math, socket, uuid
from pprint import pprint, pformat


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
  cli_data['is-aws'] = is_aws
  
  print "Setting up environment using: %s" % pformat(cli_data)

  if os.path.isfile('/home/ubuntu/ZOO_CFG'):
    file_in = open('/home/ubuntu/ZOO_CFG', 'r')
    print "Found it"
  else:
    print "Was not there"
    
    if os.path.isfile('./ZOO_CFG'):
      print "Found it locally"
      file_in = open('./ZOO_CFG', 'r')



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
    sys.exit(-1)

  run_config(_DATA)