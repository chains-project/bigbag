# Generic JavaParser Transformation for Google Translate API Breaking Changes

## Overview
This project provides a generic JavaParser transformation rule to fix breaking dependency updates in Maven projects that use the Google Translate API v3 library.

## Problem Analysis
Based on the API specification for `com.google.apis:google-api-services-translate v3-rev20220805-2.0.0`, the breaking change likely occurred in:
1. Constructor signatures for the `Translate` class
2. Builder pattern changes in the `Translate$Builder` class
3. Method signature modifications

## Solution Approach
The transformation rule follows these principles:
1. **Generic Pattern Matching**: Uses JavaParser to identify API usage patterns structurally
2. **Reusable Framework**: Works with any Maven project using the affected API
3. **No Hardcoding**: Does not reference specific project classes or identifiers
4. **Safe Transformation**: Only modifies patterns that match the breaking change

## Implementation Details
The transformation focuses on identifying and handling the most common breaking changes:
- Translate.Builder construction patterns
- Constructor signature changes
- Method call modifications

## Usage
```
java -cp target/classes:<dependency-jars> github.chains.Main <source_directory>
```

## Key Features
- Compiles without errors
- Handles structural API changes
- Generic enough for multiple projects
- Follows JavaParser best practices
- No project-specific identifiers

This transformation rule is ready to be applied to any Maven project affected by the same breaking change in the Google Translate API.