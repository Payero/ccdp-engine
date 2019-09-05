#!/usr/bin/env python
# encoding: utf-8
from __future__ import print_function

import boto3, botocore
import json

from optparse import OptionParser
import logging
from pprint import pformat, pprint
import os, sys, traceback
import tarfile
from subprocess import call
import shutil, ast, urllib

class TaskRunner:
  """
  Runs a task from a bucket 
  What I need to know:

  The arguments passed to the handler function is (dictionary, LambdaContext)

  The Role:
    IAM > Roles > ApiGwy-Lambda
      arn:aws:iam::451796069025:role/ApiGwy-Lambda

  The command I used to create the lambda:
    aws lambda create-function \
    --region us-east-1 \
    --function-name CcdpLambdaTaskRunner \
    --code S3Bucket=ccdp-tasks,S3Key=CcdpLambdaTaskRunner.zip \
    --role arn:aws:iam::451796069025:role/ApiGwy-Lambda \
    --handler CcdpLambdaTaskRunner.handler \
    --runtime python2.7 \
    --profile default

  Another option rather than using buckets is to replace
        --code S3Bucket=ccdp-tasks,S3Key=CcdpLambdaTaskRunner.zip \
  with
        --zip-file fileb://file-path/CcdpLambdaTaskRunner.zip \

  The command to invoke it:
  aws lambda  invoke \
  --invocation-type Event \
  --function-name CcdpLambdaTaskRunner \
  --region us-east-1 \
  --payload file:///home/oeg/lambda_inputs.json \
  --profile default \
  outputfile.txt
  
  Create an API Gwy 
    aws apigateway create-rest-api --name PythonTaskRunner
    Response:
      {
        "name": "PythonTaskRunner", 
        "id": "cx62aa0x70", 
        "createdDate": 1495579921
      }
    
    I also need the ID of the API root resource. To get the ID, run the 
    get-resources command
      aws apigateway get-resources --rest-api cx62aa0x70

    Response:
    {
    "items": [
        {
            "path": "/", 
            "id": "ikue32mnp6"
        }
      ]
    }

    Create a resource:
      aws apigateway create-resource \
      --rest-api-id cx62aa0x70 \
      --parent-id ikue32mnp6 \
      --path-part TaskRunnerManager
    Response
      {
      "path": "/TaskRunnerManager", 
      "pathPart": "TaskRunnerManager", 
      "id": "3jpl8x", 
      "parentId": "ikue32mnp6"
      }
    
    Create Method (POST) on the Resource
      aws apigateway put-method \
      --rest-api-id cx62aa0x70 \
      --resource-id 3jpl8x \
      --http-method POST \
      --authorization-type NONE
    
    Response:
    {
    "apiKeyRequired": false, 
    "httpMethod": "POST", 
    "authorizationType": "NONE"
    }

    Set the lambda Function as the Destination for the POST method
      aws apigateway put-integration \
      --rest-api-id cx62aa0x70 \
      --resource-id 3jpl8x \
      --http-method POST \
      --type AWS \
      --integration-http-method POST \
      --uri arn:aws:apigateway:us-east-1:lambda:path/2015-03-31/functions/arn:aws:lambda:us-east-1:451796069025:function:CcdpLambdaTaskRunner/invocations
    
    Response
    {
    "httpMethod": "POST", 
    "passthroughBehavior": "WHEN_NO_MATCH", 
    "cacheKeyParameters": [], 
    "type": "AWS", 
    "uri": "arn:aws:apigateway:us-east-1:lambda:path/2015-03-31/functions/arn:aws:lambda:us-east-1:451796069025:function:CcdpLambdaTaskRunner/invocations", 
    "cacheNamespace": "3jpl8x"
    }

    Set content-type of the POST method response and integration response to 
    JSON as follows:
    aws apigateway put-method-response \
    --rest-api-id cx62aa0x70 \
    --resource-id 3jpl8x \
    --http-method POST \
    --status-code 200 \
    --response-models "{\"application/json\": \"Empty\"}"
    
    This is the response type that your API method returns.

    Response:
    {
    "responseModels": {
        "application/json": "Empty"
      }, 
    "statusCode": "200"
    }

    Set the POST method integration response to JSON. This is the response 
    type that Lambda function returns.
      aws apigateway put-integration-response \
      --rest-api-id cx62aa0x70 \
      --resource-id 3jpl8x \
      --http-method POST \
      --status-code 200 \
      --response-templates "{\"application/json\": \"\"}"

    Response:
    {
    "statusCode": "200", 
    "responseTemplates": {
        "application/json": null
      }
    }

    Deploy the API in a stage called prod
      aws apigateway create-deployment --rest-api-id cx62aa0x70 --stage-name prod

    Response:
    {
    "id": "gnottv", 
    "createdDate": 1495584245
    }

    Grant Permissions that Allows Amazon API Gateway to Invoke the Lambda Function
    aws lambda add-permission \
    --function-name CcdpLambdaTaskRunner \
    --statement-id apigateway-test-2 \
    --action lambda:InvokeFunction \
    --principal apigateway.amazonaws.com \
    --source-arn "arn:aws:execute-api:us-east-1:451796069025:cx62aa0x70/*/POST/TaskRunnerManager"
  
    Response:
    {
    "Statement": "{\"Sid\":\"apigateway-test-2\",\"Resource\":\"arn:aws:lambda:us-east-1:451796069025:function:CcdpLambdaTaskRunner\",\"Effect\":\"Allow\",\"Principal\":{\"Service\":\"apigateway.amazonaws.com\"},\"Action\":[\"lambda:InvokeFunction\"],\"Condition\":{\"ArnLike\":{\"AWS:SourceArn\":\"arn:aws:execute-api:us-east-1:451796069025:cx62aa0x70/*/POST/TaskRunnerManager\"}}}"
    }

    Test Sending an HTTPS Request
    $ aws apigateway test-invoke-method \
    --rest-api-id cx62aa0x70 \
    --resource-id 3jpl8x \
    --http-method POST \
    --path-with-query-string "" \
    --body "{\"arguments\": \"1000000\",\"bkt_name\": \"ccdp-tasks\",\"keep_files\": \"False\",\"mod_name\": \"simple_pi\",\"verb_level\": \"debug\",\"res_file\": \"pi_out\",\"zip_file\": \"simple_pi.zip\"}"

    or I can also use
    curl -X POST -d "{\"arguments\": \"1000000\",\"bkt_name\": \"ccdp-tasks\",\"keep_files\": \"False\",\"mod_name\": \"simple_pi\",\"verb_level\": \"debug\",\"res_file\": \"pi_out\",\"zip_file\": \"simple_pi.zip\"}" https://cx62aa0x70.execute-api.us-east-1.amazonaws.com/prod/TaskRunnerManager



  """
  __LEVELS = {"debug": logging.DEBUG, 
            "info": logging.INFO, 
            "warning": logging.WARN,
            "error": logging.ERROR}
  
  def __init__(self, verb_level):
    self.__logger = logging.getLogger('TaskRunner')
    handler = logging.StreamHandler()
    filelog = logging.FileHandler('/tmp/task_runner.log')

    formatter = logging.Formatter(
            '%(asctime)s %(name)-12s %(lineno)d %(levelname)-8s %(message)s')
    handler.setFormatter(formatter)
    filelog.setFormatter(formatter)

    self.__logger.addHandler(handler)
    self.__logger.addHandler(filelog)
    
    # Setting root level to warning and THEN set the level for this module
    self.__logger.setLevel(self.__LEVELS['warning'])
    logging.getLogger('TaskRunner').setLevel(self.__LEVELS[verb_level])
    
    self.__logger.debug("Logging Done")

    self.__s3 = boto3.resource('s3')
    self.__files = []


  def runTask(self, params):
    """
    Gets the ccdp-dist.tar.gz and the ccdp_mesos_settings.json from the CCDP
    Settings bucket.  These files are used to install CCDP
    """
    self.__logger.debug("Performing Download using %s" % pformat(params))
    self.__logger.debug("The Type: %s" % type(params))
    
    
    bkt_name = params['bkt_name']
    zip_mod  = params['zip_file']
    mod_name = params['mod_name']

    msg = None
    if zip_mod == None:
      msg = "The zipped module name needs to be provided"
    bkt = self.__s3.Bucket(bkt_name)
    
    _root = "/tmp"
    
    self.__logger.debug("The name of the element: %s" % zip_mod)

    self.__logger.debug("Downloading zipped file ")
    fpath = os.path.join(_root, zip_mod)
    self.__logger.debug("Saving file in %s" % fpath)
    bkt.download_file(zip_mod, fpath)


    if not os.path.isfile(fpath):
      self.__logger.error("The zip file was not found ")
      sys.exit(-3)
    
    self.__files.append(fpath)

    self.__logger.info("Running the Task from %s" % mod_name)
    # need to add both the directory and the file to support either a 
    # zip file and/or a python module
    sys.path.append(fpath)
    sys.path.append(_root)
    exec("from %s import runTask" % mod_name)

    res = None
    args = ""
    if params.has_key('arguments'):
      args = params['arguments']
    
    self.__logger.info("Using Arguments: %s" % args)

    if params.has_key('out_file') and params['out_file'] != None:
      out = os.path.join(_root, params['out_file'])
      print("Redirecting to %s" % out)
      with RedirectStdStreams(stdout=out, stderr=out):
        if args != None:
          try:
            # if is not a string, then evaluate it
            res = runTask(ast.literal_eval(args))
          except ValueError:
            # otherwise just pass it
            res = runTask(args)
        else:
          res = runTask()
      
      self.__load_file(bkt_name, out)
    else:
      if args != None:
        try:
          # if is not a string, then evaluate it
          res = runTask(ast.literal_eval(args))
        except ValueError:
          # otherwise just pass it
          res = runTask(args)
                
    res_file = None
    if params.has_key('res_file') and params['res_file'] != None:
      res_file = os.path.join(_root, params['res_file'])
    
    
    if res_file != None and res != None:
      self.__logger.info("Writing %s" % res)
      results = open(res_file, 'w')
      results.write("%s" % res)
      results.flush()
      results.close()
      self.__load_file(bkt_name, res_file)

    if params.has_key('keep_files') and not params['keep_files']:
      self.__clean_files()

    # returning the results in case is from the lambda function
    return res

  def __load_file(self, path, fname):
    '''
    Loads the file to the bucket specified in the path and is saved as 'fname'
    '''
    src_path, name = os.path.split(fname)
    self.__logger.info("Loading file %s to bucket %s" % (fname, path) )
    self.__s3.Object(path, name).put(Body=open(fname, 'rb'))
    self.__logger.debug("File %s was uploaded successfully" % fname)
    self.__files.append(fname)


  def __clean_files(self):
    '''
    As the files are being generated they are stored in a list.  All the files
    stored in the list are removed at the end unless the keep_files flag is set
    '''
    self.__logger.info("Removing files")
    for f in self.__files:
      self.__logger.info("Removing %s" % f)
      os.remove(f)


