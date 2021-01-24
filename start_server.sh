#!/bin/sh

# This is a launch script for the server.
# It assumes the existence of the JAR file at /home/deploy/dojo-latest-server.jar
# and a .env file with any necessary environment variables.
# It is recommended to be used with a systemd service file (see `server.service`).

cd /home/deploy

set -a  # Automatically export the variables, to make them available in the subprocess, see https://unix.stackexchange.com/a/79077
. ./.env
set +a  # Done with auto-export

set -e  # Stop script on error
set -x  # Print executed commands

java -server -XX:+UnlockExperimentalVMOptions -XX:+UseG1GC -XX:+UseStringDeduplication -jar dojo-latest-server.jar
