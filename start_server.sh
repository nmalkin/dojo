#!/bin/sh

# This is a launch script for the server.
# It assumes the existence of the JAR file at /home/deploy/dojo-latest-server.jar
# and a .env file with any necessary environment variables.
# It is recommended to be used with a systemd service file (see `server.service`).

cd /home/deploy
. ./.env

set -ex

java -server -XX:+UnlockExperimentalVMOptions -XX:+UseG1GC -XX:+UseStringDeduplication -jar dojo-latest-server.jar
