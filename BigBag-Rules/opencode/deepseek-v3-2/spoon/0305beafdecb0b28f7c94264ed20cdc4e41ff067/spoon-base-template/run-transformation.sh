#!/bin/bash

# Run the MySQL Connector/J migration transformation
# Usage: ./run-transformation.sh <input-dir> <output-dir>

if [ "$#" -ne 2 ]; then
    echo "Usage: $0 <input-dir> <output-dir>"
    echo "Example: $0 /path/to/project/src /path/to/transformed/src"
    exit 1
fi

INPUT_DIR="$1"
OUTPUT_DIR="$2"

# Create output directory if it doesn't exist
mkdir -p "$OUTPUT_DIR"

# Run the transformation
java -cp "target/spoon-base-1.0-SNAPSHOT.jar:target/dependency/*" github.chains.Main "$INPUT_DIR" "$OUTPUT_DIR"