#!/bin/bash

# Script to fix ScriptResult API breaking change in Maven projects
# Usage: ./fix-scriptresult.sh <source-directory>

SOURCE_DIR="${1:-.}"

echo "Fixing ScriptResult usage in $SOURCE_DIR"

# Find all Java files that might contain ScriptResult usage
find "$SOURCE_DIR" -name "*.java" -type f | while read -r file; do
    # Check if file contains ScriptResult
    if grep -q "ScriptResult" "$file"; then
        echo "Processing $file"
        
        # Backup the file
        cp "$file" "$file.backup"
        
        # Replace ScriptResult constructor usage
        sed -i 's/new ScriptResult(/(/g' "$file"
        
        # Replace getJavaScriptResult() calls
        sed -i 's/\.getJavaScriptResult()/)/g' "$file"
        
        # Remove unused import
        sed -i '/import com\.gargoylesoftware\.htmlunit\.ScriptResult;/d' "$file"
        
        echo "  Fixed $file"
    fi
done

echo "Fix complete. Backups saved with .backup extension."