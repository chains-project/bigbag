#!/bin/bash
cd /workspace/javaparser-base-template
java -cp "target/javaparser-1.0-SNAPSHOT.jar:target/dependency/*" github.chains.Main "$@"