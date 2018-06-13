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
import traceback, sys
from threading import Timer

from IASLogging.logConf import Log
from IasPlugin2.UdpPlugin import UdpPlugin

class VRFS(object):
    
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
            logger.error("Exception got getting the list of antennas")
            return self.antenna_list

# Counts the number of iteration to schedule the execution of each task
iteration = 0

# The plugin to send monitor points and alarms to the IAS
udpPlugin = None

# The logger
logger = None
        
def schedule():
    timer = Timer(1, periodicTask)
    timer.start() 

def periodicTask():
    """
    The periodic task runs every second

    The iteration is increased at every iteration and used
    to schedule tasks at certain time intervals
    """
    global iteration
    try:
      if iteration < sys.maxint:
        iteration = iteration + 1
      else:
        iteration = 0
      print "Periodic task running "+str(iteration)
      if iteration % 60 == 0:
        # Get and send the association antenna/pad
        getAntennaPadAssociation()
    except:
       message = str(traceback.format_exc())
       logger.error("Exception got in periodic task %s", message)
    schedule()

def getAntennaPadAssociation():
    """
    Get and publish the association of anetnnas to pad
    """
    global udpPlugin
    logger.info("Getting antennea/pad association")
    
    v = VRFS("http://vrfs.alma.cl/getAntInfoWS.php?wsdl")

    # Get the anteannas from the 3 APEs
    apes = [ 'APE1', 'APE2', 'TFINT' ]
    antennas = []
    for ape in apes:
       antennas.extend(v.get_antenna_list(ape))

    # Build the string to send to the IAS
    strToSend = ""
    for element in antennas:
        s = "[%s,%s] " % (element["antenna"], element["pad"])
        logger.info("[name:%s pad:%s] ", element["antenna"], element["pad"])
        strToSend = strToSend+s
    strToSend=strToSend[:-1]
    logger.info("%d antennas found",len(antennas))

    udpPlugin.submit("ANTvsPAD", strToSend, "STRING", operationalMode="OPERATIONAL")
    logger.info("Assocation sent to the UDP port [%s]",strToSend)

if __name__=="__main__":
    logger = Log.initLogging(__file__)
    udpPlugin = UdpPlugin("localhost",10101)
    udpPlugin.start()
    logger.info("UDP plugin started")

    schedule()

