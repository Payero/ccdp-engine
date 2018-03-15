#!/usr/bin/env python
# encoding: utf-8
from __future__ import print_function

import boto3, botocore
import json

import subprocess

from optparse import OptionParser, Option, OptionValueError
from copy import copy

import logging
from pprint import pformat, pprint
import os, sys, traceback
import tarfile
from subprocess import call
import shutil, ast, urllib

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

    pprint(args)
    ModuleRunner(args)
    # return "%s" % runner.runTask(args['ver'])

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
    dec_str = urllib.base64.standard_b64decode( value )
    return ast.literal_eval( dec_str )
  except:
    print("Argument %s is not base64 encoded, using custom" % value)

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
    
    # Setting root level to warning and THEN setting the level for this module
    self.__logger.setLevel(self.__LEVELS['warning']) #TODO this seems supergluous
    logging.getLogger('ModuleRunner').setLevel(self.__LEVELS[cli_args['verb_level']])
    
    self.__logger.debug("Logging setup Done")

    self.__s3 = boto3.resource('s3')
    self.__files = []
    self.__get_ccdp_gui_fmwk()

    self.__logger.info("Running with arguments: %s" % pformat(cli_args))
    
    if cli_args['file_name'] is None:
      self.__logger.error("Neither a file name nor S3 info (bucket, module, and zip file name) were provided")
      sys.exit(-3)
    if os.path.isfile(cli_args['file_name']):
      self.__logger.debug('Using a file rather than an S3 bucket')
      self.__runFileTask(cli_args)
    else:
      self.__logger.debug('The file does not exist locally, so it will be downloaded from an S3 bucket')
      self.__runS3Task(cli_args)

  def __runFileTask(self, params):
      """
      Runs a local module or file that complies with the CcdpModule 
      specifications.

      """
      
      file_name = os.path.expandvars(params['file_name'])
      self.__logger.debug("Running from file %s" % file_name)
      if params.has_key('arguments'):
        self.__logger.debug("The args type: %s" % type(params['arguments']))
      
      path, _name = os.path.split(file_name)
      name, ext = os.path.splitext(_name)
      sys.path.append(path)
      
      mod_name = None
      if file_name.endswith('.zip'):
        if not params.has_key('mod_name') or params['mod_name'] is None:
          self.__logger.error('A zip file was provided, but the module to run from that zip file was not specified')
          sys.exit(-3)
        else:
          sys.path.append(file_name)
          mod_name = params['mod_name']
      if mod_name is None:
        self.__logger.info("Running the Task from file %s" % _name)
        self.__logger.debug("the file name is " + name)
        exec("import %s" % name)
      else:
        self.__logger.info("Running the Task from module %s" % mod_name)
        exec("import %s" % mod_name)
  
      self.res = None
      args = None
      if params.has_key('arguments') and params['arguments'] is not None:
        args = params['arguments']
  
      self.invoke_module(name if mod_name is None else mod_name, params, args)
  
      # returning the results in case is from the lambda function
      return self.res

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
    #TODO what is the purpose of including the GUI environment?
    self.__logger.info("Looking for the GUI framework")
    if os.environ.has_key('CCDP_GUI'):
      src_dir = os.path.join(os.environ['CCDP_GUI'], 'src')
      if os.path.isdir(src_dir):
        sys.path.append( src_dir )
        self.__logger.info("GUI framework found")
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
    
      self.__logger.debug("Downloading %s" % zip_fmwk)

      fpath = os.path.join(_root, zip_fmwk)
      self.__logger.debug("Saving framework as %s" % fpath)
      bkt.download_file(zip_fmwk, fpath)

      if not os.path.isfile(fpath):
        self.__logger.error("Failed to download the framework file")
        #TODO we don't need to exit here, do we? We don't exit at line 202 when $CCDP_GUI/src is an invalid path
        sys.exit(-3)

      sys.path.append(fpath)



  def __runS3Task(self, params):
    """
    Gets the ccdp-dist.tar.gz and the ccdp_mesos_settings.json from the CCDP
    Settings bucket.  These files are used to install CCDP
    """
    if params.has_key('arguments') and params['arguments'] is not None:
      self.__logger.debug("The args type: %s" % type(params['arguments']))
    
    name = params['file_name'].split(":")
    if len(name) == 2:
      bkt_name = name[0]
      file_name = name[1]
    else:
      self.__logger.error("The file name is not in the form <bucket_name>:<file_name>, so it cannot be found.")
      sys.exit(-3)

    bkt = self.__s3.Bucket(bkt_name)
    
    _root = "/tmp"
    
    self.__logger.debug("Downloading file %s from bucket %s" % (file_name, bkt_name))
    fpath = os.path.join(_root, file_name)
    self.__logger.debug("Saving file in %s" % fpath)
    bkt.download_file(file_name, fpath)


    if not os.path.isfile(fpath):
      self.__logger.error("The file download failed")
      sys.exit(-3)
    
    self.__logger.error("File download completed")
    self.__files.append(fpath)
    
    mod_name = None
    if file_name.endswith('.zip'):
      if not params.has_key('mod_name') or params['mod_name'] is None:
        self.__logger.error('A zip file was provided, but the module to run from that zip file was not specified')
        sys.exit(-3)
      else:
        mod_name = params['mod_name']
        sys.path.append(fpath)
    else:
      sys.path.append(_root)
    sys.path.append(fpath)
    if mod_name is None:
      self.__logger.info("Running the Task from file %s" % file_name)
      exec("import %s" % file_name.replace('.py', ''))
    else:
      self.__logger.info("Running the Task from module %s" % mod_name)
      exec("import %s" % mod_name)

    self.res = None
    args = None
    if params.has_key('arguments') and params['arguments'] is not None:
      args = params['arguments']

    if params.has_key('out_file') and params['out_file'] is not None:
      out = os.path.join(_root, params['out_file'])
      self.__logger.debug(("Redirecting to %s" % out))
      with RedirectStdStreams(stdout=out, stderr=out):
        self.invoke_module(file_name.replace('.py', '') if mod_name is None else mod_name, params, args)
      self.__load_file(bkt_name, out)
    else:
      self.invoke_module(file_name.replace('.py', '') if mod_name is None else mod_name, params, args)
    
    res_file = None
    if params.has_key('res_file') and params['res_file'] is not None:
      res_file = os.path.join(_root, params['res_file'])
      self.__logger.info("Output (from lambda module only) will be written to %s" % res_file)
    
    
    if res_file != None and self.res != None:
      self.__logger.info("Writing results: %s" % self.res)
      results = open(res_file, 'w')
      results.write("%s" % self.res)
      results.flush()
      results.close()
      self.__load_file(bkt_name, res_file)

    if not params.has_key('keep_files'):
      self.__clean_files()

    # returning the results in case is from the lambda function
    return self.res
  #copied
  
  def invoke_module(self, mod_name, params, args):
    if not params.has_key('class_name') or params['class_name'] is None:
      try:
        exec("from %s import runTask" % mod_name.replace('.py', ''))
        self.__logger.debug('Imported runTask from lambda module %s' % mod_name)
        if args != None:
          self.res = runTask(ast.literal_eval(args))
        else:
          self.res = runTask()
      except Exception as e:
        self.__logger.debug('The module is not a lambda, and no class was specified. It will be invoked as a script.')
        if args != None:
          sp_args = [mod_name];
          sp_args.extend(args)
          p = subprocess.Popen(args=sp_args, executable="python")
          p.wait()
        else:
          sp_args = [mod_name];
          p = subprocess.Popen(args=sp_args, executable="python")
          p.wait()
    else:
      self.__logger.debug("Invoking class %s from the module." % params['class_name'])
      clazz = self.__get_class(mod_name, params['class_name'])
      if args is None:
        clazz()
      else:
        clazz(args)


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
  
