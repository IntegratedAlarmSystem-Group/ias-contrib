#! /usr/bin/env python

'''
Connects to the 3 APEs and get the status of the antennas
from their utility module.

Note that the utility modules are reachable only from
the APE wher they are connected.

This script runs the command though ssh
'''

from subprocess import Popen, PIPE

# The APEs with their output
APEs = {"ape2-gns.osf.alma.cl":"","tfint-gns.osf.alma.cl":"","10.197.52.1":""}

# The name pattern of the moinito points
mPointIdPrefix="Array-UMStatus-"

if __name__=="__main__":
  for ape in APEs.keys():
    cmd = ["ssh"]
    cmd.append("ialarms@"+ape)
    cmd.append("/users/ialarms/utilitymodulepublisher/utilitymodulePlugin.py")
    cmd.append("11112")
    print cmd
    proc=Popen(cmd,stdout=PIPE)
    APEs[ape]=proc.stdout.read()
    print "Command returned", proc
    print "output:\n",APEs[ape]

  # Scans the outputs for antennas
  lines = []
  for ape in APEs.keys():
    lines.extend(APEs[ape].split("\n"))
  
  mPoints = [x for x in lines if x.count(mPointIdPrefix)!=0 ]
  print "Monitor point to send to the java plugin:"
  print "\n==>".join(mPoints)
  
  for mp in mPoints:
    parts = mp.split(' ')
    if (len(parts)==7):
      mpId=parts[1][1:-1]
      value=parts[6][1:-1]
      print mpId, value
    else:
      print "Wrong format, cannot extract data from ",parts
