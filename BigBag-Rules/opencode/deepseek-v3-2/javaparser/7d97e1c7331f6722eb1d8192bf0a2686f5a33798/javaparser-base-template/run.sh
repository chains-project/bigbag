#!/bin/bash
# Create a runnable jar with dependencies
mvn clean compile assembly:single -q
java -cp "target/javaparser-1.0-SNAPSHOT-jar-with-dependencies.jar" github.chains.Main "$@"
