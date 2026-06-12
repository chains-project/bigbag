# Zip4j Package Rename Transformer

This is a generic transformation tool to fix the breaking API change in zip4j version 2.11.0 where the package structure changed from:
- `net.lingala.zip4j.core.ZipFile` to `net.lingala.zip4j.ZipFile`

## Problem

In zip4j version 2.11.0, the `ZipFile` class was moved from the `net.lingala.zip4j.core` package to the `net.lingala.zip4j` package. This breaks existing code that imports and uses `net.lingala.zip4j.core.ZipFile`.

## Solution

This transformation tool automatically updates all Java files to use the new package structure:
1. Updates import statements
2. Updates class references
3. Handles all common usage patterns

## Usage

### For the Allure Maven project (already fixed):

The Allure Maven project has already been fixed by changing:
```java
import net.lingala.zip4j.core.ZipFile;
```
to:
```java
import net.lingala.zip4j.ZipFile;
```

### For other projects:

1. Compile the transformation tool:
```bash
mvn compile
```

2. Run the transformation on your Java files:
```bash
java -cp target/classes github.chains.Main /path/to/your/JavaFile.java
```

## How it works

The tool uses JavaParser to:
1. Parse Java source code
2. Identify import statements for `net.lingala.zip4j.core.ZipFile`
3. Replace them with `net.lingala.zip4j.ZipFile`
4. Save the updated file

## Generic Application

This transformation rule can be applied to any Maven project that uses zip4j version 2.11.0 or later and has the old import pattern.

## Files Modified

- `/workspace/allure-maven/src/main/java/io/qameta/allure/maven/AllureCommandline.java` - Fixed import statement