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

### Starting JMeter GUI

This script starts the JMeter GUI and loads a test script.

```sh
./run-gui.sh
```

### Running test plans without GUI

JMeter tests can be ran straight from the command line:

```sh
./run-tests.sh <nthreads> <nloops> <csvfile>
```

The syntax for the script is as follows:

* `<nthreads>`: (Required) Specifies the number of threads to run. Each thread is equivalent to a user.
  Threads are run in parallel.
* `<nloops>`: (Required) Specifies the number of loops, i.e. the number of times each thread or user will
  go though the list of locations.
* `<csvfile>`: (Required) Specifies the path to a CSV file with the OTP trip locations.
  An example file can be found at `fixtures/locations.csv`

### Uploading scripts and running tests on a remote server

You can upload the test scripts to a remote server using:

```sh
./upload-jmeter-scripts.sh <hostname>
```

The syntax for the script is as follows:

* `<hostname>`: (Required) Can be a server name found in your `~/.ssh/config` file,
  or can be of the form `user:password@server` (same format as SSH).

This script will also run `install-jmeter.sh` on the remote server.
Once execution completes, you can run `run-tests.sh` from the `~/jmeter` folder on the remote server.

## Reporting

If running this script in GUI mode, it is possible to see all results in real-time by viewing the various listeners at the end of the thread group.

When running the test plan from the command line in non-gui mode, reports will be saved to the `output` folder.  The outputs will contain a csv file of all requests made and an html report summarizing the results.
