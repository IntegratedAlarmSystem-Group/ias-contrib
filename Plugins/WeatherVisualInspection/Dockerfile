FROM centos:7

# Install java and netcat
RUN yum update -y && \
  yum install java-1.8.0-openjdk-devel \
  wget \
  nmap-ncat \
  -y
ENV JAVA_HOME /usr/lib/jvm/java-1.8.0-openjdk

# Download plugin artifact
WORKDIR /usr/src/weather_visual_inspection_plugin
ARG version=dev
RUN echo "Using version = "${version}
RUN wget -qO /usr/src/weather_visual_inspection_plugin/weather-visual-inspection-plugin.jar http://artifactory.inria.cl:8081/artifactory/inria-maven/org/eso/ias/weather-visual-inspection-plugin/${version}/weather-visual-inspection-plugin-${version}.jar

# Add config file
COPY config.json /usr/src/weather_visual_inspection_plugin/

# Set up entrypoint
COPY entrypoint.sh /usr/src/weather_visual_inspection_plugin
ENTRYPOINT ["./entrypoint.sh"]
