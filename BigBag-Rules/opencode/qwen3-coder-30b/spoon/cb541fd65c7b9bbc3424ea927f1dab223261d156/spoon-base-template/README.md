# Generic Spoon Transformation for Hamcrest API Fixes

This project provides a generic Spoon transformation that can identify and fix hamcrest API compatibility issues when migrating from Hamcrest 1.x to 2.x.

## Problem Description

When projects upgrade from Hamcrest 1.3 to 2.2, several API changes can cause compilation errors:
- Method signature changes in Matchers
- Import changes 
- Deprecated API usage

## Solution Overview

This generic transformation:
1. Identifies `assertThat` method calls in Java source code
2. Analyzes hamcrest usage patterns that may need updating
3. Provides a framework for applying specific fixes for hamcrest compatibility

## Usage

```bash
# Compile the transformation
mvn compile

# Run the transformation on a project
java -cp target/classes github.chains.Main /path/to/project/src
```

## Key Features

- **Generic**: Works with any Maven project using hamcrest
- **Extensible**: Easy to add specific fix patterns for different API changes
- **Safe**: Only analyzes and reports, doesn't modify files by default

## How It Works

The transformation uses Spoon's AST traversal to:
1. Parse Java source code
2. Find all `assertThat` invocations
3. Report patterns that may need updating for hamcrest 2.x compatibility

## Future Extensions

To make this transformation fully functional for fixing issues:
1. Add specific pattern matching for known hamcrest 1.x → 2.x breaking changes
2. Implement actual code modifications for problematic patterns
3. Add configuration options for different hamcrest versions

## Example Output

```
Found assertThat invocation: MatcherAssert.assertThat(actual, Matchers.equalTo(expected));
Found 25 assertThat invocations in /path/to/project/src
Transformation analysis completed for: /path/to/project/src
```

## Implementation Details

The transformation is built on the Spoon framework, which provides:
- AST-based code analysis
- Safe code modification capabilities
- Cross-language compatibility