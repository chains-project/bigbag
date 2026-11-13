#!/bin/bash
# Simple runner script for the Spoon transformation

cd /workspace/spoon-base-template

# Find all dependencies JARs
DEPS=$(find ~/.m2/repository -name "*.jar" -path "*spoon*" -o -name "*.jar" -path "*eclipse*" | tr '\n' ':')
DEPS="$DEPS:target/spoon-base-1.0-SNAPSHOT.jar"

echo "Running transformation on: $1"
java -cp "$DEPS" github.chains.Main "$1"