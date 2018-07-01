#!/usr/bin/env python
# encoding: utf-8

from optparse import OptionParser
import logging
from pprint import pformat
import boto3, botocore
import os, sys, traceback
import tarfile, json
from subprocess import call
import shutil
import glob, socket

class CcdpInstaller:
  """
  Performs different manipulations to install and/or configure the CCDP engine.
  Its main goal is to provide a way to generate, distribute, and install files
  from a common place that can be used when deploying new VMs.  It uses a single
  file to performs its operations:

    - dist-file:  A compressed file containing the distribution package for the
                  ccdp-engine

  Actions ( -a | --action ):
    default = None

    There are two main operations that require attention: download and upload.
    As their names indicates it uploads or downloads the distribution afile
    from/to an S3 bucket or from/to the file system.  

    IMPORTANT: If the file is not passed as an argument no action is 
               performed on it
  
  File:
    The beginning of the file indicates whether we are uploading/downloading
    from/to an S3 bucket or file system.  If the file begins with 's3://' then
    with the file will be download/upload from/to an S3 bucket. If the file  
    begins anything else then the file will be download/upload from/to a file 
    system location contingent upon is a valid absolut or relative path
  
    distribution file: ( -d | --ccdp-dist-file )
      default = None

      The compressed file containing the ccdp-engine application so it can be 
      either download or upload to a S3 bucket or file system

  
  Target Location ( -t  | --target-location )
  default = None

    Defines the location to either upload the files or where the files will be
    downloaded and installed.  

    IMPORTANT:  This argument must be a directory and not a filename

  Verbosity Level( -v | --verbosity-level ):
    default = debug

    Indicates how much help is displayed to the screen when performing an 
    operation.  A higher level decreases the verbosity.  The options are:
      'debug', 'info', 'warning','error'
  
  Compile flag ( -c | --compile ):
    If the action is upload and this flag is set, then the script will use the
    CCDP_HOME environment variable to find the source code.  If found it will
    execute the appropriate ant command to build the code prior uploading the
    necessary files

  Session Id ( -s | --session-id ):
   Sets the session id as an atrribute and is interpreted by the ccdp agent
   

  """
  
  __METADATA_URL = 'http://169.254.169.254/latest/meta-data/instance-id'
  
  __S3_PROT = 's3://'

  __LEVELS = {"debug":    logging.DEBUG, 
              "info":     logging.INFO, 
              "warning":  logging.WARN,
              "error":    logging.ERROR}
  

  def __init__(self, cli_args):
    self.__logger = logging.getLogger('CcdpInstaller')
    handler = logging.StreamHandler()

    log_file = '/tmp/ccdp_install.log'
    if os.path.isfile(log_file):
      os.remove(log_file)

    filelog = logging.FileHandler(log_file)

    formatter = logging.Formatter(
            '%(asctime)s %(name)-12s %(lineno)d %(levelname)-8s %(message)s')
    handler.setFormatter(formatter)
    filelog.setFormatter(formatter)

    self.__logger.addHandler(handler)
    self.__logger.addHandler(filelog)
    
    # Setting root level to warning and THEN set the level for this module
    self.__logger.setLevel(self.__LEVELS['warning'])
    lvl = self.__LEVELS[cli_args.verb_level]
    logging.getLogger('CcdpInstaller').setLevel(lvl)
    
    self.__logger.debug("Logging Done")

    loc = cli_args.tgt_location

    if cli_args.action == 'download':
      if loc == None or not os.path.isdir(loc):
        self.__logger.error("The target location %s is invalid" % loc)
        sys.exit(-1)

      self.__perform_download( cli_args )

    elif cli_args.action == 'upload':
      msg = None
      if loc == None:
        msg = "The target location cannot be None so it needs to be provided"
      elif not loc.startswith(self.__S3_PROT):
        if not os.path.isdir(loc):
          msg = "The target directory %s does not exists" % loc

      if msg is not None:
        self.__logger.error(msg)
        sys.exit(-1)

      self.__perform_upload( cli_args )

    elif cli_args.action == 'nickname':
      self.__set_nickname()


    # Sets the environment variable with the node type
    if cli_args.node_type:
      self.__logger.info("Setting the node type")
      bin_file = "ccdp-engine/bin/ccdp_agent.sh"
      agent_file = os.path.join('/data/ccdp/', bin_file)
      self.set_node_type(agent_file, cli_args.node_type)


    # Runs an agent 
    if cli_args.worker_agent:
      self.__logger.info("Starting a ccdp-agent worker")
      cfg_file = "ccdp-engine/config/ccdp-agent.service"
      src = os.path.join(cli_args.tgt_location, cfg_file)
      shutil.copyfile(src, '/etc/systemd/system/ccdp-agent.service')
      os.system("systemctl daemon-reload")
      os.system("systemctl enable ccdp-agent.service")  
      os.system("systemctl stop ccdp-agent")    
      os.system("systemctl start ccdp-agent")

    # Runs an agent inside a docker container 
    if cli_args.docker_container:
      self.__logger.info("Starting a ccdp-agent worker inside a container")
      bin_file = "ccdp-engine/bin/ccdp_agent.sh"
      src = os.path.join(cli_args.tgt_location, bin_file)
      self.__logger.info("Running %s start" % src)
      os.system("%s start" % src)


  def set_node_type(self, agent_file, node_type):
    try:
      src = os.path.join('/data/ccdp/', agent_file)
      
      if not os.path.isfile(src):
        self.__logger.error("The file was not found")
        return

      self.__logger.info("modifying %s" % src)
      base, ext = os.path.splitext(src)
      prev_name = "%s.PREV" % base
      os.rename(src, prev_name)
      new_file = open(src, 'w')
      pre_file = open(prev_name, 'r')
      lines = pre_file.readlines()
      n = len(lines)
      i = 0
      found_it = False

      for line in lines:
        i += 1
        if line.find('CCDP_NODE_TYPE') >= 0:
          self.__logger.debug("Modifying line: %s" % line)
          line = "export CCDP_NODE_TYPE=%s\n" % node_type
          found_it = True
        
        if i < n:
          next_line = lines[i]
          if not found_it and next_line.find('run_service.sh') >= 0:
            self.__logger.debug("The Export statement was not there adding it")
            new_file.write("export CCDP_NODE_TYPE=%s\n" % node_type)

        new_file.write("%s" % line)
      
      os.chmod(agent_file, 0777)
      
    except:
      traceback.print_exc()


  def __handle_files(self, params):
    """
    Because we are supportring multiple protocols, want to centralize where we
    are handling the files manipulation
    """
    self.__files = { 'dist-file':{} }

    if params.action == 'download':
      
      # First, do we have a distribution file?
      if params.ccdp_dist_file:
        # just in case using environment variables
        src_file = os.path.expandvars(params.ccdp_dist_file)
        tgt_dir = params.tgt_location
        self.__files['dist-file']['path'] = self.__download_file(src_file, 
                                                                  tgt_dir)

    elif params.action == 'upload':
      # First, do we have a distribution file?
      if params.ccdp_dist_file:
        # just in case using environment variables
        src_file = os.path.expandvars(params.ccdp_dist_file)
        tgt_dir = params.tgt_location
        self.__files['dist-file']['path'] = self.__upload_file(src_file, 
                                                                  tgt_dir)



  def __download_file(self, src_file, tgt_dir):
    """
    Gets a file from the source container (S3 bucket, file system, etc) and 
    saves it in the given directory.  If the file cannot be found after download
    it returns None otherwise it returns the final location path
    """
    self.__logger.info("Downloading file from %s to %s" % (src_file, tgt_dir))
    
    if not os.path.isdir(tgt_dir):
      os.makedirs(tgt_dir)
      os.chmod(tgt_dir, 0750)

    filename = 'bogus_filename'

    # getting the file from a S3 bucket
    if src_file.startswith(self.__S3_PROT):
      self.__logger.info("Getting file from S3 bucket")
      s3 = boto3.resource('s3')

      path, fname = os.path.split(src_file)
      bkt_name = path[len(self.__S3_PROT):]
      bkt = s3.Bucket(bkt_name)

      self.__logger.debug("Downloading file ")
      fpath = os.path.join(tgt_dir, fname)
      self.__logger.debug("Saving file in %s" % fpath)
      
      bkt.download_file(fname, fpath)
      filename = fpath
    
    # getting the file from file system
    else:
      self.__logger.info("Getting file from file system")
      if not os.path.isfile(src_file):
        self.__logger.error("The source file %s is invalid" % src_file)
        return None

      path, fname = os.path.split(src_file)
      tgt = os.path.join(tgt_dir, fname)
      self.__logger.debug("Copying file from %s to %s" % (src_file, tgt))
      shutil.copyfile(src_file, tgt)
      filename = tgt
    

    if os.path.isfile(filename):
      self.__logger.info("File acquired %s" % filename)
      return filename
    else:
      return None


  def __upload_file(self, src_file, tgt_loc):
      """
      Uploads a file to a destination container (S3 bucket, file system, etc).
      If no errors are found during upload, it returns the final destination 
      otherwise it returns None
      """
      self.__logger.info("Uploading file %s into %s" % (src_file, tgt_loc))
      
      # Do we have a valid source file?
      if src_file is None or not os.path.isfile(src_file):
        self.__logger.error("The source file %s is invalid" % src_file)
        return None

      filename = 'bogus_filename'
      path, fname = os.path.split(src_file)

      # getting the file from a S3 bucket
      if tgt_loc.startswith(self.__S3_PROT):
        self.__logger.info("Uploading file into S3 bucket")
        s3 = boto3.resource('s3')

        if tgt_loc.endswith('/'):
          tgt_loc = tgt_loc[:-1]

        bkt_name = tgt_loc[len(self.__S3_PROT):]


        # Creating a bucket
        try:
          #self.__s3.create_bucket(Bucket=bkt_name, CreateBucketConfiguration={
          #                        'LocationConstraint': 'us-east-1'})  
          s3.create_bucket(Bucket=bkt_name)
          self.__logger.info("Bucket %s was created" % bkt_name)
        except:
          self.__logger.info("Bucket (%s) already exists" % bkt_name) 
        
        bkt = s3.Bucket(bkt_name)
        # Storing data
        s3.Object(bkt_name, fname).put(
                          Body=open(src_file, 'rb'))
        self.__logger.debug("File %s was uploaded successfully" % fname)
        
        acl = bkt.Acl()
        bkt.Acl().put(ACL='public-read')   

        return fname


      # getting the file from file system
      else:
        self.__logger.info("Uploading file into file system")
        if not os.path.isdir(tgt_loc):
          self.__logger.error("The target location %s is invalid" % tgt_loc)
          return None

        tgt = os.path.join(tgt_loc, fname)
        self.__logger.debug("Copying file from %s to %s" % (src_file, tgt))
        shutil.copyfile(src_file, tgt)
        filename = tgt
      

        if os.path.isfile(filename):
          self.__logger.info("File uploaded %s" % filename)
          return filename
        else:
          return None



  def __perform_download(self, params):
    """
    Gets the ccdp-engine.tgz from the CCDP bucket.  These files are used to 
    install CCDP
    """
    self.__logger.debug("Performing Download using %s" % pformat(params))
    
    self.__handle_files(params)
    self.__logger.debug("Files: %s" % pformat(self.__files))

    # just in case using environment variables
    if self.__files['dist-file'].has_key('path'):
      dist_file = self.__files['dist-file']['path']

      if os.path.isfile(dist_file):
        self.__install_ccdp(dist_file, params.tgt_location)

    else:
      self.__logger.info("No distro file provided, skipping install")


    # # Runs an agent 
    # if params.worker_agent:
    #   self.__logger.info("Starting a ccdp-agent worker")
    #   cfg_file = "ccdp-engine/config/ccdp-agent.service"
    #   src = os.path.join(params.tgt_location, cfg_file)
    #   shutil.copyfile(src, '/etc/systemd/system/ccdp-agent.service')
    #   os.system("systemctl daemon-reload")
    #   os.system("systemctl enable ccdp-agent.service")  
    #   os.system("systemctl stop ccdp-agent")    
    #   os.system("systemctl start ccdp-agent")

    # # Runs an agent inside a docker container 
    # if params.docker_container:
    #   self.__logger.info("Starting a ccdp-agent worker inside a container")
    #   bin_file = "ccdp-engine/bin/ccdp_agent.sh"
    #   src = os.path.join(params.tgt_location, bin_file)
    #   self.__logger.info("Running %s start" src)
    #   os.system("%s start" % src)


  def __perform_upload(self, params):
    """
    First it attempt to create the ccdp-dist bucket in case this is the 
    first time the distributions are created.  Once is done it uploads the 
    following files into it:
      - ccdp-dist.tgz:  Contains all the files needed to run the ccdp-engine.
      - ccdp_install.py: The script used to install the ccdp-engine
    
    """
    self.__logger.debug("Performing Upload using %s" % pformat(str(params)))
  
    
    if params.compile:
      path = os.getenv('CCDP_HOME')
      if path == None:
        self.__logger.error("Need to set the CCDP_HOME env variable, exiting!!")
        sys.exit(-1)
      
      build_file = os.path.join(path, "build.xml")
      self.__logger.info("Compiling the source code")
      
      rc = os.system("ant -f %s" % build_file )
      if rc != 0: 
        self.__logger.error("Error on ant compile")
        sys.exit(1)
      else:
        self.__logger.info("Source code compiled successfully")

    
    self.__handle_files(params)
    self.__logger.debug("Files: %s" % pformat(self.__files))

    
      
  def __install_ccdp(self, dist_file, tgt_loc ):
    """
    Install the CCDP baseline using the tar file store in a S3 bucket.
    """
    self.__logger.info("Installing the CCDP baseline in %s" % tgt_loc)    
    
    inst_path = os.path.join(tgt_loc, "ccdp-engine")
    
    if os.path.exists(inst_path):
      self.__logger.info("Removing old installation: %s" % inst_path)
      shutil.rmtree(inst_path)
      
    if tarfile.is_tarfile(dist_file):
      self.__logger.debug("Tar file found, decompressing it")
      tar = tarfile.open(dist_file, 'r:*')
      tar.extractall(path=tgt_loc)
      tar.close()
      self.__logger.debug("Done extracting file %s" % dist_file)
    
    
    
    self.__logger.info("CCDP installed in %s " % inst_path)
    logs = os.path.join(inst_path, 'logs')
    if not os.path.exists(logs):
      self.__logger.debug("Creating logs directory: %s" % logs)
      os.makedirs(logs)
    
    cfg = os.path.join(inst_path, "config", "ccdp-config.properties")
    log = os.path.join(inst_path, "config", "log4j.properties")
    bin_dir = os.path.join(inst_path, "bin")
    os.chmod(bin_dir, 0777)
    for name in glob.glob("%s/*" % bin_dir):
      self.__logger.debug("Changing permission to %s" % name)
      os.chmod(name, 0777)

    cfg_dir = os.path.join(inst_path, "config")
    os.chmod(cfg_dir, 0777)
    for name in glob.glob("%s/*" % cfg_dir):
      self.__logger.debug("Changing permission to %s" % name)
      os.chmod(name, 0666)

    log_dir = os.path.join(inst_path, "logs")
    os.chmod(log_dir, 0777)
    for name in glob.glob("%s/*" % log_dir):
      self.__logger.debug("Changing permission to %s" % name)
      os.chmod(name, 0666)


    self.__logger.info("Modifying the Configuration file")
    if os.path.exists(cfg):
      src = os.path.join(inst_path, "config", 'ccdp-config.ORIG')
      os.rename(cfg, src)
      tgt = open(cfg, 'w')
      with open(src, 'r') as infile:
        for line in infile:
          new_line = "%s\n" % line.strip()
          key_val = line.split('=')
          if len(key_val) == 2:
            key = key_val[0]
            if key == 'log4j.config.file':
              self.__logger.debug("Found the config file")
              new_line = "%s=%s\n" % (key, log)
          
          tgt.write(new_line)    
      
      tgt.close()
      infile.close()
      

    self.__logger.info("Modifying the Log4J file")
    if os.path.exists(log):
      src = os.path.join(inst_path, "config", 'log4j.properties.ORIG')
      os.rename(log, src)
      tgt = open(log, 'w')
      with open(src, 'r') as infile:
        for line in infile:
          new_line = "%s\n" % line.strip()
          key_val = line.split('=')
          if len(key_val) == 2:
            key = key_val[0]
            if key == 'log4j.appender.logfile.file':
              self.__logger.debug("Found the log file")
              new_line = "%s=%s\n" % (key,os.path.join(inst_path, 
                                      'logs/framework.log') )
          
          tgt.write(new_line)    
      
      tgt.close()
      infile.close()
      
    return inst_path
        

  
  def __set_nickname(self):
    """
    Queries AWS for the Tag set as the 'Name' for this EC2 instance.  If found
    then it sets the /etc/profile.d/prompt.sh file to export the NICKNAME 
    environment variable which is later on used to set the command prompt.
    """
    self.__logger.debug("Setting Nickname")
    name = socket.gethostname()

    try:
      response = requests.get(self.__METADATA_URL, timeout=2)
      iid = response.text
      ec2 = boto3.resource('ec2')
      ec2instance = ec2.Instance(iid)
      for tags in ec2instance.tags:
        if tags["Key"] == 'Name':
          name = tags["Value"]
          break

      fname = '/etc/profile.d/prompt.sh'
      if os.path.isfile(fname):
        self.__run_sudo_cmd(["mv", fname, "/etc/profile.d/prompt.BACKUP"])


      src_file = '/tmp/prompt.sh'
      with file(src_file , 'w') as out:
        out.write("export NICKNAME=%s" % name)
        out.write("\n")
        out.close()
        os.chmod(src_file, 0644)
        
        self.__run_sudo_cmd( ["mv", src_file, fname] )


    except:
      self.__logger.debug("Is not an EC2 Instance, skipping nickname")
      traceback.print_exc()



  def __run_sudo_cmd(self, cmd):
    """
    Tests the user running this script, if not root then it inserts the 'sudo'
    command at the beggining of the given command.  Once the command is set
    properly, it is executed and returns the value of its execution

    cmd: a list of commands to execute
    return: the exit value of the command execution
    """
    self.__logger.debug("Generating sudo command if requrired")
    if os.getuid() != 0:
      self.__logger.warn("WARNING: This script needs to be executed by root, will try using sudo")
      cmd.insert(0, "sudo")

    self.__logger.debug("Running: %s " % ' '.join( cmd ) )
    return call(cmd)



