FROM camunda/connectors:8.8.2

# Copy the connector JAR
COPY target/pdf-merge-split-connector-0.1.0-SNAPSHOT.jar /opt/app/

# The base image will automatically pick up the connector
