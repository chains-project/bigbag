# Generic Spoon Transformation for XSeries 8.6.0 Breaking Change

## Problem
The XSeries library version 8.6.0 introduced a breaking change:
- **Old API**: `XEnchantment.parseEnchantment()`
- **New API**: `XEnchantment.getEnchant()`

## Solution
This project provides a generic Spoon transformation that can automatically fix this breaking change in any Maven project.

## Files
1. `src/main/java/github/chains/Main.java` - Spoon-based transformation (complex but more precise)
2. `src/main/java/github/chains/SimpleFix.java` - Simple file-based transformation (recommended)

## Usage

### Method 1: SimpleFix (Recommended)
```bash
cd /workspace/spoon-base-template
javac src/main/java/github/chains/SimpleFix.java
java -cp "src/main/java" github.chains.SimpleFix /path/to/your/project/src/main/java
```

### Method 2: Spoon Transformation
```bash
cd /workspace/spoon-base-template
mvn compile exec:java -Dexec.mainClass="github.chains.Main" -Dexec.args="/path/to/your/project/src/main/java"
```

## How It Works
1. The transformation scans all `.java` files in the specified directory
2. Finds all occurrences of `.parseEnchantment()`
3. Replaces them with `.getEnchant()`
4. Preserves all other code structure

## Generic Pattern
This transformation follows a generic pattern that can be adapted for other breaking changes:

**Breaking Change Pattern:**
- Old: `ClassName.oldMethodName()`
- New: `ClassName.newMethodName()`

**To adapt for other changes:**
1. Update the search pattern in `SimpleFix.java`
2. Update the replacement pattern
3. Recompile and run

## Example Output
```
Scanning for parseEnchantment() calls in: /workspace/WorldwideChat/src/main/java
Processing: /path/to/file.java
  Replaced 1 occurrence(s)
...
Summary:
  Files modified: 6
  Total replacements: 16
  Transformation completed!
```

## Testing
After applying the transformation, test your project:
```bash
cd /path/to/your/project
mvn compile
mvn test
```

## Limitations
- The simple fix uses string replacement and may miss complex cases
- The Spoon transformation is more precise but may have issues with certain codebases
- Always backup your code before running transformations