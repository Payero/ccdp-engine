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
    node_type = "NIFI-NODE"
    bin_file = "ccdp-engine/bin/ccdp_agent.sh"
    agent_file = os.path.join('/data/ccdp/', bin_file)

    self.set_node_type(agent_file, node_type)


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
      
    except:
      traceback.print_exc()

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
      