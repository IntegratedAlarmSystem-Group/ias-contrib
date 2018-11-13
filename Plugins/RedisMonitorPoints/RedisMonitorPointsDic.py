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

#
# The logging function is commented, because zenoss show a strange behaviour when the 
# logging function is activated.
#

# THIS FILE is a refactor of RedisMonitorPoints, but:
# - it return a dict instead of print to stdout
# - it handles better nan values

import sys
import os
import time
import math
#import logging
# import traceback
import redis

class RedisMonitorPointDic:
    
    def __init__(self, component, server, port, tolerance_time):
        '''
        Initialize the class attributes.
        '''
        self.component = "%s" %  component.replace("_","/").replace("MLD/", "MLD_").replace("ML/", "ML_").replace("ML2/", "ML2_")
        self.channel = "TMCS:%s:*" % self.component
        self.monitor_lists = None
        self.server = server
        self.port = port
        self.tolerance_time = tolerance_time
    
    def get_monitor_points(self):
        '''
        Connect to a Redis server to retrieve all the monitor points values
        associated to the component.
        '''
        response = {}
        try:
            #Get the connection to the redis server.
            connection = redis.StrictRedis(host=self.server, port=self.port, db=0)
            log ("Connecting to the server %s with port %d." % (self.server, self.port))
            self.monitor_lists = connection.keys(self.channel)
            if len(self.monitor_lists) == 0:
                log("The component %s is not returning data." % self.component)
            for element in self.monitor_lists:
                #Get the monitor point name, then get the clob from redis,
                #finally process the clob to retrieve the monitor point value.
                #monitor_point = TMCS:component:monitor_point.
                monitor_point = element.split(":")[2]
                data = connection.lindex(element, -1)
                log( repr(monitor_point) +":"+ repr(data) )
                value = self.process_data(data, monitor_point)
                response[monitor_point] = value
        except:
            log( "%s -- %s" % (self.component, str(traceback.format_exc())) )            
        return response
            
    def process_data(self, data, monitor_point):
        '''
        Get the monitor point value from the clob, and return the value.
        '''
        actual_time = time.time()
        value = data.split(";")[-1]
        #time_struct = start time in acs time, end time in acs time, etc.
        time_struct = data.split("|")[1]
        acs_end_time = float(time_struct.split(";")[1])
        #Convert ACS time to UTC time.
        utc_end_time = (acs_end_time - 122192928000000000) / 10000000
        #If the data is older than the tolerance time discard the data.
        if math.fabs(actual_time - utc_end_time) > self.tolerance_time:
            log("TOO OLD")
            #message = "The actual time is: %f, the monitor point %s" % (actual_time, monitor_point)
            #message = message + " end time is: %f" % (utc_end_time)
            #message = message + " there is %f seconds of difference." % (actual_time - utc_end_time)
            #logging.error(message)
            #Retrun nan if the value is too old.
            value = "nan"
        return str(value)
            

def log(txt): 
    pass
def verboseLog(txt):
    print txt

if __name__=="__main__":
    if "DEBUG" in os.environ.keys() and os.environ["DEBUG"] == "1":
        log = verboseLog

    component = sys.argv[1]
    log("Component: %s" % component)
    monitor_points = RedisMonitorPointDic(component, "metis.osf.alma.cl", 6379, 4500)
    print monitor_points.get_monitor_points()

    if len(sys.argv) > 2:
        print monitor_points.get_monitor_points()[ sys.argv[2] ]
