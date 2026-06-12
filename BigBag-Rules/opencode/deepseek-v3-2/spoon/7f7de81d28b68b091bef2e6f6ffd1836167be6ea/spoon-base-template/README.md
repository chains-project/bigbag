# Generic Spoon Transformation for snmp4j-agent 3.6.6 API Breaking Change

## Problem
When upgrading from older versions of snmp4j-agent to version 3.6.6, the `MOServer.getRegistry()` method signature changed:
- **Old**: Returns `SortedMap<MOScope, ManagedObject>`
- **New**: Returns `SortedMap<MOScope, ManagedObject<?>>` (added wildcard `<?>`)

This causes compilation errors in projects that assign the result to variables with the old type signature.

## Solution
This Spoon-based transformation automatically fixes the breaking change by adding wildcards to `ManagedObject` type parameters in generic contexts.

## Transformation Rule
The transformation finds all occurrences where `ManagedObject` appears as a type argument without a wildcard (e.g., `SortedMap<MOScope, ManagedObject>`) and transforms it to `SortedMap<MOScope, ManagedObject<?>>`.

## Usage

### 1. Build the transformation
```bash
cd /workspace/spoon-base-template
mvn compile
```

### 2. Run the transformation on your project
```bash
cd /workspace/spoon-base-template
java -cp "target/classes:$(cat classpath.txt)" github.chains.Main /path/to/your/java/source/directory
```

Example for the @snmpman/ project:
```bash
java -cp "target/classes:$(cat classpath.txt)" github.chains.Main /workspace/snmpman/snmpman/src/main/java
```

### 3. Check transformed files
Transformed files are written to `{source-directory}-transformed`. For example:
- Input: `/workspace/snmpman/snmpman/src/main/java`
- Output: `/workspace/snmpman/snmpman/src/main/java-transformed`

## What the Transformation Fixes

### Specific Fix
Changes `SortedMap<MOScope, ManagedObject>` to `SortedMap<MOScope, ManagedObject<?>>` in variable declarations, field declarations, method parameters, and return types.

### Example
Before transformation:
```java
final SortedMap<MOScope, ManagedObject> reg = server.getRegistry();
```

After transformation:
```java
final SortedMap<MOScope, ManagedObject<?>> reg = server.getRegistry();
```

## Code Structure

- `src/main/java/github/chains/Main.java` - Main entry point
- `ManagedObjectWildcardProcessor` - Spoon processor that:
  - Extends `AbstractProcessor<CtElement>` to process various AST elements
  - Detects `ManagedObject` types in generic parameters without wildcards
  - Replaces them with `ManagedObject<?>`
  - Handles recursion through type arguments

## Generic and Reusable
This transformation is **generic** and can be applied to **any Java project** affected by this breaking change. No project-specific identifiers are hardcoded.

## Limitations
- May produce warnings for missing dependencies (Lombok annotations, etc.)
- Only fixes the specific breaking change pattern
- Output files are written to a separate directory (doesn't modify originals)

## Testing
The transformation was tested on the @snmpman/ project and successfully fixed the compilation error at `SnmpmanAgent.java:389`.