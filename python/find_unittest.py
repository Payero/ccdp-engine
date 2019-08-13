#!/usr/bin/env python
import os, sys, glob


def find_unittest():
  if os.environ.has_key('CCDP_HOME'):
    path = os.environ['CCDP_HOME']
  else:
    path = os.path.realpath(__file__)
    base, name = os.path.split(path)
    path, name = os.path.split(base)

  if not os.path.isdir(path):
    print "ERROR: Could not find the source directory"
    sys.exit(-1)

  files = []
  def visit(arg, dirname, names):
    for name in names:
      if name.endswith("UnitTest.java"):
        files.append(name)

  src_dir = os.path.join(path, "src")
  os.path.walk(src_dir, visit, "The arg")
  
  names = []
  for name in files:
    names.append(name[0:name.find('UnitTest.java')])

  print ""
  print "Found the following Unit Tests:"
  for name in names:
    print "\t%s" %name
  print ""
  print "Invoking 'ant <test name>'' runs only that test"
  print "Invoking 'ant test' runs them all"
  print ""
  print "The configuration file can be set by setting the ccdp.config.file system" 
  print "property for example:" 
  print ""
  print "    'ant -Dccdp.config.file=<new file> test' "
  print ""
  print "will run all the tests using the given configuration file"
  print ""


if __name__ == '__main__':
  find_unittest()
