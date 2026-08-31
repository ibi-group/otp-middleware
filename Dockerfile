# syntax=docker/dockerfile:1
FROM maven:3.9.15-eclipse-temurin-25
WORKDIR /middleware

ARG JAR_VERSION=1.0-SNAPSHOT

# Grab latest dev build/
# Append a version/commit number that will be used in swagger.
COPY target/otp-middleware.jar ./otp-middleware-$JAR_VERSION.jar

# Generate launch script
RUN echo "java -jar otp-middleware-$JAR_VERSION.jar /config/env.yml \$@" >> ./start-middleware.sh
RUN chmod +x ./start-middleware.sh

# Launch server (relies on env.yml being placed in volume!)
ENTRYPOINT ["sh", "./start-middleware.sh"]
CMD ["--endpoints", "yes"]
EXPOSE 4567
