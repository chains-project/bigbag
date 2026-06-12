#!/bin/bash
mvn exec:java -Dexec.mainClass="github.chains.Main" -Dexec.args="$1"
