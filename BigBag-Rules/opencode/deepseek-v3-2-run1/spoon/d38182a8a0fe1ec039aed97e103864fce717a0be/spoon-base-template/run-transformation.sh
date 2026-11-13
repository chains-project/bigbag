#!/bin/bash
cd /workspace/spoon-base-template
java -cp "target/spoon-base-1.0-SNAPSHOT.jar:target/dependency/*" github.chains.Main "$@"