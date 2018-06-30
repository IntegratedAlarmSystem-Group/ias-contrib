#! /usr/bin/env python
#
# ALMA - Atacama Large Millimiter Array
# (c) Associated Universities Inc., 2011
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
# License along with this library; if not, write to alarms_status the
# Free Software Foundation, Inc., 
# 59 Temple Place, Suite 330, Boston, MA 02111-1307  USA

from suds.client import Client
from xml.dom import minidom
from datetime import datetime
import traceback, sys

from IASLogging.logConf import Log
from IasPlugin2.UdpPlugin import UdpPlugin

class VRFS:
    
    def __init__(self, vrfs_service):
        '''
        Initialize the class attributes.
        '''
        self.vrfs_service = vrfs_service

    def get_antenna_list(self, ste):
        '''
        Get all the antennas connect to the ALMA pads for STE.
        '''
        #Use suds to read the antennas deploy in VRFS in XML format.
        self.antenna_list = []
        try:
            client = Client(self.vrfs_service)
            xml_antennas = client.service.getAntennasInfo(ste)
            print xml_antennas
            root = minidom.parseString(xml_antennas)
            xml_list = root.getElementsByTagName("antenna")
            for element in xml_list:
                #Get the antenna name and pad.
                antenna = element.firstChild.data
                antenna = antenna.replace("-AS-01","").upper()
                pad = element.getAttribute("pad")
                #Store data in a dictionary and add to the list. 
                data = {"antenna":antenna, "pad":pad}
                self.antenna_list.append(data)
            return self.antenna_list
        except:
            message = str(traceback.format_exc())
            print message
            return self.antenna_list
        
 
if __name__=="__main__":

    logger = Log.initLogging(__file__)
    
    # Get the UDP port number from the command line
    if len(sys.argv)!=2:
      logger.error("UDP port expected in command line")
      sys.exit(-1)
    try:
      udpPort = int(sys.argv[1])
    except ValueError:
      logger.error("Invalid port number %s",(sys.argv[1]))
      sys.exit(-2)
    logger.info("Will send alarms to UDP port %d",udpPort)

    v = VRFS("http://vrfs.alma.cl/getAntInfoWS.php?wsdl")
    antennas = v.get_antenna_list("APE1")
    antennas.extend(v.get_antenna_list("APE2"))
    antennas.extend(v.get_antenna_list("TFINT"))

    antennaPads = []
    for element in antennas:
	antennaPads.append("%s:%s" % (element["antenna"], element["pad"]))
    stringToSendToIas=",".join(antennaPads)
    print "Found",len(antennaPads),"antennas"
    print stringToSendToIas

    udpPlugin = UdpPlugin("localhost",udpPort)
    udpPlugin.start()

    udpPlugin.submit("Array-AntennasToPads", stringToSendToIas, "STRING", timestamp=datetime.utcnow(), operationalMode='OPERATIONAL')

    udpPlugin.shutdown()

        
