FROM centos:7
RUN yum update -y && \
  yum install java-1.8.0-openjdk-devel \
  wget \
  -y
ENV JAVA_HOME /usr/lib/jvm/java-1.8.0-openjdk
WORKDIR /usr/src/dummy_plugin
ARG version=dev
RUN echo "Using version = "${version}
RUN wget -qO /usr/src/dummy_plugin/dummy-plugin.jar http://artifactory.inria.cl:8081/artifactory/inria-maven/org/eso/ias/dummy-plugin/${version}/dummy-plugin-${version}.jar
ENTRYPOINT ["java", "-jar", "dummy-plugin.jar"]
