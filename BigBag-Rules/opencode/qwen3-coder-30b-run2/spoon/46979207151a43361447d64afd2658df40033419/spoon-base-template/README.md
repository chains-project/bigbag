# JAXBToStringStrategy Fixer

This project demonstrates a solution to fix compilation errors in a legacy codebase that uses the `org.jvnet.jaxb2_commons` library.

## Problem Description

The compilation errors occur because newer versions of the `org.jvnet.jaxb2_commons:jaxb2-basics-runtime` library no longer provide the `getInstance()` static methods on strategy classes like:

- `JAXBToStringStrategy`
- `JAXBEqualsStrategy`
- `JAXBHashCodeStrategy`
- `JAXBCopyStrategy`
- `JAXBMergeStrategy`

These were previously available in older versions but have been removed/deprecated in newer versions.

## Compilation Errors

```
error: cannot find symbol
  symbol:   method getInstance()
  location: class org.jvnet.jaxb2_commons.lang.JAXBToStringStrategy
```

## Solution Approach

Our Spoon-based transformation tool scans the entire codebase and replaces all occurrences of:

```java
// Before (broken)
JAXBToStringStrategy.getInstance()

// After (fixed) 
DefaultToStringStrategy.INSTANCE
```

## How It Works

The transformation rule identifies calls to `getInstance()` methods on the deprecated strategy classes and replaces them with the appropriate static `INSTANCE` fields from the new `Default*Strategy` classes.

## Usage

1. Run the transformation:
   ```bash
   cd /workspace/spoon-base-template
   javac -cp "lib/*:." src/main/java/github/chains/*.java
   java -cp "lib/*:." github.chains.JAXBStrategyFixer
   ```

2. The fixed code will be generated in `/workspace/billy-fixed`

## Files Processed

The transformation targets:
- All Java source files in the `/workspace/billy` directory
- Specifically looks for calls to `getInstance()` on JAXB strategy classes
- Replaces them with the appropriate static `INSTANCE` equivalent

## Result

After applying this transformation, the code should compile successfully with newer versions of the jaxb2-basics library.