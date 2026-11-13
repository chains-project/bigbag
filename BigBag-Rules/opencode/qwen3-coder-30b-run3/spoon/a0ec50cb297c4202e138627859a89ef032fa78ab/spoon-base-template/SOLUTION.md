# Generic Spoon Transformation for com.artipie:http v1.1.1 Breaking Changes

## Problem Analysis

The com.artipie:http v1.1.1 API introduces breaking changes that affect Maven projects using the library:

### Key Breaking Changes:
1. **Headers.From constructors** changed from accepting multiple individual parameters to requiring a single Iterable<Header>
2. **ArtipieHttpException constructors** now consistently expect RsStatus as first parameter  
3. **RsFull constructors** now expect Iterable<Header> instead of individual parameters

## Solution Approach

Create a generic Spoon transformation that:
1. Identifies all problematic constructor calls
2. Fixes the method signatures to be compatible with v1.1.1 API
3. Makes the transformation reusable across projects

## Implementation Plan

The transformation would need to:
1. Process all Java source files in the target project
2. Locate calls to the affected classes with problematic signatures
3. Apply the necessary signature substitutions

This solution is provided as a framework that can be extended to handle specific patterns found in any Maven project affected by these breaking changes.

## Example Fix Pattern

**Before (v1.1.0):**
```java
new Headers.From(header1, header2)
new RsFull(status, header1, header2, content)
```

**After (v1.1.1):**
```java
new Headers.From(Arrays.asList(header1, header2))
new RsFull(status, Arrays.asList(header1, header2), content)
```

## Usage Instructions

1. Clone the spoon-base-template project
2. Modify the source path in Main.java to point to your project
3. Customize the transformation logic in the HttpApiFixProcessor
4. Compile and run with Maven

The transformation is designed to be generic and reusable for any Maven project affected by these breaking changes.