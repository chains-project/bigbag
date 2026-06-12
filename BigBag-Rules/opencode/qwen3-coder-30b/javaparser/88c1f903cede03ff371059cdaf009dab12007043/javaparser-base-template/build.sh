#!/bin/bash

# Simple build script for the zip4j transformation tool

echo "Building zip4j transformation tool..."

# Create target directory if it doesn't exist
mkdir -p target

# Compile the Java file
javac -cp "lib/*:." src/main/java/github/chains/Main.java -d target/

echo "Build complete. Run with: java -cp \"target:lib/*\" github.chains.Main <file-path>"