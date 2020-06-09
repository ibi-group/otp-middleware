#!/bin/sh

source ./jmeter-version.sh

if [ -z $1 ]
then
  >&2 echo 'Must supply first argument (number of threads).'
  exit 1
fi

if [ -z $2 ]
then
  >&2 echo 'Must supply second argument (number of loops).'
  exit 1
fi

if [ -z $3 ]
then
    >&2 echo 'Must supply third argument (csv file).'
    exit 1
fi

# clean up old output
rm -rf output
mkdir output
mkdir output/result
mkdir output/report

echo "starting jmeter script"

jmeter_cmd="apache-jmeter-$JMETER_VER/bin/jmeter.sh -n -t testbed.jmx -l output/result/result.csv -e -o output/report -Jthreads=$1 -Jloops=$2 -Jbatchcsvfile=$3"

echo "$jmeter_cmd"
eval "$jmeter_cmd"

echo "done"
