#!/bin/bash

source jmeter-version.sh

# Delete old jmeter directory
rm -d -r apache-jmeter-$JMETER_VER

# install jmeter
wget https://archive.apache.org/dist/jmeter/binaries/apache-jmeter-$JMETER_VER.zip
unzip -q apache-jmeter-$JMETER_VER.zip
rm -rf apache-jmeter-$JMETER_VER.zip

# install jmeter plugin manager
wget -O apache-jmeter-$JMETER_VER/lib/ext/jmeter-plugins-manager-0.16.jar https://jmeter-plugins.org/get/

# install command line runner
wget -O apache-jmeter-$JMETER_VER/lib/cmdrunner-2.2.jar https://search.maven.org/remotecontent?filepath=kg/apc/cmdrunner/2.2/cmdrunner-2.2.jar

# run jmeter to generate command line script
java -cp apache-jmeter-$JMETER_VER/lib/ext/jmeter-plugins-manager-0.16.jar org.jmeterplugins.repository.PluginManagerCMDInstaller

# install jpgc-json-2
apache-jmeter-$JMETER_VER/bin/PluginsManagerCMD.sh install jpgc-json

# install bzm-parallel 0.9
apache-jmeter-$JMETER_VER/bin/PluginsManagerCMD.sh install bzm-parallel

# install jar file for commons csv
wget -O apache-jmeter-$JMETER_VER/lib/ext/commons-csv-1.5.jar https://repo1.maven.org/maven2/org/apache/commons/commons-csv/1.5/commons-csv-1.5.jar
