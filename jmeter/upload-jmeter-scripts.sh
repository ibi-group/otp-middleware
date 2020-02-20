#!/bin/bash

# Usage: upload-jmeter-scripts.sh <hostname>
# <hostname> can be an entry from your ~/.ssh/config
# or it can be of form user:pwd@host (same format as in ssh).

host=$1

# Create directory if needed
ssh $1 'mkdir -p ~/jmeter'

# Copy scripts and jmeter files
scp *.jmx $host:~/jmeter
scp *.sh $host:~/jmeter

# (re-)install JMeter
ssh $host 'cd ~/jmeter; ./install-jmeter.sh'
