#!/usr/bin/env python
# encoding: utf-8

from optparse import OptionParser
import logging
from pprint import pformat
import boto3, botocore
import os, sys, traceback
import tarfile, json
from subprocess import call
import boto3

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
    '''
    $ aws apigateway test-invoke-method \
    --rest-api-id cx62aa0x70 \
    --resource-id 3jpl8x \
    --http-method POST \
    --path-with-query-string "" \
    --body "{\"arguments\": \"1000000\",\"bkt_name\": \"ccdp-tasks\",\"keep_files\": \"False\",\"mod_name\": \"simple_pi\",\"verb_level\": \"debug\",\"res_file\": \"pi_out\",\"zip_file\": \"simple_pi.zip\"}"

    '''
    client = boto3.client('apigateway')
    response = client.test_invoke_method(
    restApiId='cx62aa0x70',
    resourceId='3jpl8x',
    httpMethod='POST',
    pathWithQueryString='string',
    body='{"arguments": "10000","bkt_name": "ccdp-tasks","keep_files": "False","mod_name": "simple_pi","verb_level": "debug","res_file": "pi_out","zip_file": "simple_pi.zip"}',
    )
    
    self.__logger.info("Status: %d ==> Result: %s" % (response['status'], response['body']))


        
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
      