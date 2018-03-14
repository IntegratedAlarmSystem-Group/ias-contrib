FROM openjdk:8-jdk-alpine
WORKDIR /usr/src/dummy_plugin
RUN wget -qO /usr/src/dummy_plugin/DummyPlugin.jar http://artifactory.inria.cl:8081/artifactory/inria-maven/org/eso/ias/dummy-plugin/dev/dummy-plugin-dev.jar
ENTRYPOINT ["java", "-jar", "DummyPlugin.jar"]
