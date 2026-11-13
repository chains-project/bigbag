#!/bin/bash
CP="target/classes:$(find ~/.m2/repository -path "*/spoon-core/*.jar" -o -path "*/commons-io/*.jar" -o -path "*/eclipse.jdt.core/*.jar" | tr '\n' ':')"
java -cp "$CP" github.chains.Main "$@"
