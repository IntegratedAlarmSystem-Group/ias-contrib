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
import traceback

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

if __name__=="__main__":
    apes = [ 'APE1', 'APE2', 'TFINT' ]
    UMStates = {}
    for ape in apes:
        vrfs = VRFS("http://vrfs.alma.cl/getAntInfoWS.php?wsdl")
        ut = UTModRedis("metis.osf.alma.cl", "6379", "5")
        antennas = vrfs.get_antenna_list(ape)
        for element in antennas:
            key = "%s:%s" % ("UtilityModulePublisher", element["antenna"])
            auxiliar = {"Fire_Alarm": 0, "Emergency_Stop":0, "AC_Power":0, "UPS_Power":0, "Stow_Pin":0,
                       "Rx_Cabin_Temperature":0, "HVAC":0, "Antenna_Position":0,
                       "Drive_Cabin_Temperature":0, "Shutter_Status_at_Zenith":0}
            # Utility module status of the antenna
            um = ut.get_jason_dictionary(key)
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
    for k in UMStates:
        print k, UMStates[k]

