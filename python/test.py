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
import urllib

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
    is_pi = True

    if is_pi:
      args = urllib.base64.standard_b64encode("10000")
      data = {'arguments': 1000000, 'bkt_name':'ccdp-tasks', 'keep_files':False, 'mod_name':'simple_pi', 'verb_level':'debug', 'zip_file':'simple_pi.zip'}

      self.__logger.debug("The body of the message: %s" % pformat(data) )
      
      body = urllib.base64.standard_b64encode( str(data) )

      self.__logger.debug("The body of the message encoded: %s" % body )

      response = client.test_invoke_method(
      restApiId='cx62aa0x70',
      resourceId='3jpl8x',
      httpMethod='POST',
      pathWithQueryString='string',
      body="\"%s\"" % str(body),
      )
    else:
      args = {"column-number":8, "operator": "GE", "value":1, "record":"1,1929,1 - Junior,50,Systems,Issue,2 - Normal,0 - Unassigned,3,1 - Unsatisfied"}
      data = {'arguments': args, 'bkt_name':'ccdp-tasks', 'keep_files':False, 'mod_name':'csv_selector_lambda', 'verb_level':'debug', 'zip_file':'csv_selector_lambda.py'}

      body = urllib.base64.standard_b64encode( str(data) )
      
      response = client.test_invoke_method(
      restApiId='cx62aa0x70',
      resourceId='3jpl8x',
      httpMethod='POST',
      pathWithQueryString='string',
      body="\"%s\"" % str(body),
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
      