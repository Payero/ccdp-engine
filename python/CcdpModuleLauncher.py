#!/usr/bin/env python
# encoding: utf-8
from __future__ import print_function

import boto3, botocore
import json

from optparse import OptionParser, Option, OptionValueError
from copy import copy

import logging
from pprint import pformat, pprint
import os, sys, traceback
import tarfile
from subprocess import call
import shutil, ast


def check_ccdp_args(option, opt, value):
  '''
  Generates a type of argument passed to the module launcher.  It returns a 
  value as follow:
    - If the arguments is not a comma delimited list, it returns the value
    - if the list contains the same number of '=' as the number of items, it
      returns a dictionary using the key=val items
    - If none of the items have an '=', it returns a list
    - else it throws an error
  '''
  try:
    
    vals = value.split(',')
    # if is a single value, return it
    if len(vals) == 1:
      return value
    else:
      # do we have the same number of = as the number of entries? (dict a=b)
      if len(vals) == value.count('='):
        my_dict = {}
        for val in vals:
          k, v = val.split('=')
          my_dict[k] = v
        return my_dict
      else:
        if value.count('=') == 0:
          return vals
        else:
          msg = "option %s: invalid ccd-argument: %r." % (opt, value)
          msg += " cannot have list with '=' (reserved for dict)"
          raise OptionValueError(msg)

  except ValueError:
    raise OptionValueError(
          "option %s: invalid ccd-argument: %r" % (opt, value))

class CcdpArgOption (Option):
  '''
  Simple class used to define my own argument type for the optparse
  '''
  TYPES = Option.TYPES + ("ccdp_args",)
  TYPE_CHECKER = copy(Option.TYPE_CHECKER)
  TYPE_CHECKER["ccdp_args"] = check_ccdp_args


