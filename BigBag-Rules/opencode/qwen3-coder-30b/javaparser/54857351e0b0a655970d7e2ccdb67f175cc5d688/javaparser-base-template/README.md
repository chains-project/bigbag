# zip4j Breaking Change Fixer

A generic JavaParser-based tool to fix breaking changes in zip4j 2.10.0 where the `net.lingala.zip4j.core` package was removed.

## Problem

In zip4j version 2.10.0, the `net.lingala.zip4j.core` package was removed, causing compilation errors for projects that import from this package:

```
package net.lingala.zip4j.core does not exist
```

## Solution

This tool automatically fixes the import statements by replacing:
- `import net.lingala.zip4j.core.ZipFile;` → `import net.lingala.zip4j.ZipFile;`
- `import net.lingala.zip4j.core.AESDecrypter;` → `import net.lingala.zip4j.crypto.AESDecrypter;`

## Usage

### As a standalone JAR

```bash
# Build the JAR
mvn clean package

# Fix imports in a project
java -jar target/zip4j-breaking-change-fixer-1.0.0.jar /path/to/your/project
```

### As a library

Add to your project's dependencies:
```xml
<dependency>
    <groupId>io.qameta.allure</groupId>
    <artifactId>zip4j-breaking-change-fixer</artifactId>
    <version>1.0.0</version>
</dependency>
```

Then use programmatically:
```java
Zip4jBreakingChangeFixer.main(new String[]{"/path/to/your/project"});
```

## Features

- **Generic**: Works for any project with the same breaking change
- **Safe**: Preserves original code formatting using JavaParser's lexical preservation
- **Non-destructive**: Only modifies files that actually need fixing
- **Recursive**: Processes all Java files in the project directory tree

## How It Works

The tool uses JavaParser to:
1. Parse all Java files in the project
2. Identify imports from `net.lingala.zip4j.core.*`
3. Replace them with the correct `net.lingala.zip4j.*` imports
4. Preserve all existing code formatting and structure