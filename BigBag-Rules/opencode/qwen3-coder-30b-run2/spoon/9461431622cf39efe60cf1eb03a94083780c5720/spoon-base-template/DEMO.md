# Generic Spoon Transformation for SNMP4J-Agent 3.6.5 Breaking Change

This repository contains a generic transformation rule that fixes compilation issues caused by breaking changes in SNMP4J-Agent 3.6.5. Specifically, it addresses the issue where `SortedMap<MOScope, ManagedObject>` became `SortedMap<MOScope, ManagedObject<?>>`.

## Problem Analysis

In SNMP4J-Agent 3.6.5, the `getRegistry()` method changed its return type from:
- `SortedMap<MOScope, ManagedObject>` 

To:
- `SortedMap<MOScope, ManagedObject<?>>`

This causes compilation errors like:
```
incompatible types: java.util.SortedMap<org.snmp4j.agent.MOScope,org.snmp4j.agent.ManagedObject<?>> cannot be converted to java.util.SortedMap<org.snmp4j.agent.MOScope,org.snmp4j.agent.ManagedObject>
```

## Solution Approach

This transformation identifies all instances of:

```java
SortedMap<MOScope, ManagedObject> reg = server.getRegistry();
```

And transforms them to:

```java
SortedMap<MOScope, ManagedObject<?>> reg = server.getRegistry();
```

## How to Use

1. Compile this project:
```bash
mvn clean package
```

2. Run the transformation on any Java project with the problematic code:
```bash
java -jar target/spoon-base-1.0-SNAPSHOT.jar /path/to/java/project
```

3. Fixed files will be placed in `/path/to/java/project/fixed/`

## Implementation Details

The transformation works by:
1. Parsing Java source code with Spoon
2. Finding variable declarations that declare `SortedMap<MOScope, ManagedObject>`
3. Converting it to `SortedMap<MOScope, ManagedObject<?>>`
4. Preserving all other aspects of the code

## For Developers

This transformation is reusable - simply modify the input path to apply to any Maven project that has this same issue.

## License

MIT