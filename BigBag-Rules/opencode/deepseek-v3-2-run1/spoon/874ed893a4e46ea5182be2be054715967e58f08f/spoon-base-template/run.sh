#!/bin/bash
# Run the Spoon transformation
# Usage: ./run.sh <source-directory>

cd /workspace/spoon-base-template
java -cp "target/classes:$(mvn dependency:build-classpath -q -DincludeScope=runtime)" github.chains.Main "$@"