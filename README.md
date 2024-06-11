# otp-middleware
The otp-middleware application proxies requests from OpenTripPlanner UI to API, 
enhancing [OpenTripPlanner](https://www.opentripplanner.org) (OTP) with user
storage, real-time trip monitoring, and more!

## Background
OTP provides multi-modal journey planning, combining transit, biking, walking,
and various car options (e.g., Park & Ride) into complete passenger itineraries.

Transportation agencies might want to extend the functionality of their
trip planner to improve the overall user experience for their OTP implementation.
otp-middleware is a potential way for agencies to fill in those gaps. It provides:

- user management and storage,
- trip monitoring,
- and other features (TODO).

## Deployment
The easiest way to deploy otp-middleware is to use the included Docker Compose configuration.

There are three compose configs. All build upon the "standard" config. The `dev` config exposes the Mongo instance, and saves the database data outside of the container. The path the database data is saved to is by default set to the directory homebrew installs Mongo into. This can be changed to any Mongo installation directory. The `e2e` configuration also exposes the Mongo instance, and pre-seeds the database using the files in the `configurations/e2e-mongo-seed` directory. Please note that the seed files do not work out of the box -- valid auth0 users are needed, and their information needs to be pasted into the seed json files. Fields which require input are clearly marked.

The `configurations/default/docker.env.yml` contains a configuration file that makes it easy to get started. However, **there are fields which need to be manually set**. These fields are clearly marked within the file.

##### Running otp-middleware via Docker Compose
```bash
docker compose -f docker-compose.yml up
```

##### Running otp-middleware in dev mode via Docker Compose
```bash
docker compose -f docker-compose.yml -f docker-compose.dev.yml up
```

##### Running otp-middleware in e2e mode via Docker Compose
Note: you will need to populate `env.docker.yml`, `admin-e2euser.json`, `api-e2euser.json` and `aws-credentials`

```bash
docker compose -f docker-compose.yml -f docker-compose.e2e.yml up
```

OTP Middleware is also suitable for continuous deployment implementations.

## Development
To run otp-middleware in a local, development environment:

```bash
# Clone the repo.
git clone https://github.com/ibi-group/otp-middleware.git
cd otp-middleware
# Copy the config template, then make any changes to the file to 
# match your local configuration (e.g., database name).
cp configurations/default/env.yml.tmp configurations/default/env.yml
# At this point, you can either package the source code into an 
# executable jar, or import and run the code in an interactive 
# development environment. 
mvn package
java -jar target/otp-middleware.jar configurations/default/env.yml
```

It's also possible to run otp-middleware via Docker (see Deployment above)

## Configuration

### Auth0

TODO: Add Auth0 setup instructions.

#### Auth0 Scope

The requesting user type, which determines the level of authorization, is based on the scope provided as part of a user's bearer token. 
The bearer token 'scope' claim must contain one of `otp-user`, `api-user` or `admin-user` for the user to be correctly matched to a user held in the database.

##### Auth0 Scope Rule

A rule must be added to the Auth0 tenant for the scope provided by third parties to be available for authorization within the OTP-middleware.
This rule takes the scope value provided by the caller and adds it to the access token.  

```javascript
function (user, context, callback) {
    const req = context.request;
    // Retrieve scopes either from the parameters or body
    const requestedScopeString = (req.query && req.query.scope) || (req.body && req.body.scope);
    context.accessToken.scope = requestedScopeString;
    return callback(null, user, context);
}
```


### OTP Server Proxy Setup
The follow parameters are used to interact with an OTP server.

| Parameter | Description | Example |
| --- | --- | --- |
| OTP_API_ROOT | This is the address of the OTP server, including the root path to the OTP API, to which all OTP related requests will be sent to. | http://otp-server.example.com/otp | 
| OTP_PLAN_ENDPOINT | This defines the plan endpoint part of the requesting URL. If a request is made to this, the assumption is that a plan request has been made and that the response should be processed accordingly. | /plan |

### Trip Actions

OTP-middleware supports triggering certain actions when someone activates live tracking of a monitored trip
and reaches a location or is about to enter a path. Actions include location-sensitive API calls to notify various services.
In the context of live trip tracking, actions may include notifying transit vehicle operators or triggering traffic signals.

Trip actions are defined in the optional file `trip-actions.yml` in the same configuration folder as `env.yml`.
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

Known trigger classes below are in package `org.opentripplanner.middleware.triptracker.interactions`
and implement its `Interaction` interface:

| Class | Description |
| ----- | ----------- |
| UsGdotGwinnettTrafficSignalNotifier | Triggers select pedestrian signals in Gwinnett County, GA, USA |
#### Bus Notify Actions

Bus notifier actions are defined in the optional file `bus-notifier-actions.yml` in the same configuration folder as `env.yml`.
The file contains a list of actions defined by an agency ID and a fully-qualified trigger class:

```yaml
- agencyId: id1
  trigger: com.example.package.MyTriggerClass
```

Known trigger classes below are in package `org.opentripplanner.middleware.triptracker.interactions.busnotifiers`
and implement its `BusOperatorInteraction` interface:

| Class | Description                                                                  |
| ----- |------------------------------------------------------------------------------|
| UsRideGwinnettNotifyBusOperator | Triggers select route bus operator notifications in Gwinnett County, GA, USA |

### Monitored Components

This application allows you to monitor various system components (e.g., OTP API, OTP UI, and Data Tools) that work together 
to provide a trip planning service. Each of these should be defined in the config file in the list of 
`MONITORED_COMPONENTS` with the following properties:

| Parameter | Example | Description |
| --- | --- | --- |
| name | `datatools-server` | Name of the system component for display in the OTP Admin UI |
| bugsnagProjectId | `abcd1234` | Bugsnag project ID that maps to the system component. After [logging into Bugsnag](https://app.bugsnag.com), visit https://api.bugsnag.com/organizations/<ORGANIZATION_ID>/projects?sort=favorite&direction=asc&per_page=20 (make sure to add your Bugsnag organization ID) to view a list of projects with their IDs. | 

### Bugsnag

Bugsnag is used to report error events that occur within the otp-middleware application or 
OpenTripPlanner components that it is monitoring. 

#### Bugsnag Configuration Parameters

These values can be used as defined here (where applicable), or commented out, so the default values are used. Parameters 
that don't have default values (N/A) can be obtained by following the steps in the next section.

| Parameter | Default | Description |
| --- | --- | --- |
| BUGSNAG_API_KEY | N/A | Used to authenticate against Bugsnag's API. |
| BUGSNAG_EVENT_JOB_DELAY_IN_MINUTES | 1 | Frequency in minutes to obtain events. |
| BUGSNAG_EVENT_REQUEST_JOB_DELAY_IN_HOURS | 24 | Frequency in hours to trigger event requests. |
| BUGSNAG_PROJECT_NOTIFIER_API_KEY | N/A | Used to report project errors to Bugsnag. |
| BUGSNAG_REPORTING_WINDOW_IN_DAYS | 14 | The number of days in the past to start retrieving event information. |  
| BUGSNAG_WEBHOOK_PERMITTED_IPS | 104.196.245.109, 104.196.254.247 | Bugsnag IP addresses which webhook requests are expected to come from. |  


#### Bugsnag Setup
Where default parameters cannot be used, these steps describe how to obtain each compulsory parameter.

##### BUGSNAG_API_KEY
A bugsnag API key is a key that is unique to an individual Bugsnag user. This key can be obtained by logging into 
Bugsnag (https://app.bugsnag.com), clicking on settings (top right-hand corner) then `My account settings`. From here 
select `Personal auth tokens` and then `Generate new token`.

##### BUGSNAG_PROJECT_NOTIFIER_API_KEY
A Bugsnag project identifier key is unique to a Bugsnag project and allows errors to be saved against it. This key can 
be obtained by logging into Bugsnag (https://app.bugsnag.com), clicking on Projects (left side menu) and selecting the 
required project. Once selected, the notifier API key is presented.

##### Configure the Bugsnag Webhook
The Bugsnag webhook must be configured for your project, so that errors can be forwarded to the OTP Middleware and to
anyone subscribing to get email notifications when errors occur.

To configure the Bugsnag Webhook:
1) Select the desired project in the Bugsnag console.
2) Under "Integrations and email" select "Data forwarding".
3) Under "Available integrations" select "Webhook".
4) Enter the URL you would like Bugsnag to push project errors to e.g. <host>:<port>/api/bugsnagwebhook.

