# Generic JavaParser Transformation for Breaking Dependency Changes

This transformation tool helps fix breaking changes in Maven projects caused by dependency updates.

## How It Works

This tool analyzes Java source code files and detects method calls to APIs that have changed signatures or behaviors in newer versions of dependencies.

## Usage

```bash
mvn compile exec:java -Dexec.mainClass="github.chains.Main" -Dexec.args="/path/to/source/directory"
```

## Customization

To handle specific breaking changes, modify the `handleBreakingChange()` method in `Main.java` to detect and transform specific API calls.

## Features

- Generic approach that can be adapted for various breaking changes
- Handles method signature modifications
- Safe to use across multiple projects
- Does not require project-specific knowledge