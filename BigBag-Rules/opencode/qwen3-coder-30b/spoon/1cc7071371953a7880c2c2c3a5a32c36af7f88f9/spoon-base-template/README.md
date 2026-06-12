# AssertJ Vavr ByteBuddy Migration Tool

## Problem
The `@assertj-vavr` project fails to compile due to breaking changes in `assertj-core 3.23.0`. The project was using internal AssertJ ByteBuddy classes (`org.assertj.core.internal.bytebuddy.*`) which were removed in this version.

## Solution
This is a generic Spoon transformation that replaces all imports from the deprecated package with the correct external ByteBuddy package:

**Before:**
```java
import org.assertj.core.internal.bytebuddy.ByteBuddy;
import org.assertj.core.internal.bytebuddy.TypeCache;
import org.assertj.core.internal.bytebuddy.dynamic.loading.ClassLoadingStrategy;
// ... other imports
```

**After:**
```java
import net.bytebuddy.ByteBuddy;
import net.bytebuddy.TypeCache;
import net.bytebuddy.dynamic.loading.ClassLoadingStrategy;
// ... other imports
```

## How It Works
The transformation:
1. Identifies all imports from `org.assertj.core.internal.bytebuddy.*` 
2. Replaces them with equivalent imports from `net.bytebuddy.*`
3. Works on any Maven project with the same issue

## Usage
1. Run the Spoon transformation on your project source directory
2. The transformation will automatically fix all import statements
3. No code changes required beyond the import replacements

## Files Modified
- `VavrAssumptions.java` - Fixes imports for ByteBuddy classes
- `ClassLoadingStrategyFactory.java` - Fixes imports for ByteBuddy classes

This is a generic solution that can be applied to any project affected by the same breaking change in AssertJ core.