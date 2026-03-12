# OTP-middleware Live Tracking

This file provides an overview of live tracking in OTP-middleware.

## Live Tracking Actions

OTP-middleware supports triggering certain actions during live tracking when someone reaches a location or is about to enter a path.
Actions include location-sensitive API calls to notify various services.
In the context of live trip tracking, actions may include notifying transit vehicle operators or triggering traffic signals.

### Location-Based Actions

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

| Class | Description                                                              |
| --- |--------------------------------------------------------------------------|
| `UsGdotGwinnettTrafficSignalNotifier` | Remotely activates select pedestrian signals in Gwinnett County, GA, USA |

Location-based trip actions are triggered when someone is in the `TRIP_INSTRUCTION_IMMEDIATE_RADIUS` of
either the `start` or `end` location.

### Bus Notifier Actions

Bus notifier actions are defined in the optional file `bus-notifier-actions.yml` in the same configuration folder as `env.yml`.
The file contains a list of actions defined by an agency ID and a fully-qualified trigger class:

```yaml
- agencyId: id1
  trigger: com.example.package.MyTriggerClass
```

Known trigger classes below are in package `org.opentripplanner.middleware.triptracker.interactions.busnotifiers`
and implement its `BusOperatorInteraction` interface.

| Class | Description                                                                                          |
| --- |------------------------------------------------------------------------------------------------------|
| `UsRideGwinnettNotifyBusOperator` | Notifies a bus driver on a specific trip in the RideGwinnett (Gwinnett County, GA, USA) service area |
