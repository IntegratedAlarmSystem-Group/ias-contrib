#! /usr/bin/env python
#
# ALMA - Atacama Large Millimiter Array
# (c) Associated Universities Inc., 2014
#
# This library is free software; you can redistribute it and/or
# modify it under the terms of the GNU Lesser General Public
# License as published by the Free Software Foundation; either
# version 2.1 of the License, or (at your option) any later version.
#
# This library is distributed in the hope that it will be useful,
# but WITHOUT ANY WARRANTY; without even the implied warranty of
# MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
# Lesser General Public License for more details.
#
# You should have received a copy of the GNU Lesser General Public
# License along with this library; if not, write to alarms_status the Free Software
# Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA 02111-1

import os, sys, subprocess
from IASLogging.logConf import Log
from IasPlugin2.UdpPlugin import UdpPlugin
from RedisMonitorPointsDic import RedisMonitorPointDic

# def getMon(mon):
#     pipe = subprocess.Popen("%s/RedisMonitorPoints.py %s 2>&1" % (os.path.dirname(__file__), mon), stdout=subprocess.PIPE, shell=True)
#     output, error = pipe.communicate()
#     if error is not None:
#         print ("Error: %s" % error)
#         sys.exit(1)
#     return output.strip()

def getMon(component):
    monitor_points = RedisMonitorPointDic(component, "metis.osf.alma.cl", 6379, 100000)
    log("RedisMonitorPointDic(%s) created" % component)

    return monitor_points.get_monitor_points()



def toDict( arr ):
    dic = {}
    for val in arr.split("|")[1].split(" "):
        splitted = val.split("=")
        if len(splitted) == 2:
            dic[splitted[0]] = str(splitted[1])
    return dic

def emptyResponse():
    print "OK: data retrieved |nan|"
    sys.exit()
def log(txt):
    pass
def verboseLog(txt):
    print txt

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
    mpoint_prefix = "Array-UMStatus-Ant"+templatePrefix

    num = antName[2:]
    ant = antName[:2]

    return 'Array-%s-%s-%s%s%d%s' % (device,mPointName,ant,templatePrefix,int(num), templateSuffix)
    

def runLoop():
    '''
    Get and submits all the monitor points of all the antennas
    '''
    log("Logging activated")


    log("About to read MLD monitor points")
   # mld = toDict( getMon("CONTROL_CentralLO_MLD") )
    #mld = getMon("CONTROL_CentralLO_MLD_10b7c33e01080095")
    mld = getMon("CONTROL_CentralLO_MLD_10b7c33e01080095")
    if "INPUT_SWITCH" not in mld.keys():
        log("INPUT_SWITCH not found in MLD")
        log(mld)
        emptyResponse()

    #mld["INPUT_SWITCH"] = "1"

    log("MLD switch found: %s" % mld["INPUT_SWITCH"])
    #print "OK: data retrieved | LASER_LOCKED=0"
    #sys.exit()

    if mld["INPUT_SWITCH"] == "1.0":
        ml = getMon("CONTROL_CentralLO_ML_930008016217fc10")
        log("ML selected")

    elif mld["INPUT_SWITCH"] == "2.0":
        ml = getMon("CONTROL_CentralLO_ML2_3000080205eba710")
        log("ML2 selected")

    else:
        emptyResponse()

#    for k in  ml.keys():
#        print "key=",k,"value=",ml[k]

    if "LASER_LOCKED" in ml.keys():
        laser_locked_id="Array-Laser-Locked"
        print laser_locked_id, ml["LASER_LOCKED"]
        #print "OK: data retrieved | LASER_LOCKED=%s " % "0.0"
    else:
        emptyResponse()

        #print("Unexpected error:", sys.exc_info()[0])

    antNames = antennaNames()
    for ant in  antNames:
        print "Getting CRYO of",ant
        cryo = getMon("CONTROL/%s/FrontEnd/Cryostat"%(ant))
#        for k in  cryo.keys():
#            print "CRIO key=",k,"value=",cryo[k]
        temp0_id=buildMPointName(ant,'CRIO','TEMP0')
        temp5_id=buildMPointName(ant,'CRIO','TEMP5')
        temp9_id=buildMPointName(ant,'CRIO','TEMP0')
        pres0_id=buildMPointName(ant,'CRIO','VACUUM-PRES0')
        pres1_id=buildMPointName(ant,'CRIO','VACUUM-PRES1')
        print "%s ="%(temp0_id), cryo["TEMP0_TEMP"]
        print "%s ="%(temp5_id), cryo["TEMP5_TEMP"]
        print "%s ="%(temp9_id), cryo["TEMP9_TEMP"]
        print "%s ="%(pres0_id), cryo["VACUUM_GAUGE_SENSOR0_PRESSURE"]
        print "%s ="%(pres1_id), cryo["VACUUM_GAUGE_SENSOR1_PRESSURE"]

        print "Getting CMPR of",ant
        cmpr = getMon("CONTROL/%s/CMPR"%(ant))
#    for k in  cmpr.keys():
#        print "key=",k,"value=",cmpr[k]
        cmpr_DriveOn_id=buildMPointName(ant,'CMPR','DRIVE_ON')
        print "%s ="%(cmpr_DriveOn_id), cmpr["COMPRESSOR_DRIVE_INDICATION_ON"]



if __name__=="__main__":
    logger = Log.initLogging(__file__)

    log("Logging activated")

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

    runLoop()



