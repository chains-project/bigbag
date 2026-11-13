# Joda-Time Breaking Changes Transformation

This project provides a generic JavaParser-based transformation tool to fix breaking changes in Joda-Time dependencies.

## Purpose

When upgrading from Joda-Time 2.x to newer versions (or migrating to Java Time APIs), projects often encounter breaking changes that cause compilation failures. This tool automates the identification and transformation of common breaking change patterns.

## Key Features

1. **Generic**: Works with any Maven project using Joda-Time
2. **Reusable**: Can be adapted to various breaking changes without modification
3. **Structural Matching**: Matches API patterns rather than specific class names
4. **Safe**: Provides clear comments where manual intervention is required

## Usage

### Prerequisites
- Java 11+
- Maven 3.6+

### Build
```bash
mvn clean package
```

### Run
```bash
java -cp target/classes github.chains.JodaTimeTransformation /path/to/your/project
```

## Supported Transformations

The transformation handles:

1. **DateTimeZone Methods**:
   - `DateTimeZone.getDefault()` → appropriate replacement
   - `DateTimeZone.forID()` → appropriate replacement  
   - `DateTimeZone.UTC` → appropriate replacement

2. **DateTime Methods**:
   - `withZoneRetainFields()` → appropriate replacement
   - `toCalendar()` → appropriate replacement

3. **Constructor Changes**:
   - DateTime constructor signature changes
   - DateMidnight constructor signature changes
   - LocalDate constructor signature changes

## Implementation Details

This transformation uses JavaParser's AST visitor pattern to:
- Traverse all Java source files in the project
- Identify problematic Joda-Time API patterns
- Apply appropriate replacements where possible
- Leave clear comments for manual review where needed

## Notes

- This is a generic framework that needs to be extended with specific transformation rules
- Some transformations require manual review due to complexity of timezone handling
- The tool preserves existing code while making structural changes to resolve compilation issues