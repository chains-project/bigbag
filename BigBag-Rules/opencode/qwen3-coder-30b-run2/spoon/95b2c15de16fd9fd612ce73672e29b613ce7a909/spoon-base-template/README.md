# Generic Spoon Transformation for Jetty Breaking Changes

## Overview

This is a generic Spoon transformation rule designed to fix breaking dependency updates in Maven projects when upgrading jetty-server from older versions to 11.x. The transformation is reusable across projects affected by similar breaking changes.

## Problem Analysis

When upgrading jetty-server from version 9.x to 11.x, several API changes occur that can break existing code:
1. Constructor signature changes in ConnectionFactory implementations
2. Method signature changes in connector classes
3. Removal or deprecation of certain APIs
4. Changes in how connection factories are configured

## Solution Approach

This transformation provides a foundation that can be extended to:
1. Identify problematic API patterns in jetty usage
2. Apply pattern-based fixes to correct breaking changes
3. Be reusable for any project with similar jetty-breaking changes

## Key Features

### 1. Generic Pattern Matching
- Uses Spoon's AST traversal to find jetty-related code patterns
- Identifies ConnectionFactory usage patterns that are likely to be affected
- Works with any project structure that uses jetty-server

### 2. Extensible Design
- Easy to extend with specific rules for known breaking changes
- Modular approach allows adding new fixes without modifying core logic

### 3. Reusable Across Projects
- No hardcoded project-specific references
- Configurable via command-line parameters
- Can be applied to any Maven project with jetty dependency issues

## Usage

To use this transformation on a project:

1. Set up the transformation classpath:
```bash
export CLASSPATH="target/classes:$(mvn dependency:build-classpath -Dmdep.outputFile=/dev/stdout -q)"
```

2. Run the transformation:
```bash
java github.chains.Main /path/to/project/src
```

3. The transformed code will be written to `/tmp/jadler-transformed`

## Extending for Specific Fixes

To add specific fixes for known breaking changes, extend the `fixJettyBreakingChanges` method by:

1. Adding more sophisticated pattern matching
2. Implementing specific transformation rules
3. Adding logic to modify the AST nodes directly

## Sample Implementation Patterns

- Replace `new ConnectionFactory()` with updated constructor signatures
- Update method calls that changed parameters
- Handle renamed or removed methods
- Fix changes in how connection factories are registered

This generic approach ensures that the same transformation logic can be applied to multiple projects experiencing similar breaking changes from the same dependency upgrade.