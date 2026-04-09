# Trip Monitoring

This file provides an overview of the trip monitoring functionality.
Trip monitoring lets users save a trip from a search in OTP and receive any real-time updates such as delays and alerts.
Trips saved by users can be retrieved at a later point for viewing/editing.

## Configuration Items

| Key | Default Value | Description |
| --- | --- | --- |
| `MAXIMUM_MONITORED_TRIP_ITINERARY_CHECKS` | 3 | The maximum number of attempts to obtain a monitored trip itinerary. |
| `MAXIMUM_PERMITTED_MONITORED_TRIPS` | 5 | Constant. The maximum number of saved monitored trips. |
| `OTP_REQUESTS_THREADING_ENABLED` | true | Use multi-threading to handle OTP requests and responses. |
| `OTP_SERVER_REQUEST_TIMEOUT_IN_SECONDS` | 30 | The maximum time for making requests to OTP. |
| `OTP_TRANSFER_SLACK_SECONDS` | 0 | Extra time added by OTP between two transit legs to ensure transfers can be made, accounting for small timing variations that happen in reality. |

## Overview

OtpUser
MonitoredTrip
JourneyState
TripAnalyzer
CheckMonitoredTrip

## Trip Monitoring Lifecycle

Trip monitoring is run as a background, recurring job `MonitorAllTripsJob` that runs every minute.
The job splits the monitoring of all qualifying trips between threads as determined by the number of CPUs on the instance.
Each thread runs a `TripAnalyzer`, and each analyzer processes a queue of `MonitoredTrip`, one trip at a time.

Trip monitoring is currently intended to be performed by a single instance.
Running trip monitoring on multiple instances is possible, however race conditions may occur.

The trip monitoring lifecycle is illustrated in the following diagram, where the executing instance has *n* cores:

```mermaid
---
title: Trip Monitoring Lifecycle (Single Instance with n cores)
---
flowchart LR
    job[MonitorAllTripsJob]
    analyzer1[TripAnalyzer]
    analyzer2[TripAnalyzer]
    analyzerX[...]
    analyzerN[TripAnalyzer]
    queue1[Trip 1<br>Trip n+1<br>...]
    queue2[Trip 2<br>Trip n+2<br>...]
    queueN[Trip n<br>...]
    Timer --> job --thread 1--> analyzer1
    job --thread 2--> analyzer2
    job -.-> analyzerX
    job --thread n--> analyzerN
    analyzer1 --> queue1
    analyzer2 --> queue2
    analyzerN --> queueN

```

## Trip Monitoring Conditions

Class `CheckMonitoredTrip` contains the actual trip monitoring logic.
All saved trips are checked for the following conditions:

- Trip is active
- Trip is not snoozed
- Trip is one-time not in the past, or trip is recurring
- Trip is not deemed no longer possible
- Trip is being monitored on a particular day
- Trip start time is within the lead monitoring time, and the trip end time has not passed yet.

Within an hour of the trip start time, checks are performed every 15 minutes.
Within 30 minutes the trip start time, checks are performed every minute.

## Important concepts

### Target Date and Matching Itinerary

When a user saves an itinerary for monitoring, that itinerary is saved in Mongo as a template without real-time updates
or alerts. The query parameters used to obtain that itinerary are also saved, so that a request to OTP to
replan the trip can be made, if needed.

The *target date* is the date a trip is supposed to take place. For one-time trips, the target date is the date of the trip.
For recurring trips, the target date is the next occurrence of the trip, according to the days the trip is being monitored.

When monitoring real-time updates, a *matching itinerary* is fetched from OTP using the query parameters used to obtain the original itinerary.
A matching itinerary looks similar to the saved itinerary, however, the itinerary date corresponds to the target date.
Some of the times can be shifted, and real-time alerts can be present.

Whether an itinerary matches another one is determined by the `ItineraryMatcher` utility class.

### Itinerary Fetching and Matching

The `ItineraryMatcher` class uses two methods to find a matching itinerary: Leg ID and Plan.
Either one can optionally run in multiple threads if `OTP_REQUESTS_THREADING_ENABLED` is set to `true`,
independently from the threading from `MonitorAllTripsJob`.

#### Leg ID Method

Transit legs are fetched using OTP's `leg` query for a particular day.
Returned legs, if found, contain rela-time delays or alerts.
Legs are queried using a leg id, which is a hash used by OTP to quickly lookup a leg.
A leg id combines the date, service id, and from and to places of a transit leg.

If all transit legs are found by querying leg ids, an itinerary is reconstructed by attempting to fit the
transfer legs between the fetched, updated transit legs.
There are two constraints in that process:

- Transfer legs
: The duration of transfer legs (and other walk/bicycle legs) does not change because the physical ability
of travelers is constant. A three-minute walk to transfer between two transit routes is thus preserved while reconstructing the itinerary.

