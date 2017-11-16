#!/usr/bin/env bash

touch /tmp/external-script-used

BASE=/home/ubuntu/

pkill java
nohup java -cp "$BASE":"$BASE"paxos-iosr.jar agh.iosr.paxos.client.LocalClient < /dev/null &> out.stdlog &