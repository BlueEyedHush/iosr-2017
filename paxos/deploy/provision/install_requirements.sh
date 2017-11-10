#!/usr/bin/env bash

DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"

ansible-galaxy install -r "$DIR"/requirements.yml -p "$DIR"/roles/