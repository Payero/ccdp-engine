#!/usr/bin/env python
import time, sys, os

def runTask(n = 60):
    tw = TimeWait(n)
    return tw.res

class TimeWait:
    
    def __init__(self, n = 60):
        self.res = None
        self.runTask(int(n))
        
    def runTask(self, n):
        print "Waiting for " + str(n) + " seconds"
        time.sleep(n)
        print "Wait complete"
        self.res = "Waited " + str(n) + " seconds"
        return "Done"
    
if __name__ == '__main__':
    args = sys.argv[1:]
    
    if len(args) >= 1:
        runTask( int(args[0]) )
    else:
        runTask()