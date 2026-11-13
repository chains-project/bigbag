# Maven Archiver API Transformation Guide

## Problem Statement

This project provides a generic Spoon transformation to address breaking changes in Maven Archiver API. When upgrading from older versions of Maven Archiver to 3.6.0, developers often encounter compilation errors due to deprecated methods and changed method signatures.

## Breaking Changes Addressed

### 1. Deprecated Methods
- `configureReproducible()` → `configureReproducibleBuild()`
- `parseOutputTimestamp()` → `parseBuildOutputTimestamp()`

### 2. Method Signature Changes
Several methods in MavenArchiver class had signature changes that affect compatibility.

## Solution Overview

This transformation uses Spoon to:
1. Parse Java source code statically
2. Identify Maven Archiver API usage patterns
3. Detect deprecated method calls and signature mismatches
4. Generate actionable reports for developers

## Usage Instructions

### Running the Transformation

```bash
# Compile the transformation
mvn compile

# Run the analysis on a source directory
java -cp target/classes github.chains.MavenArchiverDetectionTool /path/to/source/code
```

### Integrating with Build Process

Add this to your Maven build configuration:

```xml
<plugin>
    <groupId>org.codehaus.mojo</groupId>
    <artifactId>exec-maven-plugin</artifactId>
    <version>3.1.0</version>
    <executions>
        <execution>
            <id>analyze-maven-archiver</id>
            <phase>compile</phase>
            <goals>
                <goal>java</goal>
            </goals>
            <configuration>
                <mainClass>github.chains.MavenArchiverDetectionTool</mainClass>
                <arguments>
                    <argument>${project.basedir}/src/main/java</argument>
                </arguments>
            </configuration>
        </execution>
    </executions>
</plugin>
```

## Implementation Details

### Key Components

1. **MavenArchiverProcessor** - Main processor that detects API usage
2. **MavenArchiverDetectionTool** - Standalone analysis tool
3. **Generic Spoon Transformation** - Framework that can be extended

### How It Works

1. Spoon parses Java source code into an abstract syntax tree (AST)
2. The processor filters for invocations on MavenArchiver classes
3. Deprecated method calls are flagged with suggested replacements
4. Locations are reported for easy navigation

## Extending the Solution

To add support for additional breaking changes, modify:

1. `MAVEN_ARCHIVER_CLASSES` - Add new Maven Archiver classes
2. `DEPRECATED_METHODS` - Add new deprecated methods
3. Update `process()` method to provide guidance for new methods

## Limitations

1. This is a static analysis tool and cannot guarantee runtime behavior
2. Some breaking changes may require manual intervention beyond method replacement
3. Complex refactoring patterns may not be fully detected

## Future Enhancements

1. Add automatic fix capabilities using Spoon templates
2. Support for more Maven Archiver versions
3. Integration with Maven build lifecycle
4. Better reporting and visualization tools