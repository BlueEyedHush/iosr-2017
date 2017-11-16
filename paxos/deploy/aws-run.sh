#!/usr/bin/env bash

touch /tmp/external-script-used

BASE=/home/ubuntu

pkill java
java -Dconfig.file="$BASE/application.conf" -jar "$BASE"/paxos-iosr.jar < /dev/null &> out.stdlog &