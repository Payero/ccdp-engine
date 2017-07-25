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
import ast


class Test:
  
  __LEVELS = {"debug": logging.DEBUG, 
              "info": logging.INFO, 
              "warning": logging.WARN,
              "error": logging.ERROR}
  
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
    self.get_data()
   

  def get_instance_name(self, fid):
      """
          When given an instance ID as str e.g. 'i-1234567', return the instance 'Name' from the name tag.
          :param fid:
          :return:
      """
      ec2 = boto3.resource('ec2')
      ec2instance = ec2.Instance(fid)
      instancename = ''
      for tags in ec2instance.tags:
          if tags["Key"] == 'Name':
              instancename = tags["Value"]
      return instancename


  def get_data(self):
    #meta = boto.utils.get_instance_metadata()
    #id = meta['instance-id']
    try:
      response = requests.get('http://169.254.169.254/latest/meta-data/instance-id', timeout=2)
      id = response.text
      print "Getting Data: %s " % get_instance_name(id)
    except:
      print "Timed out, now what?"

        
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
      