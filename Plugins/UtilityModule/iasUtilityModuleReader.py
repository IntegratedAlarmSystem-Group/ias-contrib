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
# Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA 02111-130

import re
import modbus_tk.modbus_tcp as modbus_tcp
import modbus_tk.defines as cst
import traceback
import sys
from datetime import datetime

class UtilityModule:
    
    def __init__(self, antenna):
        '''
        Initialize the class attributes.
        '''
        self.antenna = str(antenna)
        self.ip_address = self.__get_ip_address()
        self.data = {}
        
    def __get_ip_address(self):
        '''
        Use the antenna name to get the utility module IP address
        '''
        if "DV" in self.antenna:
          auxiliar = re.sub("\D", "", self.antenna)
          return "10.196.%s.22" % auxiliar
        elif "DA" in self.antenna:
          auxiliar = re.sub("\D", "", self.antenna)
          return "10.196.%s.22" % auxiliar
        elif "CM" in self.antenna:
          auxiliar = 80 + int(re.sub("\D", "", self.antenna))
          return "10.196.%d.22" % auxiliar
        elif "PM" in self.antenna:
          auxiliar = 92 + int(re.sub("\D", "", self.antenna))
          return "10.196.%d.22" % auxiliar
    
    def get_utility_module_data(self):
        '''
        Communicate with the utility module through the mod-bus protocol to retrieve the sensor status,
        0 for off status and 1 for on status.
        '''
        master = modbus_tcp.TcpMaster(self.ip_address, timeout_in_sec = 0.01)
        result = master.execute(1, cst.READ_DISCRETE_INPUTS, 0, 12)
        self.data["FIRE"] = result[0]
        self.data["E-STOP"] = result[1]
        self.data["AC-POWER"] = result[2]
        self.data["UPS-POWER"] = result[3]
        self.data["STOW-PIN"] = result[4]
        self.data["RX-CAB-TEMP"] = result[5]
        self.data["HVAC"] = result[6]
        self.data["ANTENNA-POS"] = result[7]
        self.data["DRIVE-CAB-TEMP"] = result[8]
        self.data["AT-ZENITH"] = result[9]

antennas = [
	'DV01','DV02','DV03','DV04','DV05','DV06','DV07','DV08','DV09','DV10','DV11','DV12','DV13','DV14','DV15','DV16','DV17','DV18','DV19','DV20','DV21','DV22','DV23','DV24','DV25',
	'DA41','DA42','DA43','DA44','DA45','DA46','DA47','DA48','DA49','DA50','DA51','DA52','DA53','DA54','DA55','DA56','DA57','DA58','DA59','DA60','DA61','DA62','DA63','DA64','DA65',
	'CM01','CM02','CM03','CM04','CM05','CM06','CM07','CM08','CM09','CM10','CM12', 
	'PM01','PM02','PM03','PM04'
]
    
if __name__=="__main__":

    # The name of the monitor point is given
    # by the prefix plus the name of the antenna
    mPointIdPrefix="Array-UMStatus-"
    
    # The utility module to conncet to each antenna
    utilityModules = []
    for antenna in antennas:
      utilityModules.append(UtilityModule(antenna))
    for utm in utilityModules:
      try:
        utm.get_utility_module_data()
        vals=[]
	for mpName in utm.data:
          vals.append("%s:%s" % (mpName,utm.data[mpName]))
        valToSend= ','.join(vals)
        idOfMp = mPointIdPrefix+utm.antenna
        # This is the string expected by utilitymoduleRemoteRunner.py
        print "{0} [{1}] MPoint sent with value '{2}'".format(datetime.utcnow().strftime("%Y-%m-%dT%H:%M:%S"),idOfMp,valToSend)
      except Exception as e:
        print "Errror reading data from", utm.antenna