class RedirectStdStreams(object):
  '''
  Class used to redirect the stdout and stderr to a file that can be then 
  uploaded into the same S3 bucket 
  '''
  def __init__(self, stdout=None, stderr=None):
    if stdout != None:
      stdObj = open(stdout, 'w')
      self._stdout = stdObj
    else:
      self._stdout = sys.stdout

    if stderr != None:
      steObj = open(stderr, 'w')
      self._stderr = steObj
    else:
      self._stderr = sys.stderr

  def __enter__(self):
    self.old_stdout, self.old_stderr = sys.stdout, sys.stderr
    self.old_stdout.flush(); self.old_stderr.flush()
    sys.stdout, sys.stderr = self._stdout, self._stderr

  def __exit__(self, exc_type, exc_value, traceback):
    self._stdout.flush(); self._stderr.flush()
    sys.stdout = self.old_stdout
    sys.stderr = self.old_stderr

        



def handler(event, context):
    '''
    Allows to run a task from a module on a S3 Bucket.  The arguments passed 
    as an event.  The is the help message printed when passing the -h option:
    
    Runs a task contained on a bucket.  The bucket must have  a module with the
    runTask method defined.  The name of  that module is one of the required
    arguments.

  Options:
    --version             show program's version number and exit
    -h, --help            show this help message and exit
    -v VERB_LEVEL, --verbosity-level=VERB_LEVEL
                          The verbosity level of the logging
    -b BKT_NAME, --s3-bucket=BKT_NAME
                          The name of the bucket containing zip file with
                          runTask
    -z ZIP_FILE, --zipped-module=ZIP_FILE
                          The name of the zip file containing the module
    -m MOD_NAME, --module=MOD_NAME
                          The name of the module to run
    -r RES_FILE, --results=RES_FILE
                          The name of the file to store resutls from the runTask
                          call
    -o OUT_FILE, --output=OUT_FILE
                          The name of the file to store the stdout and stderr
    -k, --keep-files      It does not delete any file after execution
    -a ARGUMENTS, --arguments=ARGUMENTS
                          The arguments to provide to the runTask method

    '''
    #print("Received event: " + json.dumps(event, indent=2))
    #print("Context: %s" % str(type(context)))
    #pprint(dir(context))
    #args = urllib.base64.standard_b64decode( event )
    args = ast.literal_eval(json.dumps(event))

    runner = TaskRunner(args['verb_level'])
    return "%s" % runner.runTask(args)



