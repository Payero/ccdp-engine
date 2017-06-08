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

class CcdpEnvSetter:
  
  __CCDP_DIST = 'ccdp-dist'
  __CCDP_SETTINGS = 'ccdp_mesos_settings.json'
  
  __LEVELS = {"debug":    logging.DEBUG, 
              "info":     logging.INFO, 
              "warning":  logging.WARN,
              "error":    logging.ERROR}
  
  def __init__(self, cli_args):
    self.__logger = logging.getLogger('CcdpEnvSetter')
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
    logging.getLogger('CcdpEnvSetter').setLevel(self.__LEVELS[cli_args.verb_level])
    
    self.__logger.debug("Logging Done")
    
    self.__s3 = boto3.resource('s3')
    
    
    
    if cli_args.action == 'download':
      self.__perform_download( cli_args )
    else:
      self.__perform_upload( cli_args )




  def __perform_download(self, params):
    """
    Gets the ccdp-dist.tar.gz and the ccdp_mesos_settings.json from the CCDP
    Settings bucket.  These files are used to install CCDP
    """
    self.__logger.debug("Performing Download using %s" % pformat(params))
    
    bkt_name = params.bkt_name
    bkt = self.__s3.Bucket(bkt_name)
    
    ccdp_root = os.getenv('CCDP_HOME')

    # Do I need to install CCDP?
    if params.inst_ccdp:
      ccdp_root = self.__install_ccdp(params)
