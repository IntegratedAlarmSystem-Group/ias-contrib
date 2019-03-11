#! /usr/bin/env python

startTag= '{"id":"'
endTag = '!]", "refreshTime":"60000", "filter":"", "filterOptions":""}'

mPointIds = [
  'Array-PSA-OPERATIONAL',
  'Array-PSA-SHUTDOWN',
  'Array-PSD-OPERATIONAL',
  'Array-PSD-SHUTDOWN',
  'Array-CMPR-OPERATIONAL'
]

DV = ('DV', range(1,26))
DA = ('DA', range(41,66))
PM = ('PM', range(1,5))
CM = ('CM', range(1,13))

antennaTypes = [ DV, DA, CM, PM ]

firstRow = True
for tp in antennaTypes:
  for antNum in tp[1]:
    for mPName in mPointIds:
      if not firstRow:
        print ','
      else:
        firstRow=False
      print startTag+mPName+'-'+tp[0]+'[!#'+str(antNum)+endTag,