More information on the Bugsnag webhook can be found here:
https://docs.bugsnag.com/product/integrations/data-forwarding/webhook/

##### Whitelisting addresses from BUGSNAG_WEBHOOK_PERMITTED_IPS
In some restricted environments such as AWS Security Groups, you may need to whitelist the two Bugsnag IP addresses,
so that Bugsnag can post error notifications to OTP Middleware and that the errors appear in the admin dashboard.
Refer to your cloud service for whitelisting IP addresses.

### Connected Data Platform

#### AWS S3 Policy configuration
An IAM access management S3 policy is required in order for an IAM user to write/delete objects on the Connected Data 
Platform S3 bucket. 

The following permissions are required:
1) ListBucket
2) GetObject
3) DeleteObject
4) PutObject
5) PutObjectAcl

The following snippet is an example policy which can be used/modified to allow access to the CDP S3 bucket:
```bash
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Sid": "VisualEditor0",
      "Effect": "Allow",
      "Action": [
        "s3:PutObject",
        "s3:GetObject",
        "s3:ListBucket",
        "s3:DeleteObject",
        "s3:PutObjectAcl"
      ],
      "Resource": [
        "arn:aws:s3:::cdp-bucket-name/*",
        "arn:aws:s3:::cdp-bucket-name"
      ]
    }
  ]
}
```


