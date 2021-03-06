# Alma Weather Station Plugin

This plugin retrieves data from the [alma weather station](http://weather.aiv.alma.cl/ws_weather.php) SOAP service and publishes them to a Kafka Queue.

The _weather station plugin_ was created extending the plugin class in the ias core, inspired in the example provided in _plugin/test/.../DumbWeatherStationPlugin.java_.


## Requirements
To be able to connect to the station the device must be connected to a network from ALMA, ESO, NAOJ or NRAO.

The following programs must be installed:

* Zookeeper (v.3.4.9)
* Apache Kafka (v.1.0)
* Gradle (v.4.4.1)
* Java JDK (v.1.8.x)

## Build and Run

 To build it execute `gradle` in the _AlmaWeatherStationPlugin_ folder, this will run the default tasks build and clean.

 `[AlmaWeatherStationPlugin]$ gradle`

 The jar is generated by default in the dist/ directory.

To execute the jar, you must be in the same folder as the config file _WeatherStationPluginTest.json_ and kafka must be running, then run

`java -jar dist/AlmaWeatherStationPlugin.jar`

 The data retrieved is published in kafka, by default in the topic _PluginKTopic_.

 To see the output of the plugin in the console you can run a simple consumer using

 `./kafka-console-consumer.sh --bootstrap-server localhost:9092 --topic PluginsKTopic`

 from the _kafka/bin/_ folder.

## Run the examples

After generate the jar file following the previous steps you can also run the available examples.

To execute the examples, you must run:

`java -cp dist/AlmaWeatherStationPlugin.jar <example-class>`
