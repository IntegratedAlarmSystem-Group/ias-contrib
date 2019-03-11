#! /usr/bin/env python
import requests, json,sys, time
from datetime import datetime
from IASLogging.logConf import Log
from IasPlugin2.UdpPlugin import UdpPlugin

restSvcUrl = 'http://acse2-gas02.sco.alma.cl:9000'

def antennaNames():
    DV = range(1,26)
    DA = range(41,66)
    PM = range(1,5)
    CM = range(1,13)

    ret=[]
    for dv in DV:
        ret.append('DV'+'%02d'%(dv))
    for da in DA:
        ret.append('DA'+'%02d'%(da))
    for pm in PM:
        ret.append('PM'+'%02d'%(pm))
    for cm in CM:
        ret.append('CM'+'%02d'%(cm))
    return ret

def buildMPointName(antName,device, mPointName):
    '''
    Build the name of the monitor point for the passed antenna
    and monitor point ID, applying templates

    @param  antName thename of the antenna like DA45
    @param device the device like CRIO
    @param  mPointName The ID of the monitor point like LASER_LOCKED
    @return the ID of the monitor point for the IAS
    '''
    templatePrefix="[!#"
    templateSuffix= "!]"

    num = antName[2:]
    ant = antName[:2]
    
    return 'Array-%s-%s-%s%s%d%s' % (device,mPointName,ant,templatePrefix,int(num), templateSuffix)

def isOperational(device):
  data = "{\"componentName\": \""+device+"\", \"methodName\": \"getHwState\", \"arguments\": {}}"
  r = requests.post(restSvcUrl, data=data)
  if r.status_code is 200:
    j = json.loads(r.text)
    return j['data']['data']['_n']
  else:
    print "Error from REST server code",r.status_code
    return None

def isPsShutdown(device):
  data = "{\"componentName\": \""+device+"\", \"methodName\": \"GET_PS_SHUTDOWN\", \"arguments\": {}}"
  r = requests.post(restSvcUrl, data=data)
  if r.status_code is 200:
    j = json.loads(r.text)
    return j['data'][0]
  else:
    print "Error from REST server code",r.status_code
    return None

def isFepsShutdown(device):
  data = "{\"componentName\": \""+device+"\", \"methodName\": \"GET_OUTPUT_STATE\", \"arguments\": {}}"
  r = requests.post(restSvcUrl, data=data)
  if r.status_code is 200:
    j = json.loads(r.text)
    on =  j['data'][0]
    if on == '1':
      return True
    else:
      return False
  else:
    print "Error from REST server code",r.status_code
    return None

def runLoop(plugin):
  '''
  Get and subits monitor points to the IAS.
  
  The monitor points to return are the result of ISL methods called through the REST API

  @param plugin the UDP plugin to send values to the IAS
  @return the exection time of the loop in msecs
  '''
  startTime = int(round(time.time() * 1000))
  antennas = antennaNames()
  print antennas
  for ant in antennas:
    psaName = 'CONTROL/'+ant+'/PSA'
    psdName = 'CONTROL/'+ant+'/PSD'
    fepsName= 'CONTROL/'+ant+'/FEPS'
    cmprName= 'CONTROL/'+ant+'/CMPR'
    crioName= 'CONTROL/'+ant+'/Cryostat'
    print ant
    print '\tPSA',  buildMPointName(ant,'PSA','OPERATIONAL'), isOperational(psaName)
    print '\tPSA',  buildMPointName(ant,'PSA','SHUTDOWN'), isPsShutdown(psaName)
    print '\tPSD',  buildMPointName(ant,'PSD','OPERATIONAL'), isOperational(psdName)
    print '\tPSD',  buildMPointName(ant,'PSD','SHUTDOWN'), isPsShutdown(psdName)
    #print '\tFEPS', isOperational(fepsName),isFepsShutdown(fepsName)
    print '\tCMPR',  buildMPointName(ant,'CMPR','OPERATIONAL'), isOperational(cmprName)
    #print '\tCRIO', isOperational(crioName)

  return int(round(time.time() * 1000))-startTime

if __name__ == "__main__":

  logger = Log.initLogging(__file__)

  logger.info("Started")

  # Get the UDP port number from the command line
  if len(sys.argv)!=3:
      logger.error("UDP port and loop time (seconds) expected in command line")
      sys.exit(-1)
  try:
      udpPort = int(sys.argv[1])
  except ValueError:
      logger.error("Invalid port number %s",(sys.argv[1]))
      sys.exit(-2)
  logger.info("Will send alarms to UDP port %d",udpPort)

  try:
      loopSecs = int(sys.argv[2])
  except ValueError:
      logger.error("Invalid loop time (seconds) %s",(sys.argv[2]))
      sys.exit(-3)
  logger.info("Will send alarms every %d seconds ",loopSecs)


  while True:
      logger.info("Running a new loop")
      try:
          udpPlugin = UdpPlugin("localhost",udpPort)
      except:
          logger.error("Exception building the UdpPlugin with port {}",udpPort)
          time.sleep(loopSecs)
          continue

      try:
          udpPlugin.start()
          execTime=runLoop(udpPlugin)
          logger.info("Loop terminated: all data sent in %d msecs",execTime)
      except Exception, e:
          logger.error("Exception starting the plugin or getting data: "+str(e))
      finally:
          try:
              udpPlugin.shutdown()
          except:
              logger.error("Exception closing the UPD plugin")
      if loopSecs>execTime/1000:
              sleepTime =  loopSecs-execTime/1000
              logger.info("Will sleep for %d secs",sleepTime)
              time.sleep(sleepTime)
