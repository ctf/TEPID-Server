#!/usr/bin/env bash

############################
# The following will build #
# the war file             #
############################

printf "Starting testpid script...\n"

echo "$CI_PROJECT_DIR"
echo "$CI_COMMIT_SHA"

chmod +x ./gradlew
./gradlew clean build test war

if [ -e build/libs/tepid.war ]; then
    printf "Moving war file\n"
	mv -f build/libs/tepid.war /var/lib/tomcat8/webapps/tepid.war
	printf "Restarting tepid\n"
else
	printf "Could not find tepid.war in build output\n"
	exit 1
fi
