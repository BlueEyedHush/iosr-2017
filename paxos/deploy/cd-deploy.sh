#!/usr/bin/env bash

aws deploy create-deployment \
 --region us-east-1 \
 --application-name paxos-server \
 --deployment-group-name paxos-server \
 --s3-location bundleType=zip,bucket=iosr,key=travis-builds/app.zip