#       ccdp_root = "/data/CCDP"
#       self.__logger.warn("SKIPPING INSTALL FOR NOW")
#       self.__logger.warn("SKIPPING INSTALL FOR NOW")

    elif ccdp_root != None:
      self.__logger.info("CCDP is installed in %s" % ccdp_root)
    else:
      self.__logger.error("Need to set the CCDP_HOME env. variable")
      sys.exit(-2)

    self.__logger.debug("Downloading settings file ")
    fpath = os.path.join(ccdp_root, "config/mesos", self.__CCDP_SETTINGS)
    self.__logger.debug("Saving file in %s" % fpath)
    bkt.download_file(self.__CCDP_SETTINGS, fpath)


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
    self.__logger.debug("Running ./mesos_config.py %s " % fpath)
    n = call(["./mesos_config.py", fpath ])
    self.__logger.debug("The Exit Code: %d" % n)
    
    
    
  def __perform_upload(self, params):
    """
    First it attempt to create the ccdp-settings bucket in case this is the 
    first time the settings are created.  Once is done it uploads the following
    files into it:
      - ccdp-dist.tar.gz:  Contains all the files in the ${CCDP_HOME}/dist 
                           directory.
      - ccdp_mesos_settings.json:  Contains all the settings used to configure
                                   mesos.  This file is store in the 
                                   ${CCDP_HOME}/scripts directory
    
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
    
    if params.compile:
      self.__logger.info("Compiling the source code")
      fname = os.path.join(path, "build.xml")
      rc = os.system("ant -f %s" % fname)
      if rc != 0: 
        self.__logger.error("Error on ant compile")
        sys.exit(1)
      else:
        self.__logger.info("Source code compiled successfully")


    if params.inst_ccdp:
      fname = '%s.tar.gz' % self.__CCDP_DIST
      self.__logger.debug("Creating file: %s" % fname)
      #base_name, format, root_dir=None, base_dir=None,
      root_dir = os.path.join(path, "dist/ccdp-engine")
      
      tar = tarfile.open(fname, "w:gz")
      tar.add(root_dir, os.path.basename(root_dir))
      tar.close()
      
      if os.path.isfile('%s' % fname):
        self.__logger.debug("File created successfully, uploading it")
        # Storing data
        self.__s3.Object(bkt_name, '%s.tar.gz' % self.__CCDP_DIST).put(
                          Body=open('./%s.tar.gz' % self.__CCDP_DIST, 'rb'))
        self.__logger.debug("File %s was uploaded successfully" % fname)
        if not params.keep_file:
          self.__logger.debug("Removing file %s" % fname)
          os.remove(fname)

      else:
        self.__logger.error("Failed to create tar file %s" % fname)
    else:
      self.__logger.info("Skipping CCDP baseline upload")
      
      
    fname = os.path.join(path, 'config/mesos', self.__CCDP_SETTINGS)
    if os.path.isfile(fname):
      self.__logger.info("Uploading CCDP Settings file: %s" % fname)
      self.__s3.Object(bkt_name, self.__CCDP_SETTINGS).put(
                        Body=open(fname, 'rb'))
      self.__logger.debug("File %s was uploaded successfully" % fname)
    else:
      self.__logger.error("Failed to upload file %s" % fname)
    
    bkt = self.__s3.Bucket(bkt_name)
    acl = bkt.Acl()
    for grant in acl.grants:
      if grant['Grantee'].has_key('DisplayName'):
        print(grant['Grantee']['DisplayName'], grant['Permission'])
    
    bkt.Acl().put(ACL='public-read')   
    
    
      
  def __install_ccdp(self, params ):
    """
    Install the CCDP baseline using the tar.gz file store in a S3 bucket.
    """
    bkt_name = params.bkt_name
    tgt = params.tgt_install
    self.__logger.info("Installing the CCDP baseline in %s" % tgt)    
    
    bkt = self.__s3.Bucket(bkt_name)
    dist_fname = "%s.tar.gz" % self.__CCDP_DIST
    tgt_name = os.path.join(tgt, dist_fname)  
    self.__logger.debug("Saving tgz file: %s as %s" % (dist_fname, tgt_name))
    bkt.download_file(dist_fname, tgt_name)
    inst_path = os.path.join(tgt, "ccdp-engine")
    
    if os.path.exists(inst_path):
      self.__logger.info("Removing old installation: %s" % inst_path)
      shutil.rmtree(inst_path)
      
    if tarfile.is_tarfile(tgt_name):
      self.__logger.debug("Tar file found, decompressing it")
      tar = tarfile.open(tgt_name, 'r:gz')
      tar.extractall(path=tgt)
      tar.close()
      self.__logger.debug("Done extracting file %s" % tgt_name)
    
    
    
    self.__logger.info("CCDP installed in %s " % inst_path)
    logs = os.path.join(inst_path, 'logs')
    if not os.path.exists(logs):
      self.__logger.debug("Creating logs directory: %s" % logs)
      os.makedirs(logs)
    
    cfg = os.path.join(inst_path, "ccdp-config.properties")
    log = os.path.join(inst_path, "log4j.properties")
    
    self.__logger.info("Modifying the Configuration file")
    if os.path.exists(cfg):
      src = os.path.join(inst_path, 'ccdp-config.ORIG')
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
            elif key == 'executor.src.jar.file':
              self.__logger.debug("Found the jar file")
              new_line = "%s=%s\n" % (key,os.path.join(inst_path, 'mesos-ccdp-exec.jar') )
          
          tgt.write(new_line)    
      
      tgt.close()
      infile.close()
      

    self.__logger.info("Modifying the Log4J file")
    if os.path.exists(log):
      src = os.path.join(inst_path, 'log4j.properties.ORIG')
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
                      choices=['download', 'upload'],
                      default='download',                      
                      help='To either download or upload files to set the env',)
  
  parser.add_option("-b", "--s3-bucket",
                    dest="bkt_name",
                    default='ccdp-settings',
                    help="The name of the bucket containing the env files")
  
  parser.add_option("-i", "--install-ccdp",
                  action="store_true", dest="inst_ccdp", default=False,
                  help="Downloads and install the CCDP baseline")

  parser.add_option("-c", "--compile",
                    action="store_true", dest="compile", default=False,
                    help="Compiles the source code by using ant")

  parser.add_option("-k", "--keep-file",
                  action="store_true", dest="keep_file", default=False,
                  help="It does not delete the ccdp-dist.tgz file after upload")

  parser.add_option('-t', '--target-install',
                    action='store',
                    dest='tgt_install',
                    default='/data',
                    help='The location where CCDP needs to be installed',)
  
  parser.add_option("-s", "--session-id",
                    dest="session_id",
                    default='',
                    help="The session id this VM is assigned to ")


  (options, args) = parser.parse_args()
  
  test = CcdpEnvSetter(options)
      
