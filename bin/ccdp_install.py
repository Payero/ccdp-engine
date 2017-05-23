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
import glob

class CcdpInstaller:
  
  __CCDP_DIST = 'ccdp-engine.tar'
  __CCDP_INST = 'ccdp_install.py'
  
  
  __LEVELS = {"debug": logging.DEBUG, 
            "info": logging.INFO, 
            "warning": logging.WARN,
            "error": logging.ERROR}
  
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
    else:
      self.__perform_upload( cli_args )



  def __perform_download(self, params):
    """
    Gets the ccdp-engine.tar from the CCDP bucket.  These files are used to install CCDP
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
    bkt.download_file(self.__CCDP_DIST, fpath)

    self.__logger.debug("Downloading installation script")
    fpath = os.path.join(tgt_dir, self.__CCDP_INST)
    self.__logger.debug("Saving file in %s" % fpath)
    bkt.download_file(self.__CCDP_INST, fpath)


    # Do I need to install CCDP?
    if params.inst_ccdp:
      ccdp_root = self.__install_ccdp(params)

    
    
  def __perform_upload(self, params):
    """
    First it attempt to create the ccdp-dist bucket in case this is the 
    first time the distributions are created.  Once is done it uploads the following
    files into it:
      - ccdp-dist.tar:  Contains all the files needed to run the ccdp-engine.
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
      
      rc = os.system("ant -f %s ccdp.engine.dist" % build_file)
      if rc != 0: 
        self.__logger.error("Error on ant compile")
        sys.exit(1)
      else:
        self.__logger.info("Source code compiled successfully")


    if params.inst_ccdp:
      
      root_dir = os.path.join(path, "dist")
      dist_file = os.path.join(root_dir, self.__CCDP_DIST)
      inst_file = os.path.join(root_dir, self.__CCDP_INST)
      
      if os.path.isfile('%s' % dist_file):
        self.__logger.debug("File created successfully, uploading it")
        # Storing data
        self.__s3.Object(bkt_name, self.__CCDP_DIST).put(
                          Body=open(dist_file, 'rb'))
        self.__logger.debug("File %s was uploaded successfully" % self.__CCDP_DIST)

      else:
        self.__logger.error("Failed to create tar file %s" % self.__CCDP_DIST)

      
      if os.path.isfile('%s' % inst_file):
        self.__logger.debug("File created successfully, uploading it")
        # Storing data
        self.__s3.Object(bkt_name, self.__CCDP_INST).put(
                          Body=open(inst_file, 'rb'))
        self.__logger.debug("File %s was uploaded successfully" % self.__CCDP_INST)

      else:
        self.__logger.error("Failed to find script file %s" % self.__CCDP_INST)

    else:
      self.__logger.info("Skipping CCDP baseline upload")
      

    if not params.keep_file:
      self.__logger.debug("Removing files")
      rc = os.system("ant -f %s clean.eng.dist" % build_file)

    
    bkt = self.__s3.Bucket(bkt_name)
    acl = bkt.Acl()
    for grant in acl.grants:
        print(grant['Grantee']['DisplayName'], grant['Permission'])
    
    bkt.Acl().put(ACL='public-read')   
    
    
      
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

  parser.add_option('-t', '--target-install',
                    action='store',
                    dest='tgt_install',
                    default='/data',
                    help='The location where CCDP needs to be installed',)
  


  (options, args) = parser.parse_args()
  
  CcdpInstaller(options)
      
