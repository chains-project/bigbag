# Generic JavaParser Transformation Template

## Overview

This template provides a framework for creating generic JavaParser transformations to fix breaking dependency changes in Maven projects.

## Problem Addressed

This solution specifically addresses breaking changes caused by dependency updates that remove classes like:
- `org.cactoos.io.BytesOf`
- `org.cactoos.text.HexOf`

## Usage

### 1. Build the transformation tool
```bash
cd /workspace/javaparser-base-template
mvn clean compile
```

### 2. Apply transformation to a project
```bash
java -cp target/classes:$(mvn dependency:build-classpath -Dmdep.outputFile=/dev/stdout -q) github.chains.Main /path/to/target/project
```

## Solution Implemented

The transformation:
1. Removes `org.cactoos.*` imports
2. Replaces `HexOf(...)` calls with `HexFormat.of().formatHex(...)`
3. Replaces `BytesOf(...)` calls with direct byte handling

## Example Fix Applied

In the docker-adapter project:
- `new HexOf(new BytesOf(sha.digest())).asString()` 
- Became: `HexFormat.of().formatHex(sha.digest())`

## Configuration

Update the `pom.xml` to include the appropriate JavaParser dependencies:
```xml
<dependencies>
    <dependency>
        <groupId>com.github.javaparser</groupId>
        <artifactId>javaparser-core</artifactId>
        <version>3.27.1</version>
    </dependency>
</dependencies>
```

## Testing

The transformation was tested on the docker-adapter project and resolved compilation errors.