"""
  Runs the application by instantiating a new object and passing all the
  command line arguments
"""  
if __name__ == '__main__':
    
  desc = "Runs a task from a python file. The file may exist on the file\n"
  desc += " system or in an S3 bucket. The file may be a python file or a zip\n"
  desc += " archive containing a python file. The module may be invoked as a lambda\n"
  desc += " function with a runTask method, a python script, or a specified clas"
  parser = OptionParser(usage="usage: %prog [options] args",
            version="%prog 1.0",
            description=desc,
            option_class=CcdpArgOption)
  
  parser.add_option("-f", "--file-name",
    dest="file_name",
    default=None,
    help= "The name of the file containing the module. If the file is not found on\n"
        + " the file system, it will be interpreted as an AWS S3 file in the form\n"
        + " <bucket_name>:<file_name>. The file must be a python file or a zip archive\n"
        + " of python files. If a zip archive, use -m to identify the python module file.")
   
  parser.add_option("-m", "--module",
    dest= "mod_name",
    help= "The name of the module within a zip archive, i.e. the python file name without\n"
        + " the '.py' extension. Ignored if -f does not point to a zip file.")
  
  parser.add_option("-c", "--class-name",
    dest="class_name",
    help= "The name of the class to invoke. If not set, and the module is a lambda\n"
        + " module, the runTask function will be invoked. Otherwise the module will\n"
        + " will be invoked a script file.")
  
  parser.add_option("-r", "--results",
    dest="res_file",
    help= "The name of the file to store the module's results on the S3 bucket. Used only for\n"
        + " lambda modules run from an S3 bucket")

  parser.add_option("-o", "--output",
    dest="out_file",
    help= "The name of the file to store the output from stdout and stderr on the S3 bucket.\n"
        + " Used only for lambda modules run from an S3 bucket.")

  parser.add_option("-k", "--keep-files",
    action="store_true",
    dest="keep_files",
    default=False,
    help= "If set, the files created in accordance with the -r and -o options will not be\n"
         +  "deleted from the local file system after upload to the S3 bucket.")

  parser.add_option("-a", "--arguments",
    dest="arguments",
    type="ccdp_args", 
    default=None,
    help="The arguments to provide when invoking the module.")
  
  parser.add_option('-v', '--verbosity-level',
    type='choice',
    action='store',
    dest='verb_level',
    choices=['debug', 'info', 'warning','error',],
    default='debug',
    help='The verbosity level of the logging.')

  (options, args) = parser.parse_args()
  # it expects a dictionary 
  opts = vars(options)
  
  ModuleRunner(opts) 

