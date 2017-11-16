#!/usr/bin/env bash

DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
export ANSIBLE_STDOUT_CALLBACK=debug

if [ -z "$1" ]; then
    ansible-playbook -i "$DIR"/ec2-inventory.py -u ubuntu "$DIR"/deploy_app.yml
elif [ "$1" == "local" ]; then
    ansible-playbook -i "$DIR"/ec2-inventory.py --extra-vars "local_deployment=true" -u ubuntu "$DIR"/deploy_app.yml
else
    ansible-playbook -i "$DIR"/ec2-inventory.py --connection=local --tags=config_gen --extra-vars "paxos_path=/tmp/" "$DIR"/deploy_app.yml
fi

