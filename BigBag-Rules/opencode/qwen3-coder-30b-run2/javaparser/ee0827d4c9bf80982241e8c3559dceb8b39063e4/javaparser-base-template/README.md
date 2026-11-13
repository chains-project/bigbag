# Generic Commons-IO Breaking Change Fixer

This tool fixes Maven projects that are affected by breaking changes in commons-io dependencies.

## Problem
Projects using old versions of commons-io (like 20030203.000550) may encounter compilation errors when upgrading to newer versions because:
- Classes like BoundedInputStream, ClosedInputStream, ThresholdingOutputStream, and NullPrintStream 
- May have been removed, relocated, or had their APIs changed

## Solution
This JavaParser-based transformation removes problematic imports and provides guidance for fixing usage patterns.

## Usage
```
java -cp target/classes github.chains.Main /path/to/project/src
```

## How it Works
1. Identifies imports to problematic commons-io classes
2. Removes those imports from Java files
3. Provides a mechanism for future enhancements to replace class usage patterns

## Limitations
This is a basic fix that removes imports. More sophisticated transformations would need to:
- Detect and replace class instantiation patterns
- Handle method calls that use these classes
- Provide alternative implementations

## To Test
Run the transformation on the plexus-archiver project to see if it fixes the compilation issues.