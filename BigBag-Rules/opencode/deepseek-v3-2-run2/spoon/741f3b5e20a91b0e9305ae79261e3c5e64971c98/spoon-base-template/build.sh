#!/bin/bash

# Build script for Spoon transformation

echo "Cleaning previous build..."
rm -rf target
mkdir -p target/classes

echo "Downloading dependencies..."
cd /workspace/spoon-base-template
mvn dependency:copy-dependencies -DoutputDirectory=target/dependency

echo "Compiling corrected transformation..."
javac -cp "target/dependency/*" \
      -d target/classes \
      src/main/java/github/chains/CorrectJacksonExceptionMigration.java

echo "Creating JAR file..."
jar cvfe target/jackson-exception-migration.jar github.chains.CorrectJacksonExceptionMigration \
    -C target/classes .

echo "Build complete! JAR file: target/jackson-exception-migration.jar"
echo ""
echo "Usage:"
echo "  java -jar target/jackson-exception-migration.jar <source-directory> [output-directory]"
echo ""
echo "Example:"
echo "  java -jar target/jackson-exception-migration.jar ./src"