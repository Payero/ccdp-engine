#!/usr/bin/env python
# encoding: utf-8

from optparse import OptionParser
import logging
from pprint import pformat
import boto3, botocore, requests
import os, sys, traceback
import tarfile, json
from subprocess import call
import boto3
import urllib
import ast, socket


class Test:

  __LEVELS = {"debug": logging.DEBUG, 
              "info": logging.INFO, 
              "warning": logging.WARN,
              "error": logging.ERROR}
  
  __NFS_HOST ="192.168.86.10"
  __DIR_LOC = "/media/root/Pictures"
  __MNT_DIRS = ['root', 'music', 'video']
  __MNT_CMD = "sudo mount %s:/volume1/%s /media/%s -o %s"
  __MNT_OPTS = "nouser,rsize=8192,wsize=8192,atime,auto,rw,dev,exec,suid"
  
  def __getLogger(self, name='Tester', level='debug'):
    logger = logging.getLogger(name)
    handler = logging.StreamHandler()
    formatter = logging.Formatter(
            '%(asctime)s %(name)-12s %(lineno)d %(levelname)-8s %(message)s')
    handler.setFormatter(formatter)
    logger.addHandler(handler)
    
    # Setting root level to warning and THEN set the level for this module
    logger.setLevel(self.__LEVELS[level.lower()])
    logging.getLogger(name).setLevel(self.__LEVELS[level.lower()])
    return logger
    
    
  def __init__(self, cli_args):
    self.__logger = self.__getLogger(level=cli_args.verb_level)
    self.__logger.debug("Logging Done")
    
    self.__runTest(cli_args)


  def __runTest(self, args):
    self.__logger.info("Running the test")

    s = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    try:
      s.connect((self.__NFS_HOST, 22))
      self.__logger.debug("Host %s available checking directory" % self.__NFS_HOST)
      if not os.path.isdir(self.__DIR_LOC):
        self.__logger.info("Need to mount the drive")
        for d in self.__MNT_DIRS:
          cmd = self.__MNT_CMD % (self.__NFS_HOST, d, d, self.__MNT_OPTS)
          self.__logger.info("Executing: %s" % cmd)
      else:
        self.__logger.info("%s is already available" % self.__DIR_LOC)

    except socket.error as e:
      self.__logger.debug("Host %s not available" % self.__NFS_HOST)

    s.close()

    
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
  
  
  (options, args) = parser.parse_args()
  
  test = Test(options)
      