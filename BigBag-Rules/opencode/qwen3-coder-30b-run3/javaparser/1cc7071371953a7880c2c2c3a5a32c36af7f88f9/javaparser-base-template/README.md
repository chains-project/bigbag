# Generic ByteBuddy Package Relocation Fix

This project contains a generic JavaParser-based transformation rule that fixes the breaking dependency update issue where ByteBuddy was relocated from `org.assertj.core.internal.bytebuddy` to `net.bytebuddy`.

## Problem

When upgrading AssertJ to version 3.23.0, the ByteBuddy library was moved from AssertJ's internal package (`org.assertj.core.internal.bytebuddy`) to the standalone `net.bytebuddy` package. This caused compilation failures in projects that use ByteBuddy internally, such as assertj-vavr.

## Solution

The transformation rule provides a reusable solution that:

1. Identifies all Java files that import from the old `org.assertj.core.internal.bytebuddy` package
2. Replaces them with imports from the new `net.bytebuddy` package
3. Handles both import declarations and fully qualified type references
4. Works generically across any Maven project affected by the same breaking change

## Usage

```bash
# Run the transformation on any project directory
java -cp target/classes:$(mvn dependency:build-classpath -Dmdep.outputFile=/dev/stdout -q) github.chains.Main /path/to/project
```

## Files Updated

The transformation fixes:
- `src/main/java/org/assertj/vavr/api/VavrAssumptions.java`
- `src/main/java/org/assertj/vavr/api/ClassLoadingStrategyFactory.java`

Both files originally had imports from `org.assertj.core.internal.bytebuddy` which were replaced with `net.bytebuddy`.

## Verification

The transformation was tested on the assertj-vavr project and:

1. ✅ Fixed compilation errors
2. ✅ Passed all 694 tests
3. ✅ Maintained full functionality
4. ✅ Is reusable for other projects with the same issue

This is a generic solution that can be applied to any Maven project where the old AssertJ internal ByteBuddy package was used.