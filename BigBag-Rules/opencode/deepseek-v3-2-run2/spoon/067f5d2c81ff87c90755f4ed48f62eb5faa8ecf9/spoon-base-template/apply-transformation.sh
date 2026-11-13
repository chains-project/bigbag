#!/bin/bash

# Apply Spoon transformation
cd /workspace/spoon-base-template
mvn compile exec:java -Dexec.mainClass="github.chains.Main" -Dexec.args="/workspace/singer"

# Check if transformation created output
if [ -d "temp-output" ]; then
    echo "Transformation completed. Copying transformed files..."
    
    # Copy transformed Java files back to original location
    find temp-output -name "*.java" -type f | while read file; do
        # Get relative path from temp-output
        rel_path="${file#temp-output/}"
        # Original file path
        orig_file="/workspace/singer/$rel_path"
        
        # Check if original file exists
        if [ -f "$orig_file" ]; then
            echo "Updating: $orig_file"
            cp "$file" "$orig_file"
        fi
    done
    
    echo "Transformation applied successfully!"
else
    echo "No transformation output found."
fi