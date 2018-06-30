The association antenna/pad is refreshed every 5 minutes (configurable in crontab).
Crontab runs the python (2.7) script that sends the monitor point to the 
UdpPlugin.
It makes sense to run this script by means of crontab because the value
is read very rarely.

The Udp plugin instead, must always be running as it continuosly sends to the BSDB the
last computed value with its validity and the heartbeat.

HOW-TO:
1 run the Udp plugin to listen for messages ut port 1111:
  > nohup iasRun -l j org.eso.ias.plugin.network.UdpPlugin -u 11111  -c ./antennaPadsPlugin.json >/dev/null &
2 crontab runs the script every 5 minutes:
  * * * * * /users/ialarms/utilitymodulepublisher/antennaPadsPluginRunner.sh

The python executable and antennaPadsPluginRunner.sh must be in 
/users/ialarms/utilitymodulepublisher

