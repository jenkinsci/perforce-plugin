FROM java:6-jdk

RUN apt-get update && apt-get install -y git && rm -rf /var/lib/apt/lists/*

ENV MAVEN_MAJOR_VERSION 2
ENV MAVEN_VERSION 2.2.1

RUN curl -fsSL https://archive.apache.org/dist/maven/maven-$MAVEN_MAJOR_VERSION/$MAVEN_VERSION/binaries/apache-maven-$MAVEN_VERSION-bin.tar.gz | tar xzf - -C /usr/share \
  && mv /usr/share/apache-maven-$MAVEN_VERSION /usr/share/maven \
  && ln -s /usr/share/maven/bin/mvn /usr/bin/mvn

ENV MAVEN_HOME /usr/share/maven

#VOLUME /root/.m2

RUN mkdir /src

#VOLUME /src
WORKDIR /src

