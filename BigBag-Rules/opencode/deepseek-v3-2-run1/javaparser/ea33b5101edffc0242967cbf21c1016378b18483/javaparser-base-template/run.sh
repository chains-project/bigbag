#!/bin/bash
cd /workspace/javaparser-base-template
mvn exec:java -Dexec.mainClass="github.chains.Main" -Dexec.args="$1"