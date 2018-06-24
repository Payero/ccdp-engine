#!/usr/bin/env python

'''
Created on Mar 1, 2017

@author: oeg
'''
from optparse import OptionParser
import logging
from pprint import pformat
import inspect
import ast
import threading
import signal
import time
import random

class Test():
  """
  Simple class containing different types of tests that can be launched.  This
  is intended to be used to simulate tasks running in a Mesos Agent.  So far 
  these are the methods or tests to run:

    - testHellp:  Just prints the name passed as argument
    - testCpuUsage:    Forces the CPU to 100% utilization
    - testNeverEndingRun: Runs a never ending loop
    - testRandomTime: Runs for a time between min and max and quits
    - testSignalTerm:  Runs a loop until a SIGTERM is passed
    
  """
  __LEVELS = {"debug": logging.DEBUG, 
              "info": logging.INFO, 
              "warning": logging.WARN,
              "error": logging.ERROR}

  def __init__(self, cli_args):
    
    self.__logger = logging.getLogger()
    handler = logging.StreamHandler()
    formatter = logging.Formatter(
            '%(asctime)s %(module)-12s %(lineno)d %(levelname)-8s %(message)s')
    handler.setFormatter(formatter)
    self.__logger.addHandler(handler)
    self.__logger.setLevel(self.__LEVELS[cli_args.verb_level])
    self.__logger.debug("Logging Done")
    self.__event = threading.Event()
    
    # Generating all the test methods as dictionary
    my_actions = inspect.getmembers(self, predicate=inspect.ismethod)
    todo = {}
     
    for m in my_actions:
      name = m[0]
      if name.startswith('test'):
        actions.append(name)
        self.__logger.debug("Adding %s" % name)
        todo[name] = m[1]

    action = cli_args.action
    
    # Was the action valid?
    if action is not None and todo.has_key(action):
      self.__logger.debug("About to run test %s" % action)
      test = todo[action]
      # getting all the parameters, if have some pass them otherwise skip it
      params = cli_args.params
      
      if params is not None:
        self.__logger.debug("Passing %s " % params)
        test(params)
      else:
        test()
      
    else:
      self.__logger.info("Skipping Running Test")

  
  def testHello(self, name):
    """
      Prints the name passed as arguments and quits
    """
    self.__logger.debug("Hello %s, from CCDP" % name )


  def testCpuUsage(self, seconds):
    """
      Runs a test that forces the CPU to 100% utilization rate for a timed time
    """
    self.__logger.debug("Running a cpu Test")
    
    # It just performs a dumb multiplication
    def runTest():
      self.__logger.debug("Running CPU Test")
      x = 0
      while not self.__event.isSet():
        x * x 
    
    thread = threading.Thread(target=runTest, args=())
    thread.daemon = True
    thread.start()
    time.sleep(float(seconds))
    self.__logger.debug("Done running test")
    self.__setEvent()
        
        
  def testMemUsage(self, params="megabytes=512,secs=10"):
    """
      Runs a test that forces the system to consume a given number of megabytes
      for a number of seconds
    """
    self.__logger.debug("Running a mem Test")
    
    megabytes=512
    secs=10

    vals = params.split(',')
    for item in vals:
      val = item.split('=')
      if val[0] == 'megabytes':
        megabytes = int(val[1])
      if val[0] == 'secs':
        secs = int(val[1])

    self.__logger.debug("Consuming %d MB for %d seconds" % (megabytes,secs))

    # It just performs a dumb data storage
    def runTest():
      self.__logger.debug("Running Mem Test")
      a = []
      while not self.__event.isSet():
        if len(a) >= megabytes:
          time.sleep(0.1)
        else:
          a.append(' ' * 10**6 )

    thread = threading.Thread(target=runTest, args=())
    thread.daemon = True
    thread.start()
    time.sleep(float(secs))
    

    self.__logger.debug("Done running test")
    self.__setEvent()


  def testRandomTime(self, params="min=0,max=10"):
    """
    Gets a random time between min and max and sleeps for that time.  The 
    parameters should be a key=value comma delimited set (min=1,max=2).  If one
    of them is missing then it uses a default value (min = 0, max = 10)
    """            
    random.seed(time.time())
    #args = ast.literal_eval(params)
    min_val = 0
    max_val = 10
    
    vals = params.split(',')
    for item in vals:
      val = item.split('=')
      if val[0] == 'min':
        min_val = int(val[1])
      if val[0] == 'max':
        max_val = int(val[1])
    
    wait = random.randint(min_val, max_val)
    self.__logger.debug("Waiting for %d seconds" % wait)
    time.sleep(wait)
    
    
  def testNeverEndingRun(self):
    """
    Runs a test that never ends and therefore needs to be killed
    """
    self.__logger.debug("Running a never ending test")
    while not self.__event.isSet():
      self.__logger.debug("Wasting time and resources")
      time.sleep(2)
  
    
  def testSignalTerm(self):
    """
    Handles a signal termination interrupt.  It runs until it receives either 
    a SIGTERM or a SIGINT interrupt
    """
    self.__logger.debug("Running a test that gets a signal")

    def stopTest(signum, frame):
      self.__logger.debug("Got a Termination Signal %d" % signum)
      self.__setEvent()
      
    signal.signal(signal.SIGINT, stopTest)
    signal.signal(signal.SIGTERM, stopTest)
    
    while not self.__event.isSet():
      self.__logger.debug("Running a cycle")
      time.sleep(1)


  def __setEvent(self):
    """
    Sets an internal object to terminate a while loop
    """
    self.__logger.info("Setting the Event")
    self.__event.set()
    
"""
  Runs the application by instantiating a new Test object and passing all the
  command line arguments
"""  
if __name__ == '__main__':
  
  # generates a list with all the methods to call
  test_actions = inspect.getmembers(Test, predicate=inspect.ismethod)
  actions = []
  
  for m in test_actions:
    name = m[0]
    if name.startswith('test'):
      actions.append(m[0])
    
  desc = "Runs different ways to test tasking including %s\n" % pformat(actions)
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
                      choices=actions,                      
                      help='The different tests you can launch',)
  
  parser.add_option("-p", "--parameters",
                    dest="params",
                    help="All the different parameters to pass to the method")
  
  (options, args) = parser.parse_args()
  
  test = Test(options)
      