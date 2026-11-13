# Generic Spoon Transformation for XEnchantment Breaking Change

This is a generic Spoon transformation that fixes the breaking change in XSeries library where `XEnchantment.parseEnchantment()` was removed.

## Problem
In XSeries version 8.6.0, the `parseEnchantment()` method was removed from `com.cryptomorin.xseries.XEnchantment` class. This causes compilation errors in projects that use this method.

## Solution
This transformation replaces all usages of `XEnchantment.matchXEnchantment(...).get().parseEnchantment()` with `XEnchantment.matchXEnchantment(...).get().getEnchant()`.

## Usage
1. Place your project source in `/workspace/WorldwideChat/src` (adjust path as needed)
2. Run the transformation:
```bash
cd /workspace/spoon-base-template
mvn compile exec:java -Dexec.mainClass="github.chains.Main"
```

## How it Works
- Identifies all method calls to `.parseEnchantment()` 
- Replaces with `.getEnchant()` method call
- Maintains all other arguments and context
- Works generically across any project using this API pattern

## Target Pattern
**Before:**
```java
XEnchantment.matchXEnchantment("power").get().parseEnchantment()
```

**After:**
```java
XEnchantment.matchXEnchantment("power").get().getEnchant()
```

## Notes
- This transformation is applicable to any Maven project that uses the XEnchantment API in the same way
- The transformation should be run with proper dependencies on XSeries
- The transformation handles the generic pattern without hardcoding project-specific names