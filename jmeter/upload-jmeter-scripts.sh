#!/bin/bash

# Usage: upload-jmeter-scripts.sh <hostname>
# <hostname> can be an entry from your ~/.ssh/config
# or it can be of form user:pwd@host (same format as in ssh).

host=$1

if [ -z $1 ]
then
  >&2 echo 'Usage: upload-jmeter-scripts.sh <hostname>'
  exit 1
fi

# Create fresh directories, and copy jmeter files.
ssh $1 'rm -rf ~/jmeter'

ssh $1 'mkdir -p ~/jmeter'
scp *.jmx $host:~/jmeter
scp install-jmeter.sh $host:~/jmeter
scp jmeter-version.sh $host:~/jmeter
scp run-tests.sh $host:~/jmeter

ssh $1 'mkdir -p ~/jmeter/fixtures'
scp fixtures/* $host:~/jmeter/fixtures

# (re-)install JMeter
ssh $host 'cd ~/jmeter; ./install-jmeter.sh'