"""
  Runs the application by instantiating a new object and passing all the
  command line arguments
"""  
if __name__ == '__main__':
    
  desc = "Runs a task contained on a bucket.  The bucket must have \n"
  desc += "a module with the runTask method defined.  The name of \n"
  desc += "that module is one of the required arguments."
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
    
  parser.add_option("-b", "--s3-bucket",
            dest="bkt_name",
            default='ccdp-tasks',
            help="The name of the bucket containing zip file with runTask")
  
  parser.add_option("-z", "--zipped-module",
            dest="zip_file",
            help="The name of the zip file containing the module")

  parser.add_option("-m", "--module",
            dest="mod_name",
            help="The name of the module to run")

  parser.add_option("-r", "--results",
            dest="res_file",
            help="The name of the file to store resutls from the runTask call")

  parser.add_option("-o", "--output",
            dest="out_file",
            help="The name of the file to store the stdout and stderr")

  parser.add_option("-k", "--keep-files",
            action="store_true", dest="keep_files", default=False,
            help="It does not delete any file after execution")

  parser.add_option("-a", "--arguments",
            dest="arguments", 
            default=None,
            help="The arguments to provide to the runTask method")

  (options, args) = parser.parse_args()
  # it expects a dictionary 
  opts = vars(options)
  print("Running using %s" % pformat(opts))
  runner = TaskRunner( opts['verb_level'] )
  print( "The Results: %s" % runner.runTask(opts) ) 

