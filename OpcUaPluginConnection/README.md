The OpcuaClientPlugin class is created for connect the Plugin with a OPC UA server.

# REQUIREMENT:
	- OPC UA server, you need a for create the connection, in this case we use the WeatherStation at this address: opc.tcp://localhost:52520/OPCUA/hwSimulator;
	- Kafka and Zookeeper Server installed and running.
	- The Plugin need a file Json for the configuration, inside it you must insert the name of monitor point/s, the type, the refresh rate and other parameter (see https://github.com/IntegratedAlarmSystem-Group/ias/tree/master/plugin/test/resources/org/eso/iasplugin/config/test/jsonfiles).

	
# OPC UA SERVER CONFIGURATION:
	- The client created by the java class send connection request to the server and generate one certificates file, this file must be added to the folder named "certs" otherwise the server refuse the client connection.


# RUN:	
After check the requirement you can run the OpcuaClientPlugin class.
Example of the connection.
*
	-----------------------------
	Select the port number for the connection
	0 -> port number: 52520 for WeatherStation
	1 -> port number: 52521 for AMC
	2 -> port number: 52522 for PMC
	3 -> port number: 52523 for TCU
	4 -> port number: 52524 for THCU
	-----------------------------
*
Select the port number for start a connection beetween one OPCUA server, this class collect monitor point from the server (in this case only the wind speed value named ws_windspd). 
The Plugin save the monitor points in a Kafka topics named "PluginsKtopic", the Converter take the data from the Plugin topic and convert it into IASValue, after the conversion they are saved in another Kafka topic named BsdbCoreKTopic.
This Prototype first start the converter loop then the Plugin, at this point start a client OPC UA and connect it to a server OPC UA, at this time it's possible take monitor points.
