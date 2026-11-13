# Hamcrest Constructor Fix Transformation

This project implements a Java-based transformation to fix Hamcrest constructor breaking changes in the docker-adapter project.

## Problem
The Hamcrest library underwent breaking changes where constructors that previously accepted `(boolean, String)` parameters now only accept `String` parameters:

- `new StringContains(true, "expected")` → `new StringContains("expected")`
- `new StringContains(false, "expected")` → `new StringContains("expected")`
- `new StringStartsWith(false, "expected")` → `new StringStartsWith("expected")`

## Solution
The transformation uses a regex-based approach to find and fix these problematic constructor calls:

1. Finds all occurrences of the problematic patterns
2. Removes the boolean parameter while preserving the string parameter
3. Applies the transformation recursively to all Java files in a given directory

## Implementation Details

### File Location
The main implementation is located at:
`/workspace/javaparser-base-template/src/main/java/github/chains/Main.java`

### Key Features
- Processes all Java files in a specified directory
- Handles the three specific constructor patterns mentioned
- Preserves unchanged code
- Provides verbose output of transformation results

### Usage
```bash
# Build the project
mvn package

# Apply transformation to a directory
java -cp target/classes:<dependency_jars> github.chains.Main /path/to/source/directory
```

## Limitations
Due to environment constraints, this solution uses regex-based pattern matching rather than a full JavaParser approach. While functional, a JavaParser-based solution would be more robust in handling edge cases and complex parsing scenarios.

## Testing
The transformation was tested on a sample file with the following before/after results:

**Before:**
```java
StringContains sc1 = new StringContains(true, "expected");
StringContains sc2 = new StringContains(false, "expected");
StringStartsWith ss1 = new StringStartsWith(false, "expected");
```

**After:**
```java
StringContains sc1 = new StringContains("expected");
StringContains sc2 = new StringContains("expected");
StringStartsWith ss1 = new StringStartsWith("expected");
```

The unchanged constructors remain untouched:
```java
StringContains sc3 = new StringContains("expected");
StringStartsWith ss2 = new StringStartsWith("expected");
```

## Dependencies
- Java 11 or higher
- Maven for building

## How to Use
1. Compile the project: `mvn compile`
2. Package the JAR: `mvn package`
3. Run the transformation: `java -cp target/classes:<dependency_jars> github.chains.Main <source_directory>`