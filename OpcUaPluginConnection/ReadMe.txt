The OpcuaClientPlugin class is created for connect the Plugin with a OPC UA server.

REQUIREMENT:
	- OPC UA server, you need a for create the connection, in this case we use the WeatherStation at this address: opc.tcp://localhost:52520/OPCUA/hwSimulator;
	- Kafka and Zookeeper Server installed and running.
	- The Plugin need a file Json for the configuration, inside it you must insert the name of monitor point/s, the type, the refresh rate and other parameter (see https://github.com/IntegratedAlarmSystem-Group/ias/tree/master/plugin/test/resources/org/eso/iasplugin/config/test/jsonfiles).

	
OPC UA SERVER CONFIGURATION:
	- The client created by the java class send connection request to the server and generate one certificates file, this file must be added to the folder named "certs" otherwise the server refuse the client connection.


RUN:	
After check the requirement you can run the OpcuaClientPlugin class.
Example of the connection.
	-----------------------------
	Select the port number for the connection
	0 -> port number: 52520 for WeatherStation
	1 -> port number: 52521 for AMC
	2 -> port number: 52522 for PMC
	3 -> port number: 52523 for TCU
	4 -> port number: 52524 for THCU
	-----------------------------

Select the port number for select witch server you want and the class take the monitor point from the server (in this case only the wind speed named like ws_windspd) and the Plugin save it in a Kafka topics named "PluginsKtopic", then the converter take the data (the data are MonitorPointDataToBuffer type) from the topic and convert it into IASValue and save it in another Kafka topic named BsdbCoreKTopic.

The java class first initialized and start the loop of the converter, then start the plugin and create the connection only at this time create a OPC UA client and start collect data from the server.
