FROM openjdk:8
RUN apt-get update && apt-get install -y netcat
WORKDIR /usr/src/alma_weather_plugin
COPY dist/AlmaWeatherStationPlugin.jar /usr/src/alma_weather_plugin
COPY config/config-dev.json /usr/src/alma_weather_plugin
COPY entrypoint.sh /usr/src/alma_weather_plugin
CMD ["./entrypoint.sh"]
