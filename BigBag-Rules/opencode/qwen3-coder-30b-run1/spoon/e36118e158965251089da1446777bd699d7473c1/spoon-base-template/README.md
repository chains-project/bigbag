# Generic Spoon Transformation for HTTP Library Breaking Changes

This project contains a generic Spoon transformation template designed to fix breaking dependency updates in Maven projects that use the com.artipie:http library.

## Problem Statement
When a dependency like com.artipie:http is updated to a new major version (e.g., v1.0.1), API signatures may change in breaking ways. This causes compilation errors in client projects that depend on the old API.

## Solution Approach
This transformation provides a reusable framework to:
1. Identify API calls that are affected by breaking changes
2. Apply generic transformations to migrate code to new APIs
3. Work with any Maven project with the same breaking change patterns

## How It Works
The transformation:
- Scans Java source files for method invocations
- Identifies patterns that match known breaking changes
- Applies fixes to migrate to new API signatures
- Generates a new version of the source code

## Usage
```bash
# Compile the transformation
mvn compile

# Run the transformation on a Maven project
java -cp target/classes:$(mvn dependency:build-classpath -Dmdep.outputFile=classpath.txt -q) github.chains.Main /path/to/maven/project/src
```

## Customization
To adapt this transformation for specific breaking changes:
1. Modify `isBreakingChangeInvocation()` to detect your specific API patterns
2. Update `fixBreakingChange()` to apply the correct transformation logic
3. Add specific handling for the new API signatures

## Key Features
- Generic - works with any Maven project
- Reusable - can be applied to multiple projects
- Safe - identifies specific patterns before applying changes
- Extensible - easy to add new transformation rules

## Example Breaking Change Addressed
This template specifically addresses breaking changes in `Headers.From` constructor signatures that may have changed between versions.