## Testing

### End-to-end (E2E)

In order to run E2E tests, specific configuration and environment variables must be used.

Please note the e2e Dockerfile is __not__ used to run the otp-middleware e2e tests. These are instead used to instantiate a standard otp-middleware instance which can be used by other application's e2e tests. 

#### Auth0
The standard Auth0 configuration can be used for the server settings. However, some additional settings must be applied
in order for the server to get an oath token from Auth0 for creating authenticated requests. A private application
should be created for use in Auth0 following these instructions: https://auth0.com/docs/flows/call-your-api-using-resource-owner-password-flow

One special note is that the default directory must be set at the Account/tenant level. Otherwise, authorization will not
be successful. See this StackOverflow answer for more info: https://stackoverflow.com/a/43835563/915811

The special E2E client settings should be defined in `env.yml`:

| Parameter | Default | Description |
| --- | --- | --- |
| AUTH0_CLIENT_ID | N/A | Special E2E application client ID. |
| AUTH0_CLIENT_SECRET | N/A | Special E2E application client secret. |

**Note:** Just to reiterate, these are different from the server application settings and are only needed for E2E testing.

### env.schema.json values
| Key | Type | Required | Example | Description |
| --- | --- | --- | --- | --- |
| AUTH0_API_CLIENT | string | Required | test-auth0-client-id | API client id required to authenticate with Auth0. |
| AUTH0_API_SECRET | string | Required | test-auth0-secret | API secret id required to authenticate with Auth0. |
| AUTH0_DOMAIN | string | Required | test.auth0.com | Auth0 tenant URL. |
| AWS_PROFILE | string | Optional | default | AWS profile for credentials |
| AWS_API_SERVER | string | Optional | aws-api-id.execute-api.us-east-1.amazonaws.com | For generating the swagger document at runtime. Can be null, however that will prevent tools such as swagger-UI from submitting test requests to the API server. |
| AWS_API_STAGE | string | Optional | stage-name | For generating the swagger document at runtime. Can be null, however that will prevent tools such as swagger-UI from submitting test requests to the API server. |
| BUGSNAG_API_KEY | string | Required | 123e4567e89b12d3a4564266 | A valid Bugsnag authorization token. |
| BUGSNAG_EVENT_JOB_DELAY_IN_MINUTES | integer | Optional | 1 | Bugsnag event job frequency. |
| BUGSNAG_EVENT_REQUEST_JOB_DELAY_IN_HOURS | integer | Optional | 24 | Frequency in hours to trigger event requests. |
| BUGSNAG_PROJECT_NOTIFIER_API_KEY | string | Optional | 123e4567e89b12d3a4564266 | A valid Bugsnag project API key. |
| BUGSNAG_REPORTING_WINDOW_IN_DAYS | integer | Optional | 14 | Specifies how far in the past events should be retrieved. |
| CONNECTED_DATA_PLATFORM_S3_BUCKET_NAME | string | Optional | bucket-name | Specifies the S3 bucket name for the CDP trip history push. |
| CONNECTED_DATA_PLATFORM_S3_FOLDER_NAME | string | Optional | folder-name | Specifies the S3 folder name for the CDP trip history push. |
| CONNECTED_DATA_PLATFORM_TRIP_HISTORY_UPLOAD_JOB_FREQUENCY_IN_MINUTES | integer | Optional | 5 | CDP trip history upload frequency. |
| BUGSNAG_WEBHOOK_PERMITTED_IPS | string | Optional | 104.196.245.109, 104.196.254.247 | Bugsnag IP addresses which webhook requests are expected to come from. |
| DEFAULT_USAGE_PLAN_ID | string | Required | 123e45 | AWS API gateway default usage plan used when creating API keys for API users. |
| MAXIMUM_PERMITTED_MONITORED_TRIPS | integer | Optional | 5 | The maximum number of saved monitored trips. |
| MONGO_DB_NAME | string | Required | otp_middleware | The name of the OTP Middleware Mongo DB. |
| MONGO_HOST | string | Optional | localhost:27017 | Mongo host address. |
| MONGO_PASSWORD | string | Optional | password | Mongo DB password |
| MONGO_PROTOCOL | string | Optional | mongodb | Mongo DB protocol |
| MONGO_USER | string | Optional | username | Mongo DB user name |
| MONITORED_COMPONENTS | array | Optional | n/a | An array of monitored components. |
| NOTIFICATION_FROM_EMAIL | string | Optional | noreply@email.com | The from email address used in notification emails |
| NOTIFICATION_FROM_PHONE | string | Optional | +15551234 | The from phone number used in notification SMSs. The phone number must be surrounded with quotes to be correctly parsed as a String. |
| OTP_ADMIN_DASHBOARD_FROM_EMAIL | string | Optional | OTP Admin Dashboard <no-reply@email.com> | Config setting for linking to the OTP Admin Dashboard. |
| OTP_ADMIN_DASHBOARD_NAME | string | Optional | OTP Admin Dashboard | Config setting for linking to the OTP Admin Dashboard. |
| OTP_ADMIN_DASHBOARD_URL | string | Optional | https://admin.example.com | Config setting for linking to the OTP Admin Dashboard. |
| OTP_API_ROOT | string | Required | http://otp-server.example.com/otp | The URL of an operational OTP1 server. |
| OTP2_API_ROOT | string | Optional | http://otp2-server.example.com/otp | The URL of an operational OTP2 server. |
| OTP_PLAN_ENDPOINT | string | Optional | /routers/default/plan | The path to the OTP server trip planning endpoint. |
| OTP_TIMEZONE | string | Required | America/Los_Angeles | The timezone identifier that OTP is using to parse dates and times. OTP will use the timezone identifier that it finds in the first available agency to parse dates and times. |
| OTP_UI_NAME | string | Optional | Trip Planner | Config setting for linking to the OTP UI (trip planner). |
| OTP_UI_URL | string | Optional | https://plan.example.com | Config setting for linking to the OTP UI (trip planner). |
| PUSH_API_KEY | string | Optional | your-api-key | Key for Mobile Team push notifications internal API. |
| PUSH_API_URL | string | Optional | https://example.com/api/otp_push/sound_transit | URL for Mobile Team push notifications internal API. |
| SERVICE_DAY_START_HOUR | integer | Optional | 3 | Optional parameter for the hour (local time, 24-hr format) at which a service day starts. To make the service day change at 2am, enter 2. The default is 3am. |
| SPARKPOST_KEY | string | Optional | your-api-key | Get Sparkpost key at: https://app.sparkpost.com/account/api-keys |
| TRIP_TRACKING_UPDATE_FREQUENCY_SECONDS | integer | Optional | 5 | The expected frequency to receive live journey location data. |
| TRIP_TRACKING_MINIMUM_SEGMENT_TIME | integer | Optional | 5 | The minimum segment size in seconds for interpolated points. |
| TRIP_TRACKING_WALK_ON_TRACK_RADIUS | integer | Optional | 5 | The threshold in meters below which walking is considered on track. |
| TRIP_TRACKING_BICYCLE_ON_TRACK_RADIUS | integer | Optional | 10 | The threshold in meters below which cycling is considered on track. |
| TRIP_TRACKING_BUS_ON_TRACK_RADIUS | integer | Optional | 20 | The threshold in meters below which travelling by bus is considered on track. |
| TRIP_TRACKING_SUBWAY_ON_TRACK_RADIUS | integer | Optional | 100 | The threshold in meters below which travelling by subway is considered on track. |
| TRIP_TRACKING_RAIL_ON_TRACK_RADIUS | integer | Optional | 200 | The threshold in meters below which travelling by rail is considered on track. |
| TRIP_TRACKING_TRAM_ON_TRACK_RADIUS | integer | Optional | 100 | The threshold in meters below which travelling by tram is considered on track. |
| TRIP_INSTRUCTION_IMMEDIATE_RADIUS | integer | Optional | 2 | The radius in meters under which an immediate instruction is given. |
| TRIP_INSTRUCTION_UPCOMING_RADIUS | integer | Optional | 10 | The radius in meters under which an upcoming instruction is given. |
| TWILIO_ACCOUNT_SID | string | Optional | your-account-sid | Twilio settings available at: https://twilio.com/user/account |
| TWILIO_AUTH_TOKEN | string | Optional | your-auth-token | Twilio settings available at: https://twilio.com/user/account |
| US_GDOT_GWINNETT_PED_SIGNAL_API_HOST | string | Optional | http://host.example.com | Host server for the US GDOT Gwinnett County pedestrian signal controller API |
| US_GDOT_GWINNETT_PED_SIGNAL_API_PATH | string | Optional | /intersections/%s/crossings/%s/call | Optional relative path template to trigger a US GDOT Gwinnett County pedestrian signal |
| US_GDOT_GWINNETT_PED_SIGNAL_API_KEY | string | Optional | your-api-key | API key for the US GDOT Gwinnett County pedestrian signal controller |
| US_RIDE_GWINNETT_BUS_OPERATOR_NOTIFIER_API_URL | string | Optional | http://host.example.com | US Ride Gwinnett County bus notifier API. |
| US_RIDE_GWINNETT_BUS_OPERATOR_NOTIFIER_API_KEY | string | Optional | your-api-key | API key for the US Ride Gwinnett County bus notifier API. |
| US_RIDE_GWINNETT_BUS_OPERATOR_NOTIFIER_QUALIFYING_ROUTES | string | Optional | agency_id:route_id | A comma separated list of US Ride Gwinnett County routes that can be notified. |
| VALIDATE_ENVIRONMENT_CONFIG | boolean | Optional | true | If set to false, the validation of the env.yml file against this schema will be skipped. |
