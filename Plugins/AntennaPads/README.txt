HOW-TO:
1 run the Udp plugin to listen for messages ut port 1111:
  > iasRun -l j org.eso.ias.plugin.network.UdpPlugin -u 11111  -c ./antennaPadsPlugin.json 
2 crontab runs the script every 5 minutes:
  * * * * * /users/ialarms/utilitymodulepublisher/antennaPadsPluginRunner.sh

The python executable and antennaPadsPluginRunner.sh must be in 
/users/ialarms/utilitymodulepublisher

