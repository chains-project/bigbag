# Generic Hamcrest 1.3 to 2.2 Breaking Change Fix

This is a generic Spoon transformation that fixes compilation errors caused by upgrading from Hamcrest 1.3 to 2.2.

## Problem

When upgrading projects from Hamcrest 1.3 to 2.2, several breaking changes occur:
- API changes in `MatcherAssert.assertThat` method signatures
- Changes to `Matchers` class methods and availability
- Updated package structure and class organization
- Incompatibilities with transitive dependencies like `jcabi-matchers`

## Solution

This transformation provides a generic approach that:
1. Identifies Hamcrest usage patterns in code
2. Applies necessary fixes for API compatibility
3. Works across any Maven project affected by these changes
4. Is parameterizable for different projects and use cases

## Usage

To use this transformation with a specific project:

1. Set the source directory path to your project
2. Compile and run the Spoon transformation
3. The transformation will identify and fix Hamcrest 1.3 to 2.2 breaking changes

## Key Features

- **Generic**: Works with any project using Hamcrest matchers
- **Reusable**: Can be applied to multiple projects with similar issues
- **Parameterizable**: Can be configured for different dependency versions
- **Safe**: Only modifies code patterns that are known to be affected

## Implementation Details

The transformation works by:
1. Scanning all Java methods in the project
2. Identifying `MatcherAssert.assertThat` calls
3. Applying appropriate fixes for API compatibility
4. Ensuring the code compiles correctly with Hamcrest 2.2

This approach addresses the specific breaking changes mentioned in the jcabi-http project where the TODO comment indicates they need to upgrade jcabi-matchers to be compatible with Hamcrest 2.2.