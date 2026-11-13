# Generic Spoon Transformation for SNMP4J-Agent 3.6.5 Breaking Change

## Problem Statement

The SNMP4J-Agent library version 3.6.5 introduced a breaking API change where the `getRegistry()` method in `CommandProcessor` changed its return type signature:

**Before:** `SortedMap<MOScope, ManagedObject>`
**After:** `SortedMap<MOScope, ManagedObject<?>>`

This causes compilation errors in projects that use this API, such as the `snmpman` project:

```
incompatible types: java.util.SortedMap<org.snmp4j.agent.MOScope,org.snmp4j.agent.ManagedObject<?>> cannot be converted to java.util.SortedMap<org.snmp4j.agent.MOScope,org.snmp4j.agent.ManagedObject>
```

## Solution Overview

This repository provides a generic Spoon transformation tool that can automatically fix this exact type issue across any Java project. The transformation identifies code patterns like:

```java
SortedMap<MOScope, ManagedObject> reg = server.getRegistry();
```

And transforms them to:

```java
SortedMap<MOScope, ManagedObject<?>> reg = server.getRegistry();
```

## How to Apply the Fix

1. **Compile the transformation tool:**
```bash
cd /workspace/spoon-base-template
mvn clean package -DskipTests
```

2. **Run the transformation on the affected project:**
```bash
java -jar target/spoon-base-1.0-SNAPSHOT.jar /workspace/snmpman/snmpman/src/main/java
```

3. **The fixed files will be placed in:** `/workspace/snmpman/snmpman/src/main/java/fixed/`

## Technical Details

The transformation works by:
- Using Spoon's AST parsing capabilities
- Identifying variable declarations with `SortedMap<MOScope, ManagedObject>` types
- Converting to parameterized types `SortedMap<MOScope, ManagedObject<?>>`
- Preserving all other code structure and comments

## Usage for Other Projects

This transformation is generic and reusable for any Maven project affected by the same breaking change:

1. Compile the tool as above
2. Run it on any Java source directory with the problematic code
3. The tool creates a 'fixed' subdirectory with corrected files

## Project Structure

- `src/main/java/github/chains/Main.java`: The transformation logic
- `pom.xml`: Maven configuration with Spoon dependency
- `README.md`: Documentation (this file)

## Limitations

- Only fixes the specific type conversion issue described
- Does not handle other potential breaking changes in the API
- Requires manual verification that the transformation produces correct results

## License

MIT License