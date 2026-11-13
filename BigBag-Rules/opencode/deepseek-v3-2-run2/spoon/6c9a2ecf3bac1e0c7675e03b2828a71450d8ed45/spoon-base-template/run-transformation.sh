#!/bin/bash
cd /workspace/spoon-base-template
CP="target/spoon-base-1.0-SNAPSHOT.jar"
for jar in target/dependency/*.jar; do
    CP="$CP:$jar"
done
java -cp "$CP" github.chains.Main "$@"