- Slack or margins before, between, and after transit legs.
: Slacks are constants that ensure a traveler has enough time to get to the boarding location
of the next transit vehicle. Slacks are part of the OTP routing configuration.
From the original itinerary, OTP-middleware can compute:

  - the boarding slack (minimum time between the end of a walk leg and the start of the next transit leg)
  - the alighting slack (minimum time between the end of a transit leg and the start of the next walk leg)
  
If an additional transfer slack is configured in OTP, it must also be configured in OTP-middleware using the
`OTP_TRANSFER_SLACK_SECONDS` configuration parameter.

If, after fitting the transfer legs, the wait time is more than the transfer slack, we have an updated, feasible itinerary.
If not, the itinerary is not feasible given the real-time updates (see diagram below).

```mermaid
gantt
    title Itinerary Matching Using Leg ID
    dateFormat HH:mm
    axisFormat %H:%M
    section Original Itinerary
        Walk : 12:10, 12:18
        Slack : 5m
        Bus 1: 12:23, 12:39
        Walk : 3m
        Slack : 5m
        Bus 2: 12:50, 13:00
    section Updated, Feasible Itinerary
        Walk : 12:12, 12:20
        Slack : 5m
        Bus 1, 2 min late: 12:25, 12:41
        Walk : 3m
        Slack: 5m
        Bus 2, on time: 12:50, 13:00
    section Updated, Infeasible Itinerary
        Walk : 12:20, 12:28
        Slack : 5m
        Bus 1, 10 min late: 12:33, 12:49
        Walk : crit, 3m
        Slack: crit, 5m
        Bus 2, on time: 12:50, 13:00
```

#### Plan Method

If the leg id method fails, a `plan` query is sent to OTP to replan the entire trip for the target date, using the
query parameters of the monitored trip. This method is slower than leg id method because OTP has to perform a full itinerary
search, whereas a leg id query is a simple lookup operation.
Itineraries returned from the OTP plan query are stripped from delay data and matched against the original monitored itinerary.

Two itineraries match if they meet the conditions below:
- Both itineraries can be monitored (they don't contain rentals)
- The have the same number of legs
- The origin and destination stops match
- The same modes and transit routes are used in the same order
- The transit vehicles must present the same headsign
- The same interlining between routes must be present (e.g. situations when the same vehicle continues as a different route starting from a stop)
- The start times and end times are the same.

#### Itinerary Matching Attempts

In case no matching itinerary is found using either method above, the process can be re-attempted at the next run of `MonitorAllTripsJob`,
up to the number of attempts set by `MAXIMUM_MONITORED_TRIP_ITINERARY_CHECKS`.
If the number of attempts is reached and a matching itinerary is not found, a notification of type `ITINERARY_NOT_FOUND`
("Unable to monitor trip") is sent.

### Journey State

The journey state contains various attributes that deal with real-time trip monitoring, including:
- Matching itinerary
- Target date and trip status (upcoming, in the past, active, trip not found)
- Latest departure and arrival delay updates
- Notifications sent, so that the same notifications are not unnecessarily repeated to users.

## Itinerary Existence Checking

Itinerary existence checking is performed prior to saving a new monitored trip.
Typically, the OTP-react-redux UI will request an itinerary check for a given itinerary.
OTP-middleware will perform an additional check upon submission (POST). If the check does not succeed,
the submission is rejected, and the trip is not saved or monitored.

## Notifications

Notifications are of the following types.
Multiple notifications for the same check are generally combined into a single notification message.

- Advance trip reminder ("Reminder for My Trip at 8:30") - Sent once at the lead monitoring time for all monitored trips on the day the trip occurs.
- Trip alerts - Sent once each new GTFS-realtime alerts attached to transit legs, and once when a previously present alert is no longer there (i.e. cleared).
- Trip delay notifications ("Your trip is departing/arriving 5 minutes late") - Sent if delays exceed 5 minutes from the original trip time.
- Loss of real-time data in OTP - Sent each time a matching itinerary is fetched and contains no real-time updates, while the previously fetched matching itinerary did.
- Unable to monitor trip - Sent when real-time conditions are so that a trip can no longer be performed because of
missed transfers or other reasons.

Trip notifications can be sent using the following channels as selected by the user in their account settings:
- Email to the account's email address, using Sparkpost
- SMS to the number stored and verified in their account, using Twilio
- Push notifications to mobile apps that subscribe to a specific AWS SNS service. A separate push middleware must be
implemented that manages registered devices and forwards notifications to them.

A notification message template (.ftl file) is provided for each notification channel,
so that notifications are formatted to fit the receiving device.
The length of the content sent via SMS can impact the amount billed by the SMS service.
Push notification content may be trimmed automatically to fit the message format of the mobile platform.
