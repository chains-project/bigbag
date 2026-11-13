#!/bin/bash
# Script to run the Spoon transformation
# Usage: ./run-transformation.sh <source-directory>

if [ $# -lt 1 ]; then
    echo "Usage: $0 <source-directory>"
    exit 1
fi

SOURCE_DIR="$1"

# Find the Spoon JAR
SPOON_JAR=$(find ~/.m2/repository/fr/inria/gforge/spoon/spoon-core -name "*.jar" | head -1)
BASE_JAR="/workspace/spoon-base-template/target/spoon-base-1.0-SNAPSHOT.jar"

if [ ! -f "$SPOON_JAR" ]; then
    echo "Spoon JAR not found in Maven repository"
    exit 1
fi

if [ ! -f "$BASE_JAR" ]; then
    echo "Base JAR not found: $BASE_JAR"
    exit 1
fi

echo "Running transformation on: $SOURCE_DIR"
java -cp "$BASE_JAR:$SPOON_JAR" github.chains.Main "$SOURCE_DIR"