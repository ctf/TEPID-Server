#!/usr/bin/env bash

############################
# The following will build #
# the war file             #
############################

printf "Starting testpid script...\n"

echo "$CI_PROJECT_DIR"
echo "$CI_COMMIT_SHA"

TO_DELETE=false

if [ -n "$CI" ] && [ ! -f "priv.properties" ]; then
    TO_DELETE=true
    printf "Creating priv properties\n"
    touch priv.properties
    echo "DEBUG=true" >> priv.properties
    echo "COUCHDB_USERNAME=$COUCHDB_USERNAME" >> priv.properties
    echo "COUCHDB_PASSWORD=$COUCHDB_PASSWORD" >> priv.properties
    echo "RESOURCE_CREDENTIALS=$RESOURCE_CREDENTIALS" >> priv.properties
    echo "HASH=$CI_COMMIT_SHA" >> priv.properties
else
    printf "Priv properties already exists in $PWD\n"
fi

chmod +x ./gradlew
./gradlew clean build test war

if [ "$TO_DELETE" = true ]; then
    printf "Remove priv properties\n"
    rm priv.properties
fi

if [ -e build/libs/tepid.war ]; then
    printf "Moving war file\n"
	mv -f build/libs/tepid.war /var/lib/tomcat8/webapps/tepid.war
#	rm -rf /var/lib/tomcat8/webapps/tepid
	printf "Restarting tepid\n"
	sudo systemctl restart tomcat8.service
else
	printf "Could not find tepid.war in build output\n"
	exit 1
fi

