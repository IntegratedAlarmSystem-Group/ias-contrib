#! /usr/bin/env python

'''
Generate the configuration of the IASIOs to append to the iasios.json in the CDB
'''

antennas = [
        'DV01','DV02','DV03','DV04','DV05','DV06','DV07','DV08','DV09','DV10','DV11','DV12','DV13','DV14','DV15','DV16','DV17','DV18','DV19','DV20','DV21','DV22','DV23','DV24','DV25',
        'DA41','DA42','DA43','DA44','DA45','DA46','DA47','DA48','DA49','DA50','DA51','DA52','DA53','DA54','DA55','DA56','DA57','DA58','DA59','DA60','DA61','DA62','DA63','DA64','DA65',
        'CM01','CM02','CM03','CM04','CM05','CM06','CM07','CM08','CM09','CM10','CM12',
        'PM01','PM02','PM03','PM04'
]

mpoint_prefix = "Array-UMStatus-"

iasioPrefix ="  {\n"
iasioSuffix ="  }"

if __name__ == '__main__':
  jsonIasios = []
  for ant in antennas:
    mp = '%s    "id": "%s%s",' % (iasioPrefix,mpoint_prefix,ant)
    mp = '%s\n    "shortDesc": "Status of %s from utility module",' % (mp,ant)
    mp = '%s\n    "iasType": "STRING"\n%s' % (mp,iasioSuffix)
    jsonIasios.append(mp)

  print(',\n'.join(jsonIasios))

