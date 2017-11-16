#!/usr/bin/env bash

DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
export ANSIBLE_STDOUT_CALLBACK=debug

ansible-playbook -i "$DIR"/ec2-inventory.py -u ubuntu "$DIR"/stop_app.yml

