FROM openjdk:8-jdk-alpine
RUN apk update && apk add netcat-openbsd wget
WORKDIR /usr/src/alma_weather_plugin
RUN wget -O /usr/src/alma_weather_plugin/AlmaWeatherStationPlugin.jar http://artifactory.inria.cl:8081/artifactory/inria-maven/org/eso/ias/alma-weather-station-plugin/dev/alma-weather-station-plugin-dev.jar
COPY config.json /usr/src/alma_weather_plugin/
COPY entrypoint.sh /usr/src/alma_weather_plugin
ENTRYPOINT ["./entrypoint.sh"]
