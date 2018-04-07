FROM openjdk:8-jdk-alpine
RUN apk update && apk add netcat-openbsd wget
WORKDIR /usr/src/alma_weather_plugin
ARG version=dev
RUN echo "Using version = "${version}
RUN wget -qO /usr/src/alma_weather_plugin/alma-weather-station-plugin.jar http://artifactory.inria.cl:8081/artifactory/inria-maven/org/eso/ias/alma-weather-station-plugin/${version}/alma-weather-station-plugin-${version}.jar
COPY config.json /usr/src/alma_weather_plugin/
COPY entrypoint.sh /usr/src/alma_weather_plugin
ENTRYPOINT ["./entrypoint.sh"]
