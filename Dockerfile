FROM camunda/connectors:8.8.3

# Copy the connector JAR
COPY target/pdf-merge-split-connector-1.3.0.jar /opt/app/

# The base image will automatically pick up the connector
