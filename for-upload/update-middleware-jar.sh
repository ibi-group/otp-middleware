#!/bin/bash

# Usage:
# First, upload this script to the folder on the
# EC2 middleware instance where the JAR will reside.
# Then, execute update-middleware.sh <pr-branch-name>
# If you wish, you may remove the script from the server after execution.


# Check that a branch name was specified.
# Exit if not.
if [ -z $1 ]
then
  >&2 echo Branch name was not specified. Exiting.
  exit 1
fi
branchname=$1


# Constants
JAR_INFO_FILE=jar-info-$branchname.txt
NEW_JAR_INFO_FILE=jar-latest-$branchname.txt
AWS_S3_SERVER=https://otp-middleware-builds.s3.amazonaws.com
OLD_JAR_FOLDER=oldjars


# Extract name of existing jar from first line if file exists.
currentjarfile=undefined
if [ -f "$JAR_INFO_FILE" ]
then
  currentjarfile=$(head -n 1 $JAR_INFO_FILE)

  # Check that this file exists, o/w append notfound
  if [ ! -f "$currentjarfile" ]
  then
    currentjarfile=$currentjarfile--notfound
  fi
fi
echo Current JAR: $currentjarfile


# Download latest jar INFO for the specified branch
latestfile=$(wget $AWS_S3_SERVER/$branchname/latest.txt -O $NEW_JAR_INFO_FILE -q)
if [ ! latestfile ]
then
  >&2 echo Download failed. Exiting.
  exit 1
fi


# Extract name of latest jar from first line.
newjarfile=$(head -n 1 $NEW_JAR_INFO_FILE)
echo -Latest JAR: $newjarfile


# Validate jar file name (ends in .jar ...).
# Double brackets needed to use wildcards.
if [[ "$newjarfile" != *".jar" ]]
then
  echo Incorrect JAR file name. Exiting.
  rm -f $NEW_JAR_INFO_FILE
  exit 1
fi

# If jar names are the same, cleanup and don't do anything else.
if [ "$newjarfile" == "$currentjarfile" ]
then
  echo Already up-to-date. Exiting.
  rm -f $NEW_JAR_INFO_FILE
  exit 0
fi


# Before downloading the new jar,
# get a list of jar files that we will archive.
oldjarlist=$(ls *.jar)


# Download the specified jar from the build bucket.
# Exit if download failed.
echo About to download file $newjarfile

if wget $AWS_S3_SERVER/$branchname/$newjarfile
then echo Download successfull.
else
  >&2 echo Download failed. Exiting.
  rm -f $NEW_JAR_INFO_FILE
  exit 1
fi


# Get the java process that is running the current JAR version of OTP middleware.
process="$(pgrep -f $currentjarfile)"


# Stop (gracefully) the current java process for middleware
# if that process is still running.
if [[ "$process" == "" ]]
then
  echo No running OTP Middleware process found.
else
  echo About to stop OTP Middleware process: $process.
  if kill $process
  then echo Process $process has been stopped.
  else
    >&2 echo Process $process was not stopped. Cleaning up and exiting.
    rm -f $NEW_JAR_INFO_FILE
    rm -f $newjarfile
    exit 1
  fi
fi


# Start middleware process.
echo Starting OTP Middleware process...
nohup java -jar $newjarfile &


# Check no abrupt startup shutdowns
# (e.g. wait a few seconds and check for middleware process).
sleep 7s
newprocess="$(pgrep -f $newjarfile)"

if [[ "$newprocess" == "" ]]
then
  # The process stopped,
  # so restart the previous version if applicable.
  >&2 echo Unable to start OTP Middleware.

  if [[ "$process" != "" ]]
  then
    echo Reverting to previous version...
    nohup java -jar $currentjarfile &
  fi

  # Cleanup
  >&2 echo Cleaning up and exiting.
  rm -f $NEW_JAR_INFO_FILE
  rm -f $newjarfile
  exit 1

else
  # The process started. Finish up.
  echo New OTP Middleware process $newprocess seems to be running.

  # Rename (overwrite) info file.
  echo Saving new version information...
  mv -f $NEW_JAR_INFO_FILE $JAR_INFO_FILE


  # Create the old jars folder if necessary.
  # Move the old jars there.
  echo Archiving old files...
  mkdir -p $OLD_JAR_FOLDER
  while IFS= read -r jar
  do
    if [ "$jar" != "" ]
    then
      mv -f $jar $OLD_JAR_FOLDER
    fi
  done <<< "$oldjarlist"
fi
