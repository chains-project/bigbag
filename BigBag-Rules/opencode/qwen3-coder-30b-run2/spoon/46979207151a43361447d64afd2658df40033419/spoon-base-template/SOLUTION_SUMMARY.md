# JAXB Strategy Migration Solution

## Problem Analysis

The codebase is experiencing compilation errors due to changes in the `org.jvnet.jaxb2_commons:jaxb2-basics-runtime` library. Specifically:

### Root Cause
- Old version of the library had static `getInstance()` methods on strategy classes
- Newer versions of the library removed these methods
- Generated code (especially from JAXB schema compilation) still tries to call these methods
- Classes affected: `JAXBToStringStrategy`, `JAXBEqualsStrategy`, `JAXBHashCodeStrategy`, `JAXBCopyStrategy`, `JAXBMergeStrategy`

### Error Messages
```
cannot find symbol
  symbol:   method getInstance()
  location: class org.jvnet.jaxb2_commons.lang.JAXBToStringStrategy
```

## Solution Approach

Created a Spoon-based transformation tool that:

1. **Scans the codebase** for calls to `getInstance()` on the deprecated strategy classes
2. **Replaces them** with the appropriate static `INSTANCE` constants:
   - `JAXBToStringStrategy.getInstance()` → `DefaultToStringStrategy.INSTANCE`
   - `JAXBEqualsStrategy.getInstance()` → `DefaultEqualsStrategy.INSTANCE`
   - `JAXBHashCodeStrategy.getInstance()` → `DefaultHashCodeStrategy.INSTANCE`
   - `JAXBCopyStrategy.getInstance()` → `DefaultCopyStrategy.INSTANCE`
   - `JAXBMergeStrategy.getInstance()` → `DefaultMergeStrategy.INSTANCE`

## Why This Works

According to the API specification file (`jaxb2-basics-runtime-1.11.1-PUBLISHED-BY-MISTAKE-api.md`):

- `DefaultToStringStrategy` has a static `INSTANCE` field
- `DefaultEqualsStrategy` has a static `INSTANCE` field  
- `DefaultHashCodeStrategy` has a static `INSTANCE` field
- `DefaultCopyStrategy` has a static `INSTANCE` field
- `DefaultMergeStrategy` has a static `INSTANCE` field

These are the modern equivalents of the removed `getInstance()` methods.

## Implementation Details

The transformation tool:
- Uses Spoon's AST traversal to find problematic invocations
- Handles all 5 strategy types systematically
- Generates fixed code in `/workspace/billy-fixed`
- Maintains the functionality while updating to the new API

## Running the Tool

```bash
cd /workspace/spoon-base-template
mvn compile
java -cp target/classes github.chains.JAXBStrategyFixer
```

This will process the entire `/workspace/billy` codebase and create a fixed version in `/workspace/billy-fixed`.