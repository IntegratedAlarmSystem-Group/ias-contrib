FROM openjdk:8
RUN apt-get update && apt-get install -y netcat wget
WORKDIR /usr/src/alma_weather_plugin
RUN wget -O /usr/src/alma_weather_plugin/AlmaWeatherStationPlugin.jar http://artifactory.inria.cl:8081/artifactory/inria-maven/org/eso/ias/alma-weather-station-plugin/0.0.1/alma-weather-station-plugin-0.0.1.jar
RUN mkdir -p /usr/src/alma_weather_plugin/config
COPY config.json /usr/src/alma_weather_plugin/
COPY entrypoint.sh /usr/src/alma_weather_plugin
ENTRYPOINT ["./entrypoint.sh"]