"""
  Runs the application by instantiating a new Test object and passing all the
  command line arguments
"""  
if __name__ == '__main__':
  import textwrap

  lines = textwrap.dedent(CcdpInstaller.__doc__).splitlines()
  
  help_text = "\n".join(lines)
  # for line in lines:
  #   help_text += "\n%s" % line

  parser = OptionParser(usage=help_text, version="%prog 1.0")
  
  parser.add_option('-v', '--verbosity-level',
        type='choice',
        action='store',
        dest='verb_level',
        choices=['debug', 'info', 'warning','error',],
        default='debug',
        help='The verbosity level of the logging',)
  
  parser.add_option('-a', '--action',
        type='choice',
        action='store',
        dest='action',
        choices=['download', 'upload', 'nickname'],
        default=None,                      
        help='To either download or upload files to set the env',)
  
  parser.add_option("-d", "--ccdp-dist-file",
        dest="ccdp_dist_file",
        default=None,
        help="The location of the distribution file to install")

  parser.add_option('-t', '--target-location',
        action='store',
        dest='tgt_location',
        default=None,
        help='The location where files needs to be installed or uploaded',)

  parser.add_option("-c", "--compile",
        action="store_true", dest="compile", default=False,
        help="Compiles the source code by using ant")

  parser.add_option("-n", "--node-type",
        dest="node_type",
        default=None,
        help="The node_type this VM is assigned to ")

  parser.add_option("-w", "--worker-agent",
        action="store_true", dest="worker_agent", default=False,
        help="Starts a ccdp-agent if this option is present")  

  parser.add_option("-D", "--docker",
        action="store_true", dest="docker_container", default=False,
        help="Starts a ccdp-agent inside a container if this option is present")  


  (options, args) = parser.parse_args()
  
  CcdpInstaller(options)
      
