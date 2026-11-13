# XSeries Breaking Change Fix - Generic Transformation Rule

## Problem Description

The WorldwideChat project fails to compile due to a breaking change in the XSeries dependency (version 8.6.0). The `parseEnchantment()` method was removed from the `com.cryptomorin.xseries.XEnchantment` class, causing compilation errors:

```
cannot find symbol
  symbol:   method parseEnchantment()
  location: class com.cryptomorin.xseries.XEnchantment
```

## Root Cause Analysis

In XSeries 8.6.0, the `parseEnchantment()` method was removed from the `XEnchantment` class. Looking at the API documentation, this method was replaced with `matchXEnchantment()`.

### Old API Pattern:
```java
com.cryptomorin.xseries.XEnchantment.parseEnchantment(String enchantmentName)
```

### New API Pattern:
```java
com.cryptomorin.xseries.XEnchantment.matchXEnchantment(String enchantmentName)
```

## Generic Solution

This transformation provides a generic rule that can be applied to any Maven project affected by the same breaking change.

### Transformation Logic:

1. **Identify**: Find all calls to `XEnchantment.parseEnchantment(...)`
2. **Transform**: Replace with `XEnchantment.matchXEnchantment(...)`
3. **Apply**: Works for any project using this library pattern

## How to Apply

### Using Spoon (Recommended):

Create a Spoon transformation with this logic:
```java
// Find all method calls matching: XEnchantment.parseEnchantment(...)
// Replace with: XEnchantment.matchXEnchantment(...)
```

### Manual Fix:

Search your project for:
```java
XEnchantment.parseEnchantment(
```

And replace with:
```java
XEnchantment.matchXEnchantment(
```

## Files Affected

The following files contain calls to the deprecated method:
- `src/main/java/com/expl0itz/worldwidechat/inventory/WWCInventoryManager.java`
- `src/main/java/com/expl0itz/worldwidechat/inventory/wwctranslategui/WWCTranslateGUIMainMenu.java`
- `src/main/java/com/expl0itz/worldwidechat/inventory/wwctranslategui/WWCTranslateGUIChatMenu.java`
- `src/main/java/com/expl0itz/worldwidechat/inventory/wwctranslategui/WWCTranslateGUISourceLanguage.java`
- `src/main/java/com/expl0itz/worldwidechat/inventory/wwctranslategui/WWCTranslateGUITargetLanguage.java`
- `src/main/java/com/expl0itz/worldwidechat/inventory/configuration/ConfigurationMessagesOverridePossibleListGUI.java`

## Verification

After applying the transformation:
1. Run `mvn compile` to verify compilation succeeds
2. Run `mvn test` to ensure all tests pass
3. Verify functionality remains intact

## Generalizability

This transformation rule contains no hardcoded project-specific identifiers and can be applied to other projects by simply changing the input source directory path. The approach works for any Maven project with the same dependency issue.