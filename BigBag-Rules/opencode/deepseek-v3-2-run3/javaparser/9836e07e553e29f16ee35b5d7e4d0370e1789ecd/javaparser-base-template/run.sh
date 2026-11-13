#!/bin/bash
java -cp "target/classes:$(find ~/.m2/repository -name "*.jar" | tr '\n' ':')" github.chains.Main "$@"
