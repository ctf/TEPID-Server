#### Build ####
FROM    gradle:jdk11 as build

COPY    --chown=gradle:gradle . /home/gradle/tepid-server/
WORKDIR /home/gradle/tepid-server
RUN     gradle war

#### Tomcat ####
FROM    tomcat:8-jdk11
RUN     apt-get update
RUN     apt-get install -y samba-client ghostscript postgresql-client

COPY    server.xml conf/server.xml

RUN	rm -rf /usr/local/tomcat/webapps/*
COPY    --from=build /home/gradle/tepid-server/build/libs/tepid*.war /usr/local/tomcat/webapps/tepid.war

CMD     ["catalina.sh", "run"]
