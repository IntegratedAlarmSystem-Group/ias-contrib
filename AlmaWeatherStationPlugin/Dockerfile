FROM openjdk:8
WORKDIR /usr/src/alma_weather_plugin
COPY . /usr/src/alma_weather_plugin
RUN mv /usr/src/alma_weather_plugin/WeatherStationPluginTest.json.docker /usr/src/alma_weather_plugin/WeatherStationPluginTest.json
RUN ./gradlew build
