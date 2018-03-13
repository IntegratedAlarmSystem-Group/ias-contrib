FROM openjdk:8-jdk-alpine
WORKDIR /usr/src/dummy_plugin
COPY dist/DummyPlugin.jar .
ENTRYPOINT ["java", "-jar", "DummyPlugin.jar"]
