# Generic Spoon Transformation for Sponge API 8.0.0 Migration

This repository contains a generic Spoon transformation that fixes breaking changes introduced in Sponge API 8.0.0.

## Problem Analysis

The @ChangeSkin project was failing to compile due to breaking changes in the Sponge API 8.0.0, specifically:
1. `Sponge.getServer()` method was removed/replaced with `Sponge.game().server()`
2. `ChannelRegistrar.getOrCreateRaw()` now requires a third parameter for `Type.SERVER`
3. `GameProfileManager.createProfileProperty()` signature changed to accommodate additional parameters

## Solution Overview

This is a reusable transformation rule that can be applied to any Maven project affected by the same breaking changes.

### Key Features

- **Generic Pattern Matching**: Uses Spoon's AST traversal to find method invocations by pattern
- **Parameter Count Handling**: Detects when method signatures have changed and adapts accordingly
- **Reusable Rule**: Works across different projects with similar breaking changes
- **No Hardcoded Dependencies**: Uses fully-qualified type names and method signatures

### Transformation Rules

#### Rule 1: Sponge.getServer() → Sponge.game().server()
**Before:**
```java
Sponge.getServer()
```

**After:**
```java
Sponge.game().server()
```

#### Rule 2: ChannelRegistrar.getOrCreateRaw() Signature Change
**Before:** 
```java
channelReg.getOrCreateRaw(plugin, channelName)
```

**After:**
```java
channelReg.getOrCreateRaw(plugin, channelName, Type.SERVER)
```

#### Rule 3: GameProfileManager.createProfileProperty() Signature Change
**Before:**
```java
profileManager.createProfileProperty(key, value, signature)
```

**After:**
```java
profileManager.createProfileProperty(key, value, signature, null)
```

## Implementation Details

The transformation uses Spoon's visitor pattern to:
1. Traverse all Java source files in the project
2. Identify method invocation patterns that match breaking changes
3. Replace them with equivalent calls using the new API
4. Handle parameter count differences appropriately

## Usage

To apply this transformation to a project:

1. Configure Spoon with your project source directory
2. Run the transformation with `launcher.process()`
3. The transformation will automatically fix all instances of the breaking patterns

## Testing

This transformation has been designed to be safe and conservative:
- Only processes files matching the specific breaking change patterns
- Preserves original code structure and comments
- Maintains compatibility with existing functionality
- Can be tested on a copy of the source code before applying to production

## Extensibility

The transformation can be extended to handle additional breaking changes by:
1. Adding new filter conditions for additional method patterns
2. Including new replacement logic for different API changes
3. Parameterizing additional method signatures as needed