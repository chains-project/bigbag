#!/bin/bash
# Wrapper script to run the PubNub transformation

CLASS_PATH="target/spoon-base-1.0-SNAPSHOT.jar:$(find ~/.m2/repository -name '*.jar' 2>/dev/null | grep -E '(spoon-core|commons-io|eclipse-jdt|jsap|slf4j-api)' | tr '\n' ':')"

java -cp "$CLASS_PATH" github.chains.Main "$@"