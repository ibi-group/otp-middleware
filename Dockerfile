# syntax=docker/dockerfile:1
FROM openjdk:11
WORKDIR /middleware

# Grab latest dev build
COPY target/otp-middleware.jar ./otp-middleware-1.0-SNAPSHOT.jar

# Launch server (relies on env.yml being placed in volume!)
# Try: docker run --publish 4567:4567 -v ~/env.yml:/config/env.yml otp-middleware-latest
CMD ["java", "-jar", "otp-middleware-1.0-SNAPSHOT.jar", "/config/env.yml"]
EXPOSE 4567