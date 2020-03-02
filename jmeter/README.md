# OTP-Middleware JMeter tests

This folder contains various items that to run JMeter load tests for OTP-Middleware.
(WIP: These instructions are copied from datatools-server.)

## Installation

Install JMeter with this nifty script:

```sh
./install-jmeter.sh
```

## Running

JMeter test plans can be ran from the JMeter GUI or it can be ran without a GUI.

### Starting jmeter GUI

This script starts the jmeter gui and loads a simple example test script.

```sh
./run-gui.sh
```

### Running test plans without GUI

Test plans can be ran straight from the command line.
A helper script is provided to assist in running JMeter from the command line. (to be completed)

## Test Plan

(to be completed)

## Reporting

If running this script in GUI mode, it is possible to see all results in real-time by viewing the various listeners at the end of the thread group.

When running the test plan from the command line in non-gui mode, reports will be saved to the `output` folder.  The outputs will contain a csv file of all requests made and an html report summarizing the results.
