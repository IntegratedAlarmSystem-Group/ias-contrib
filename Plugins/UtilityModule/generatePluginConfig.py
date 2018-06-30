#! /usr/bin/env python
'''
 Generate the JSON config file for the 
 plugin that sends monitor points read from the 
 Utility modules
 
 Each monitor point has the following format:
 
 Array-UM-<NAME>-<ANT>
 
 like for example UM-FIRE-DA45
 
 Created on Jun 13, 2018

 @author: acaproni
'''


antennas = [
        'DV01','DV02','DV03','DV04','DV05','DV06','DV07','DV08','DV09','DV10','DV11','DV12','DV13','DV14','DV15','DV16','DV17','DV18','DV19','DV20','DV21','DV22','DV23','DV24','DV25',
        'DA41','DA42','DA43','DA44','DA45','DA46','DA47','DA48','DA49','DA50','DA51','DA52','DA53','DA54','DA55','DA56','DA57','DA58','DA59','DA60','DA61','DA62','DA63','DA64','DA65',
        'CM01','CM02','CM03','CM04','CM05','CM06','CM07','CM08','CM09','CM10','CM12',
        'PM01','PM02','PM03','PM04'
]

mpoint_prefix = "Array-UM"

mpIds = [ 'FIRE', 'AC', 'UPS', 'HVAC', 'STOWPIN', 'ATZENITH', 'CABINTEMP']

mpPrefix = '{"id":"'
mpSuffix = '", "refreshTime":"10000", "filter":"", "filterOptions":""}'

jsonHeader = '''{
  "id":"UtilityModules",
  "monitoredSystemId":"array",
  "sinkServer":"10.195.60.180",
  "sinkPort":"9092",
  "autoSendTimeInterval":"3",
  "hbFrequency":"3",
  "properties": [],
  "values":[
'''

jsonFooter = ''']
}
'''

if __name__ == '__main__':
    mpoints = ""
    cr = "\n"
    first = True
    for ant in antennas:
        for mpId in mpIds:
            id = "%s-%s-%s" % (mpoint_prefix,mpId,ant)
            mpJson = "    %s%s%s" % (mpPrefix,id,mpSuffix)
            if not first:
                mp = "%s,%s%s" % (mp, cr, mpJson)
            else:
                mp = mpJson
                first = False
    
    configJson = jsonHeader+mp+jsonFooter    
    print(configJson)
