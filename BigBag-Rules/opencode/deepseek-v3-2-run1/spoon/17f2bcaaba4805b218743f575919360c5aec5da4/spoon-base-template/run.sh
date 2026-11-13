#!/bin/bash
# Build the project first
mvn clean compile

# Create output directory
mkdir -p /workspace/spoon-output

# Run the transformation using Maven exec plugin
mvn exec:java -Dexec.mainClass="github.chains.Main" \
    -Dexec.args="/workspace/PGS/src/main/java /workspace/spoon-output"