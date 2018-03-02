#!/usr/bin/env python

from random import *
from math import sqrt
import sys, os

def runTask(n):
  print "Estimating Pi using %d samples" % n
  inside=0
  #n=10000
  for i in range(0,n):
    x=random()
    y=random()
    if sqrt(x*x+y*y)<=1:
      inside+=1
  pi=4.0*inside/n
  print "Estimated Pi Value: %f" % pi
  return pi

if __name__ == '__main__':
  args = sys.argv[1:]
  
  if len(args) >= 1:
    runTask(int(args[0]))
  else:
    runTask(20000.0)
