FROM maven:3.8.7-ibm-semeru-17-focal as build
COPY . /app/
WORKDIR /app/
RUN mvn clean package


FROM icr.io/appcafe/open-liberty:kernel-slim-java17-openj9-ubi

COPY --chown=1001:0 /src/main/liberty/config /config
RUN features.sh

COPY --from=build --chown=1001:0 /app/target/*.war /config/apps
RUN configure.sh
EXPOSE 9080 

