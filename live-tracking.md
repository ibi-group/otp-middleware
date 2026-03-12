# OTP-middleware Live Tracking

This file provides an overview of live tracking in OTP-middleware.

## Overview

Live tracking keeps a record of a user's travel (or `TrackedJourney`) and provides location-based actions
as the user follows the itinerary saved in a `MonitoredTrip`.

## Tracked Journey Lifecycle

A `TrackedJourney` contains tracking information from the moment the journey is initiated until it is terminated.
While a `TrackedJourney` is active, successive locations of the person traveling are recorded.

A tracked journey's lifecycle is managed by calling the following endpoints, typically from a mobile app:

| Endpoint from `/api/secure/monitoredtrip` | Description |
| --- | --- |
| ~~`/starttracking`~~ | (Deprecated) Initiates the tracking of a monitored trip |
| ~~`/updatetracking`~~ | (Deprecated) Provides tracking updates on a monitored trip |
| `/track` | Starts or updates tracking on a monitored trip with an array of locations with timestamp |
| `/endtracking` | Terminates the tracking of a monitored trip by the user |
| `/forciblyendtracking` | Forcibly terminates tracking of a monitored trip by trip ID |
| `/reroute` | Reroute from the traveler's current location to the original trip destination |

## Traveler Status

As the traveler's location is updated using the `/track` endpoint, one of the following status is calculated:

| Status | Description |
| --- | --- |
| `DEVIATED` | Traveler is deviated, i.e. outside of the on-track boundary for the applicable mode on the path indicated by the `MonitoredTrip`'s itinerary. This is returned regardless of whether the traveler is ahead or behind schedule. |
| `ON_SCHEDULE` | Traveler is within the expected position boundary at the interpolated time for that position |
| `BEHIND_SCHEDULE` | Traveler's position is within the expected position boundary but at a time later than the expected, interpolated time for that position |
| `AHEAD_OF_SCHEDULE` | Traveler's position is within the expected position boundary but at a time before the expected, interpolated time for that position |

Whether someone is deviated or not is determined by the following optional configuration parameters in `env.yml`:

| Parameter | Default value | Description |
| --- | --- | --- |
| `TRIP_TRACKING_WALK_ON_TRACK_RADIUS` | 5 | The threshold in meters below which walking is considered on track. |
| `TRIP_TRACKING_BICYCLE_ON_TRACK_RADIUS` | 10 | The threshold in meters below which cycling is considered on track. |
| `TRIP_TRACKING_BUS_ON_TRACK_RADIUS` | 20 | The threshold in meters below which travelling by bus is considered on track. |
| `TRIP_TRACKING_SUBWAY_ON_TRACK_RADIUS` | 100 | The threshold in meters below which travelling by subway is considered on track. |
| `TRIP_TRACKING_RAIL_ON_TRACK_RADIUS` | 200 | The threshold in meters below which travelling by rail is considered on track. |
| `TRIP_TRACKING_TRAM_ON_TRACK_RADIUS` | 100 | The threshold in meters below which travelling by tram is considered on track. |

Whether someone is on-time or not is determined by the timestamp of the last location in the `/track` payload
and by the following optional configuration parameters in `env.yml`. The longer the segment time,
the more tolerant the "on-time" determination is.

| Parameter | Default value | Description |
| --- | --- | --- |
| `TRIP_TRACKING_MINIMUM_SEGMENT_TIME` | 5 | The minimum segment size in seconds for interpolated points. |

## Live Tracking Actions

OTP-middleware supports triggering certain actions during live tracking when someone reaches a location or is about to enter a path.
Actions include location-sensitive API calls to notify various services.
In the context of live trip tracking, actions may include notifying transit vehicle operators or triggering traffic signals.

### Location-Based Actions (`trip-actions.yml`)

Location-based trip actions are defined in the optional file `trip-actions.yml` in the same configuration folder as `env.yml`.
The file contains a list of actions defined by an ID, start and end coordinates, and a fully-qualified trigger class:

```yaml
- id: id1
  start:
    lat: 33.95684
    lon: -83.97971
  end:
    lat: 33.95653
    lon: -83.97973
  trigger: com.example.package.MyTriggerClass
- id: id2
  start:
    lat: 33.95584
    lon: -83.97871
  end:
    lat: 33.95553
    lon: -83.97873
  trigger: com.example.package.MyTriggerClass
...
```

Known trigger classes are in package `org.opentripplanner.middleware.triptracker.interactions`
and implement its `Interaction` interface.

| Class | Description |
| --- | --- |
| `UsGdotGwinnettTrafficSignalNotifier` | Remotely activates select pedestrian signals in Gwinnett County, GA, USA |

Location-based trip actions are triggered when someone is in the `TRIP_INSTRUCTION_IMMEDIATE_RADIUS` of
either the `start` or `end` location.

### Bus Notifier Actions (`bus-notifier-actions.yml`)

Bus notifier actions are defined in the optional file `bus-notifier-actions.yml` in the same configuration folder as `env.yml`.
The file contains a list of actions defined by an agency ID and a fully-qualified trigger class:

```yaml
- agencyId: id1
  trigger: com.example.package.MyTriggerClass
```

Known trigger classes below are in package `org.opentripplanner.middleware.triptracker.interactions.busnotifiers`
and implement its `BusOperatorInteraction` interface.

| Class | Description |
| --- | --- |
| `UsRideGwinnettNotifyBusOperator` | Notifies a bus driver on a specific trip in the RideGwinnett (Gwinnett County, GA, USA) service area |