class ModuleRunner:
  """
  Runs a task from a bucket or a file?????
  What I need to know:

  The arguments passed to the handler function is (dictionary, LambdaContext)

  """
  __LEVELS = {"debug": logging.DEBUG, 
              "info": logging.INFO, 
              "warning": logging.WARN,
              "error": logging.ERROR}
  
  __CCDP_BKT = 'ccdp-tasks'
  __CCDP_FMWK = 'ccdp-mod-fmwk.zip'
  
  def __init__(self, cli_args):
    self.__logger = logging.getLogger('ModuleRunner')
    handler = logging.StreamHandler()
    logs_dir = None

    if os.environ.has_key('CCDP_GUI'):
      tmp_dir = os.path.join(os.environ['CCDP_GUI'], "logs")
      if os.path.isdir(tmp_dir):
        logs_dir = tmp_dir
    elif os.environ.has_key('CCDP_HOME'):
      tmp_dir = os.path.join(os.environ['CCDP_HOME'], "logs")
      if os.path.isdir(tmp_dir):
        logs_dir = tmp_dir

    if logs_dir == None:
      logs_dir = '/tmp'

    filelog = logging.FileHandler('%s/module_runner.log' % logs_dir)

    formatter = logging.Formatter(
            '%(asctime)s %(name)-12s %(lineno)d %(levelname)-8s %(message)s')
    handler.setFormatter(formatter)
    filelog.setFormatter(formatter)

    self.__logger.addHandler(handler)
    self.__logger.addHandler(filelog)

    self.__logger.info("Saving log file in %s" % logs_dir)
    
    # Setting root level to warning and THEN set the level for this module
    self.__logger.setLevel(self.__LEVELS['warning'])
    logging.getLogger('ModuleRunner').setLevel(self.__LEVELS[cli_args['verb_level']])
    
    self.__logger.debug("Logging Done")

    self.__s3 = boto3.resource('s3')
    self.__get_ccdp_gui_fmwk()


    if cli_args['file_name'] is None:
      self.__logger.debug('Using a bucket rather than a file')
      self.__runS3Task(cli_args)
    else:
      self.__logger.debug('Using a file rather than an S3 bucket')
      self.__runFileTask(cli_args)


  def __runFileTask(self, params):
      """
      Runs a local module or file that complies with the CcdpModule 
      specifications.

      """
      self.__logger.debug("Running from file using %s" % pformat(params) )

      file_name = os.path.expandvars(params['file_name'])
      class_name = params['class_name']

      if file_name == None:
        self.__logger.error("The file name needs to be provided")
        sys.exit(-1)

      if not os.path.isfile(file_name):
        self.__logger.error("The file %s was not found " % file_name)
        sys.exit(-3)
      

      self.__logger.info("Running the Task from %s" % class_name)

      path, name = os.path.split(file_name)
      name, ext = os.path.splitext(name)
      self.__logger.info("Importing %s from %s" % (class_name, name))
      sys.path.append(path)
      sys.path.append(file_name)
      exec( "from %s import %s" % (name, class_name))

      args = ""
      if params.has_key('arguments'):
        args = params['arguments']

      self.__logger.info("Arguments: %s" % str(args))

      clazz = self.__get_class(name, class_name)
      #self.__logger.debug("The Arguments ", ast.literal_eval(args) )

      if args != None:
        clazz(args)
      else:
        #eval("%s()" % class_name )
        clazz()


  def __get_class(self, module_name, class_name):
    # load the module, will raise ImportError if module cannot be loaded
    m = __import__(module_name, globals(), locals(), class_name)
    # get the class, will raise AttributeError if class cannot be found
    c = getattr(m, class_name)
    return c

  
  def __get_ccdp_gui_fmwk(self):
    '''
    Adds the source directory to the system path if is found.  If is not found
    then it attempt to get the zipped version of the modules framework from 
    an AWS S3 bucket
    '''
    self.__logger.info("Looking for the GUI framework")
    if os.environ.has_key('CCDP_GUI'):
      src_dir = os.path.join(os.environ['CCDP_GUI'], 'src')
      if os.path.isdir(src_dir):
        sys.path.append( src_dir )
      else:
        txt = 'The path %s ' % src_dir 
        txt += ' is invalid, please make sure the $CCDP_GUI/src is valid'
        self.__logger.error(txt)
    else:
      self.__logger.info('CCDP_GUI env. var. is not set, getting it from AWS')
      bkt_name = self.__CCDP_BKT
      zip_fmwk  = self.__CCDP_FMWK

      bkt = self.__s3.Bucket(bkt_name)
    
      _root = "/tmp"
    
      self.__logger.debug("The name of the element: %s" % zip_fmwk)

      self.__logger.debug("Downloading zipped file ")
      fpath = os.path.join(_root, zip_fmwk)
      self.__logger.debug("Saving file in %s" % fpath)
      bkt.download_file(zip_fmwk, fpath)

      if not os.path.isfile(fpath):
        self.__logger.error("The zip file was not found ")
        sys.exit(-3)

      sys.path.append(fpath)



  def __runS3Task(self, params):
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
    sys.path.append(fpath)
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
          res = runTask(ast.literal_eval(args))
        else:
          res = runTask()
      
      self.__load_file(bkt_name, out)
    else:
      if args != None:
          res = runTask(ast.literal_eval(args))
      else:
        res = runTask()
    
    res_file = None
    if params.has_key('res_file'):
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
            description=desc,
            option_class=CcdpArgOption)
  
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


  parser.add_option("-f", "--file-name",
            dest="file_name",
            default=None,
            help="The name of the bucket containing zip file with runTask")

  
  parser.add_option("-c", "--class-name",
            dest="class_name",
            help="The name of the class to run")

  parser.add_option("-a", "--arguments",
            dest="arguments",
            type="ccdp_args", 
            default=None,
            help="The arguments to provide to the runModule method ")

  (options, args) = parser.parse_args()
  # it expects a dictionary 
  opts = vars(options)
  
  ModuleRunner(opts) 

