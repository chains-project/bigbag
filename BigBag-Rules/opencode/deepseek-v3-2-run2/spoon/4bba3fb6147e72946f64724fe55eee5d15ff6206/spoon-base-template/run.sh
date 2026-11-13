#!/bin/bash
cd /workspace/spoon-base-template
java -cp "target/spoon-base-1.0-SNAPSHOT.jar:$(find ~/.m2/repository -name '*.jar' 2>/dev/null | tr '\n' ':')" github.chains.Main "$@"