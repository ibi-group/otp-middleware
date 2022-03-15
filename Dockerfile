# syntax=docker/dockerfile:1
FROM openjdk:11
WORKDIR /middleware

# Grab latest dev build
RUN curl https://otp-middleware-builds.s3.amazonaws.com/dev/latest.txt --output latest.txt
RUN curl https://otp-middleware-builds.s3.amazonaws.com/dev/$(cat latest.txt) --output middleware.jar

# Launch server (relies on env.yml being placed in volume!)
# Try: docker run --publish 4567:4567 -v ~/env.yml:/config/env.yml otp-middleware-latest
CMD ["java", "-jar", "middleware.jar", "/config/env.yml"]
EXPOSE 4567