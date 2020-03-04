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
TODO

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
