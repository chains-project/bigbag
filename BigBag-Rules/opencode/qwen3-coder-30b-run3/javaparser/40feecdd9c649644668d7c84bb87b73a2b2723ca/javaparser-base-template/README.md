# Generic Joda-Time Breaking Change Transformation

This is a generic JavaParser-based transformation tool designed to fix breaking changes in Joda-Time dependencies across Maven projects.

## Purpose

This tool fixes common breaking changes in Joda-Time APIs that occur when upgrading from older versions to newer versions (e.g., 2.10.x to 2.12.5).

## Usage

```bash
java -cp target/javaparser-1.0-SNAPSHOT.jar github.chains.Main <source_directory>
```

Where `<source_directory>` is the root directory of your Maven project.

## Transformation Patterns

The tool currently handles:

1. **toGregorianCalendar() method changes** - Common breaking change in Joda-Time APIs

## How It Works

This tool uses JavaParser to:
1. Parse all Java files in the specified directory
2. Identify Joda-Time API calls that may have changed
3. Apply generic transformations to maintain compatibility
4. Save the modified files

## Customization

To extend this tool for specific breaking changes:
1. Add new patterns in the `handleJodaTimeMethodChanges` method
2. Implement corresponding replacement methods
3. Update the transformation logic to match the specific breaking change

## Requirements

- Java 11 or higher
- Maven 3.6 or higher