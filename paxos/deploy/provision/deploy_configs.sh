#!/usr/bin/env bash

DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
export ANSIBLE_STDOUT_CALLBACK=debug

if [ -z "$1" ]; then
    ansible-playbook -i "$DIR"/ec2-inventory.py -u ubuntu "$DIR"/deploy_configs.yml
else
    ansible-playbook -i "$DIR"/ec2-inventory.py --connection=local --extra-vars "paxos_path=/tmp/" "$DIR"/deploy_configs.yml
fi

