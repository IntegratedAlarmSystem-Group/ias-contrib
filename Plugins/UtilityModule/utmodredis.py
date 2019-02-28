#! /usr/bin/env python
#
# ALMA - Atacama Large Millimiter Array
# (c) Associated Universities Inc., 2012
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

import redis
import json
import traceback, sys, time
from datetime import datetime

from IASLogging.logConf import Log
from IasPlugin2.UdpPlugin import UdpPlugin

class UTModRedis:

    def __init__(self, server, port, socket_timeout):
        '''
        Initialize the class attributes.
        '''
        try:
            self.connection = redis.StrictRedis(host=server, port=int(port), socket_timeout=int(socket_timeout), db=0)
            self.status = self.connection.ping()
        except:
            self.status = False
            message = str(traceback.format_exc())
            print message

    def set_jason_dictionary(self, key, dictionary, expiration_time):
        '''
        Parse a dictionary structure to a JSON object  and 
        store the JSON object  in a Redis cache server using
        the key.
        '''
        try:
            if self.status:
                send_value = json.dumps(dictionary)
                self.connection.set(key, send_value)
                self.connection.expire(key, int(expiration_time))
        except:
            message = str(traceback.format_exc())
            print message

    def get_jason_dictionary(self, key):
        '''
        Retrieve a JSON object from a Redis cache server and
        parse to a dictionary.
        '''
        try:
            if self.status:
                json_value = self.connection.get(key)
                result = json.loads(json_value)
                return result
            return None
        except:
            message = str(traceback.format_exc())
            print message
            return None


from vrfs import VRFS
import time

def getAntennaIndex(antName):
    '''
    Build the index of the antenna from its name
    for example DA41 is 26

    :param antName: The name of the antenna like PM02
    :return: the index of the antenna for the template
    '''
    if antName is None or len(antName)!=4:
        raise ValueError("Invalid antenna name: "+antName)
    antType = antName[:2]
    antNum  = int(antName[2:])
    if antType=='DV':
        return str(antNum)
    elif antType=='DA':
        return str(antNum-15)
    elif antType=='CM':
        return str(antNum+50)
    elif antType=='PM':
        return str(antNum+62)
    else:raise ValueError('Unrecognized antenna type '+antType)

def runIteration(udpPlugin):
    '''
    Gets the data from the server and send to the java plugin with the passed
    UdpPlugin

    :param udpPlugin: the UdpPlugin to send data to
    :return:
    '''
    apes = [ 'APE1', 'APE2', 'TFINT' ]
    UMStates = {}
    AntennasPads = {}
    for ape in apes:
        vrfs = VRFS("http://vrfs.alma.cl/getAntInfoWS.php?wsdl")
        ut = UTModRedis("metis.osf.alma.cl", "6379", "5")
        antennas = vrfs.get_antenna_list(ape)
        for element in antennas:
            print "Antenmna",element['antenna'],"Pad",element['pad']
            AntennasPads[element["antenna"]]=element['pad']
        for element in antennas:
            key = "%s:%s" % ("UtilityModulePublisher", element["antenna"])
            auxiliar = {"Fire_Alarm": 0, "Emergency_Stop":0, "AC_Power":0, "UPS_Power":0, "Stow_Pin":0,
                        "Rx_Cabin_Temperature":0, "HVAC":0, "Antenna_Position":0,
                        "Drive_Cabin_Temperature":0, "Shutter_Status_at_Zenith":0}
            # Utility module status of the antenna
            print "Getting status of the UM of antenna ",element
            try:
 	        um = ut.get_jason_dictionary(key)
            except Exception,e:
                print "Error getting the state of ", element
		print e
	        um = None
            if um is not None:
                ac_power=um["AC_Power"]
                at_zenith=um["Shutter_Status_at_Zenith"]
                hvac=um["HVAC"]
                fire = um["Fire_Alarm"]
                ups_power=um["UPS_Power"]
                stow_pin=um[ "Stow_Pin"]
                rx_cab_temp=um["Rx_Cabin_Temperature"]
                drive_cab_temp=um[ "Drive_Cabin_Temperature"]
                antenna_pos=um["Antenna_Position"]
                e_stop=um[ "Emergency_Stop"]

                UMStatusWord= "AC-POWER:%s,AT-ZENITH:%s,HVAC:%s,FIRE:%s,UPS-POWER:%s,STOW-PIN:%s,RX-CAB-TEMP:%s,DRIVE-CAB-TEMP:%s,ANTENNA-POS:%s,E-STOP:%s" % (
                    ac_power,  at_zenith,hvac,fire, ups_power,stow_pin,rx_cab_temp, drive_cab_temp,antenna_pos, e_stop)
                UMStates[element["antenna"]]=UMStatusWord
    # print the UM state of each antenna
    for k in UMStates:
        print k, UMStates[k]
    # Print association of antennas to pads
    l =[]
    for k in AntennasPads:
        l.append(k+':'+AntennasPads[k])
    antspads =  ",".join(l)

    udpPlugin.submit("Array-AntennasToPads", antspads, "STRING", timestamp=datetime.utcnow(), operationalMode='OPERATIONAL')
    logger.info("Sent %s",antspads)
    time.sleep(0.05)

    templatePrefix="[!#"
    templateSuffix= "!]"
    mpoint_prefix = "Array-UMStatus-Ant"+templatePrefix
    for ant in UMStates:
        antIndex=getAntennaIndex(ant)
        idx = mpoint_prefix+antIndex+templateSuffix
        udpPlugin.submit(idx, UMStates[ant], "STRING", timestamp=datetime.utcnow(), operationalMode='OPERATIONAL')
        logger.info("Sent %s with ID %s",UMStates[ant],idx)
        time.sleep(0.05)

if __name__=="__main__":
    logger = Log.initLogging(__file__)

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
            runIteration(udpPlugin)
            logger.info("Loop terminated: all data sent")
        except:
            logger.error("Exception starting the plugin or geting data")
        finally:
            try:
                udpPlugin.shutdown()
            except:
                logger.error("Exception closing the UPD plugin")
        time.sleep(loopSecs)
