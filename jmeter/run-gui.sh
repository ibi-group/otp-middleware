#!/bin/sh

source jmeter-version.sh

apache-jmeter-$JMETER_VER/bin/jmeter.sh -t 2-location-pairs.jmx
