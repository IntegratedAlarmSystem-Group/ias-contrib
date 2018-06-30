#! /usr/bin/env python

'''
Connects to the 3 APEs and get the status of the antennas
from their utility module.

Note that the utility modules are reachable only from
the APE wher they are connected.

This script runs the command though ssh
'''

import sys
from subprocess import Popen, PIPE
from datetime import datetime

from IASLogging.logConf import Log
from IasPlugin2.UdpPlugin import UdpPlugin

# The APEs with their output
APEs = {"ape2-gns.osf.alma.cl":"","tfint-gns.osf.alma.cl":"","10.197.52.1":""}

# The name pattern of the moinito points
mPointIdPrefix="Array-UMStatus-"

if __name__=="__main__":

  if len(sys.argv)!=2:
      print "UDP port expected in command line"
      sys.exit(-1)

  try:
    udpPort = int(sys.argv[1])
  except ValueError:
    logger.error("Invalid port number %s",(sys.argv[1]))
    sys.exit(-2)
  print"Will send alarms to UDP port %d",udpPort

  udpPlugin = UdpPlugin("localhost",udpPort)
  udpPlugin.start()

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
      udpPlugin.submit(mpId, value, "STRING", timestamp=datetime.utcnow(), operationalMode='OPERATIONAL')
      print "{0} [{1}] MPoint sent with value '{2}'".format(datetime.utcnow().strftime("%Y-%m-%dT%H:%M:%S"), mpId,value)
    else:
      print "Wrong format, cannot extract data from ",parts


  udpPlugin.shutdown()
