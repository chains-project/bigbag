# Generic SLF4J Version Compatibility Fix for Logback 1.4.4+

## Problem Summary

When upgrading logback-classic from older versions to 1.4.4, projects encounter compilation errors due to API compatibility issues with SLF4J versions. The error "cannot access org.slf4j.spi.LoggingEventAware" occurs because:

1. Logback Classic 1.4.4 implements the `LoggingEventAware` interface
2. This interface was introduced in SLF4J 2.0.0
3. Projects using older SLF4J versions (1.7.x) fail to compile

## Solution

The generic transformation rule updates SLF4J dependencies to version 2.0.9 to ensure compatibility with logback-classic 1.4.4:

### Required Dependency Updates

1. **slf4j-api** from 1.7.x to 2.0.9
2. **jul-to-slf4j** from 1.7.x to 2.0.9  
3. **log4j-over-slf4j** from 1.7.x to 2.0.9
4. **jcl-over-slf4j** from 1.7.x to 2.0.9

## Generic Transformation Rule

The transformation works by:

1. **Scanning pom.xml files** for SLF4J dependency declarations
2. **Identifying outdated versions** (1.7.x, 1.6.x, 1.5.x)
3. **Updating to version 2.0.9** for full compatibility
4. **Preserving all other dependency configurations**

## Usage

```bash
java -cp target/classes github.chains.Main /path/to/maven/project
```

## Benefits

- **Generic**: Works with any Maven project affected by this issue
- **Reusable**: Can be applied to multiple projects without modification
- **Safe**: Only updates SLF4J dependencies, preserves other configurations
- **Automated**: Eliminates manual editing of pom.xml files

## Implementation Details

The transformation uses regex pattern matching to identify and replace version numbers in SLF4J dependency declarations. It specifically targets:
- `<groupId>org.slf4j</groupId>` 
- `<artifactId>slf4j-api</artifactId>`
- `<artifactId>jul-to-slf4j</artifactId>`
- `<artifactId>log4j-over-slf4j</artifactId>`
- `<artifactId>jcl-over-slf4j</artifactId>`

This approach ensures that any Maven project using logback-classic 1.4.4+ will be compatible with the updated SLF4J API.