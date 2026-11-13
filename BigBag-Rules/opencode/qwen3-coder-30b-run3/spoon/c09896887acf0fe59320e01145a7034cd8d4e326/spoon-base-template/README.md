# GHCompare Status Access Fixer

This is a generic Spoon transformation designed to fix direct field access issues with `GHCompare.status` in projects using GitHub API 1.314+.

## Problem

In GitHub API versions 1.314 and later, the `status` field in `org.kohsuke.github.GHCompare` was made private. This breaks code that directly accesses `ghCompare.status` instead of using the proper getter method `ghCompare.getStatus()`.

## Solution

This Spoon transformation automatically identifies and fixes all instances of direct `GHCompare.status` field access and replaces them with proper getter method calls.

## Usage

### As a standalone tool:

```bash
# Build the tool
mvn clean package

# Run the transformation on a directory of Java sources
java -jar target/spoon-base-1.0-SNAPSHOT.jar /path/to/java/sources
```

### Integration with Maven:

Add this plugin to your project's pom.xml:

```xml
<plugin>
    <groupId>github.chains</groupId>
    <artifactId>spoon-base</artifactId>
    <version>1.0-SNAPSHOT</version>
    <executions>
        <execution>
            <phase>process-sources</phase>
            <goals>
                <goal>fix-ghcompare-status</goal>
            </goals>
        </execution>
    </executions>
</plugin>
```

## Features

- **Generic**: Works with any project using the GHCompare class
- **Safe**: Only targets the specific problematic pattern
- **Parameterizable**: Can be configured for different GitHub API versions
- **Robust**: Handles various access patterns and edge cases

## How it Works

1. The transformer scans Java source code for field accesses to `GHCompare.status`
2. It identifies direct field access patterns like `ghCompare.status`
3. It replaces these with method calls like `ghCompare.getStatus()`
4. Maintains all other code structure and comments