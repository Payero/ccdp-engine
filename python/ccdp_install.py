#!/usr/bin/env python
# encoding: utf-8

from optparse import OptionParser
import logging
from pprint import pformat
import boto3, botocore, boto.utils
import os, sys, traceback
import tarfile, json
from subprocess import call
import shutil
import glob, socket

class CcdpInstaller:
  
  __CCDP_DIST = 'ccdp-engine.tgz'
  __MESOS_CFG = 'ccdp_mesos_settings.json'
  __METADATA_URL = 'http://169.254.169.254/latest/meta-data/instance-id'
  
  
  __LEVELS = {"debug":    logging.DEBUG, 
              "info":     logging.INFO, 
              "warning":  logging.WARN,
              "error":    logging.ERROR}
  

  def __init__(self, cli_args):
    self.__logger = logging.getLogger('CcdpInstaller')
    handler = logging.StreamHandler()
    filelog = logging.FileHandler('/tmp/ccdp_install.log')

    formatter = logging.Formatter(
            '%(asctime)s %(name)-12s %(lineno)d %(levelname)-8s %(message)s')
    handler.setFormatter(formatter)
    filelog.setFormatter(formatter)

    self.__logger.addHandler(handler)
    self.__logger.addHandler(filelog)
    
    # Setting root level to warning and THEN set the level for this module
    self.__logger.setLevel(self.__LEVELS['warning'])
    logging.getLogger('CcdpInstaller').setLevel(self.__LEVELS[cli_args.verb_level])
    
    self.__logger.debug("Logging Done")
    
    self.__s3 = boto3.resource('s3')
        
    
    if cli_args.action == 'download':
      self.__perform_download( cli_args )

    elif cli_args.action == 'upload':
      self.__perform_upload( cli_args )

    elif cli_args.action == 'nickname':
      self.__set_nickname()



  def __perform_download(self, params):
    """
    Gets the ccdp-engine.tgz from the CCDP bucket.  These files are used to i
    nstall CCDP
    """
    self.__logger.debug("Performing Download using %s" % pformat(params))
    
    bkt_name = params.bkt_name
    
    bkt = self.__s3.Bucket(bkt_name)
    
    tgt_dir = params.tgt_install
    if not os.path.isdir(tgt_dir):
      os.makedirs(tgt_dir)

    self.__logger.debug("Downloading distribution file ")
    fpath = os.path.join(tgt_dir, self.__CCDP_DIST)
    self.__logger.debug("Saving file in %s" % fpath)
    # if not os.path.isdir(fpath):
    #   os.makedirs(fpath)
    #   os.chmod(fpath, 0750)


    bkt.download_file(self.__CCDP_DIST, fpath)
 

    # Do I need to install CCDP?
    if params.inst_ccdp:
      ccdp_root = self.__install_ccdp(params)

    # Do I need to set mesos?
    if params.set_mesos:
      self.__set_mesos( ccdp_root, params )



  def __perform_upload(self, params):
    """
    First it attempt to create the ccdp-dist bucket in case this is the 
    first time the distributions are created.  Once is done it uploads the 
    following files into it:
      - ccdp-dist.tgz:  Contains all the files needed to run the ccdp-engine.
      - ccdp_install.py: The script used to install the ccdp-engine
    
    """
    self.__logger.debug("Performing Upload using %s" % pformat(str(params)))
  
    bkt_name = params.bkt_name
      # Creating a bucket
    try:
#       self.__s3.create_bucket(Bucket=bkt_name, CreateBucketConfiguration={
#                               'LocationConstraint': 'us-east-1'})  
      self.__s3.create_bucket(Bucket=bkt_name)
      self.__logger.info("Bucket %s was created" % bkt_name)
    except:
      self.__logger.info("Bucket (%s) already exists" % bkt_name) 
    
    path = os.getenv('CCDP_HOME')
    if path == None:
      self.__logger.error("Need to set the CCDP_HOME env variable, exiting!!")
      sys.exit(-1)
    
    build_file = os.path.join(path, "build.xml")

    if params.compile:
      self.__logger.info("Compiling the source code")
      
      rc = os.system("ant -f %s" % build_file )
      if rc != 0: 
        self.__logger.error("Error on ant compile")
        sys.exit(1)
      else:
        self.__logger.info("Source code compiled successfully")


    # Do we need to install it?
    if params.inst_ccdp:
      self.__logger.info("Uploading the ccdp-engine from %s" % path )

      root_dir = os.path.join(path, "dist")
      dist_file = os.path.join(root_dir, self.__CCDP_DIST)
      mesos_file = os.path.join(path, "config/mesos", self.__MESOS_CFG)
      
      if os.path.isfile('%s' % dist_file):
        self.__logger.debug("File created successfully, uploading it")
        # Storing data
        self.__s3.Object(bkt_name, self.__CCDP_DIST).put(
                          Body=open(dist_file, 'rb'))
        self.__logger.debug("File %s was uploaded successfully" % self.__CCDP_DIST)

      else:
        self.__logger.error("Failed to create tar file %s" % self.__CCDP_DIST)

      
      if params.set_mesos:
        if os.path.isfile('%s' % mesos_file):
          self.__logger.debug("Uploading mesos configuration: %s" % mesos_file)
          # Storing data
          self.__s3.Object(bkt_name, self.__MESOS_CFG).put(
                            Body=open(mesos_file, 'rb'))
          self.__logger.debug("File %s was uploaded successfully" % self.__MESOS_CFG)

        else:
          self.__logger.error("Could not find file %s" % mesos_file )
      else:
        self.__logger.info("Skipping Mesos Settings upload")  

    else:
      self.__logger.info("Skipping CCDP baseline upload")
      
    
    bkt = self.__s3.Bucket(bkt_name)
    acl = bkt.Acl()
    bkt.Acl().put(ACL='public-read')   
    
    # want to delete dist file only if uploading (factory) and we compiled the code
    if params.compile and not params.keep_file:
      self.__logger.debug("Removing files")
      rc = os.system("ant -f %s clean" % build_file)


    
      
  def __install_ccdp(self, params ):
    """
    Install the CCDP baseline using the tar file store in a S3 bucket.
    """
    bkt_name = params.bkt_name
    tgt = params.tgt_install
    self.__logger.info("Installing the CCDP baseline in %s" % tgt)    
    
    bkt = self.__s3.Bucket(bkt_name)
    tgt_name = os.path.join(tgt, self.__CCDP_DIST)  
    self.__logger.debug("Saving tar file: %s as %s" % (self.__CCDP_DIST, tgt_name))
    bkt.download_file(self.__CCDP_DIST, tgt_name)
    inst_path = os.path.join(tgt, "ccdp-engine")
    
    if os.path.exists(inst_path):
      self.__logger.info("Removing old installation: %s" % inst_path)
      shutil.rmtree(inst_path)
      
    if tarfile.is_tarfile(tgt_name):
      self.__logger.debug("Tar file found, decompressing it")
      tar = tarfile.open(tgt_name, 'r:*')
      tar.extractall(path=tgt)
      tar.close()
      self.__logger.debug("Done extracting file %s" % tgt_name)
    
    
    
    self.__logger.info("CCDP installed in %s " % inst_path)
    logs = os.path.join(inst_path, 'logs')
    if not os.path.exists(logs):
      self.__logger.debug("Creating logs directory: %s" % logs)
      os.makedirs(logs)
    
    cfg = os.path.join(inst_path, "config", "ccdp-config.properties")
    log = os.path.join(inst_path, "config", "log4j.properties")
    bin_dir = os.path.join(inst_path, "bin")
    for name in glob.glob("%s/*" % bin_dir):
      self.__logger.debug("Changing permission to %s" % name)
      os.chmod(name, 0777)


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
              new_line = "%s=%s\n" % (key,os.path.join(inst_path, 'logs/framework.log'))
          
          tgt.write(new_line)    
      
      tgt.close()
      infile.close()
      
    return inst_path
        

  def __set_mesos(self, ccdp_root, params):
    """
    Install the configuraion related to Mesos
    """
    self.__logger.info("Installing Mesos Settings")

    bkt = bkt = self.__s3.Bucket( params.bkt_name )

    if ccdp_root != None:
      self.__logger.info("CCDP is installed in %s" % ccdp_root)
    else:
      self.__logger.error("Need the location where CCDP was installed")
      sys.exit(-2)

    self.__logger.debug("Downloading settings file ")
    fpath = os.path.join(ccdp_root, "config/mesos", self.__MESOS_CFG)
    self.__logger.debug("Saving file in %s" % fpath)
    bkt.download_file(self.__MESOS_CFG, fpath)
    os.chmod(fpath, 0777)

    if not os.path.isfile(fpath):
      self.__logger.error("The JSON mesos settings file was not found ")
      sys.exit(-3)
          
    self.__logger.debug("Loading Json data")
    data = open( fpath ). read()
    json_data = json.loads(data)
    self.__logger.info("CCDP HOME: %s" % ccdp_root)

    # Do I need to add session?
    sid = params.session_id
    if sid:
      self.__logger.info("Adding Session ID: %s" % sid)
      json_data['session-id'] = sid
      with open(fpath, 'w') as outfile:
        self.__logger.info("Adding session %s to file %s" % (sid, fpath) )
        json.dump(json_data, outfile)
        
    self.__logger.info("Using Data: %s" % str(json_data))

    scripts = os.path.join(ccdp_root, 'python')
    try:
      sys.path.index(scripts)
    except ValueError:
      self.__logger.info("%s was not found in path, adding it" % scripts)
      sys.path.append(scripts)
      self.__logger.info("Path (%s) added to the sys.path" % scripts)
    
    os.chdir(scripts)
    cmd = []
    os.environ['CCDP_HOME'] = ccdp_root

    self.__run_sudo_cmd( ["./mesos_config.py", fpath] )

      
    self.__logger.debug("Running: %s " % ' '.join( cmd ) )
    n = call( cmd )
    self.__logger.debug("The Exit Code: %d" % n)



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
  
    
  desc = "Sets the environment for a new machine so it can run CCDP.\n"
  desc += "The settings are stored in a S3 bucket and are retrieved\n"
  desc += "during the execution of the program."
  parser = OptionParser(usage="usage: %prog [options] args",
                        version="%prog 1.0",
                        description=desc)
  
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
        default='download',                      
        help='To either download or upload files to set the env',)
  
  parser.add_option("-b", "--s3-bucket",
        dest="bkt_name",
        default='ccdp-dist',
        help="The name of the bucket containing the env files")
  
  parser.add_option("-i", "--install-ccdp",
        action="store_true", dest="inst_ccdp", default=False,
        help="Downloads and install the CCDP baseline")

  parser.add_option("-c", "--compile",
        action="store_true", dest="compile", default=False,
        help="Compiles the source code by using ant")

  parser.add_option("-k", "--keep-file",
        action="store_true", dest="keep_file", default=False,
        help="It does not delete the ccdp-engine-dist.tar file after upload")

  parser.add_option("-n", "--skip-mesos",
        action="store_false", dest="set_mesos", default=True,
        help="It does not install mesos components or services")

  parser.add_option("-s", "--session-id",
                    dest="session_id",
                    default='',
                    help="The session id this VM is assigned to ")

  parser.add_option('-t', '--target-install',
        action='store',
        dest='tgt_install',
        default='/data',
        help='The location where CCDP needs to be installed',)
  


  (options, args) = parser.parse_args()
  
  CcdpInstaller(options)
      
