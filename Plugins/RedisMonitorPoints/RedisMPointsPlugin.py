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

import sys, time
from datetime import datetime
from IASLogging.logConf import Log
from IasPlugin2.UdpPlugin import UdpPlugin
from RedisMonitorPointsDic import RedisMonitorPointDic

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

def log(txt):
    pass

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

def toAlarm(value, priority='SET_MEDIUM',invertLogic=False):
    '''
    Return the alarm with the passed priority
    if the value is not 0; otherwise return cleared

    @param value The numeric value to convert to an alarm
    @param priority The priority of the alarm if the value activates the alarm
    @param invertLogic reverse the logic 
           (i.e. the alarm is set when the value is 0; unset otherwise)
    @return the alarm with the passed state and priority
    '''
    val = float(value)

    if not invertLogic:
        if val == 0:
            return 'CLEARED'
        else:
            return priority
    else:
       if val == 0:
            return priority
       else:
            return 'CLEARED'

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

def submitMPoint(plugin,id,value, type,mode='OPERATIONAL'):
    ''' 
    Sumbit a monitor point to the UDP plugin that will. in turno,
    forward to the java counterpart and from there to the IAS

    @param plugin The UDP plugin
    @param id The identifier of the monitor point
    @param value The value of the monitor point
    @param the type of the monitor point
    @param mode the operational mode
    ''' 
    if value is not None:
        try:
            plugin.submit(id, value, type, timestamp=datetime.utcnow(), operationalMode=mode) 
            print "Submitted %s with value %s, type %s and mode %s" % (id,value,type,mode)
        except Exception, e:
            print "Error submitting %s with value %s: %s" % (id,value,str(e))
    else:
        print "Got a NULL value for",id,"value lost"
        
def getLaserLockedValue():
    '''
    Get the value of LASER_LOCKED from redis

    @return the value of the LASER_LOCKED monitor point or None if not 
            available in redis
    '''
    mld = getMon("CONTROL_CentralLO_MLD_10b7c33e01080095")
    if mld is None:
        return None
    if "INPUT_SWITCH" not in mld.keys():
        print "INPUT_SWITCH not found in MLD"
        return None

    if mld["INPUT_SWITCH"] == "1.0":
        ml = getMon("CONTROL_CentralLO_ML_930008016217fc10")
        print "ML selected"

    elif mld["INPUT_SWITCH"] == "2.0":
        ml = getMon("CONTROL_CentralLO_ML2_3000080205eba710")
        print "ML2 selected"

    else:
        print "Unknown INPUTS_SWITCH"
        return None

    if "LASER_LOCKED" in ml.keys():
        return  ml["LASER_LOCKED"]
    else:
        print "LASER_LOCKED not foundD"
        return None
    

def runLoop(plugin):
    '''
    Get and submits all the monitor points of all the antennas

    @param plugin the UDP plugin to send values to the IAS
    '''

    laser_locked =  getLaserLockedValue()
    if laser_locked is not None:
        laser_locked_id="Array-Laser-Locked"
        val = toAlarm( laser_locked, 'SET_CRITICAL',invertLogic=True )
        submitMPoint(plugin, laser_locked_id, val,"ALARM")

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
        if "TEMP0_TEMP" in cryo.keys():
            submitMPoint(plugin, temp0_id,  cryo["TEMP0_TEMP"],"DOUBLE")
        if "TEMP5_TEMP" in cryo.keys():
            submitMPoint(plugin, temp5_id,  cryo["TEMP5_TEMP"],"DOUBLE")
        if "TEMP9_TEMP" in cryo.keys():
            submitMPoint(plugin, temp9_id,  cryo["TEMP9_TEMP"],"DOUBLE")
        if "VACUUM_GAUGE_SENSOR0_PRESSURE" in cryo.keys():
            submitMPoint(plugin, pres0_id,  cryo["VACUUM_GAUGE_SENSOR0_PRESSURE"],"DOUBLE")
        if "VACUUM_GAUGE_SENSOR1_PRESSURE" in cryo.keys():
            submitMPoint(plugin, pres1_id,  cryo["VACUUM_GAUGE_SENSOR1_PRESSURE"],"DOUBLE")

        cmpr = getMon("CONTROL/%s/CMPR"%(ant))
#    for k in  cmpr.keys():
#        print "key=",k,"value=",cmpr[k]
        cmpr_DriveOn_id=buildMPointName(ant,'CMPR','DRIVE_ON')
        if "COMPRESSOR_DRIVE_INDICATION_ON" in  cmpr.keys():
            submitMPoint(plugin, cmpr_DriveOn_id,  cmpr["COMPRESSOR_DRIVE_INDICATION_ON"],"DOUBLE")

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
            runLoop(udpPlugin)
            logger.info("Loop terminated: all data sent")
        except Exception, e:
            logger.error("Exception starting the plugin or getting data: "+str(e))
        finally:
            try:
                udpPlugin.shutdown()
            except:
                logger.error("Exception closing the UPD plugin")
        time.sleep(loopSecs)



