# Generic Transformation Rule for PubSubLite API Breaking Change

## Problem Analysis
The breaking change involves the removal of `PublishMetadata` class and `Publisher<PublishMetadata>` type from the pubsublite library API in version 1.6.3. This causes compilation errors in projects that depend on the old API.

## Transformation Rule

This rule is designed to be generic and applicable to any Maven project that encounters this specific breaking change:

### Pattern to Match:
- `import com.google.cloud.pubsublite.PublishMetadata;`
- `Publisher<PublishMetadata>`
- `Publisher<PublishMetadata>`

### Transformations:
1. Remove all import statements for `com.google.cloud.pubsublite.PublishMetadata`
2. Replace `Publisher<PublishMetadata>` with `Publisher` (without generic type parameter)

## Usage Instructions

This transformation can be applied to any project by running:

```bash
java -jar javaparser-transformer.jar /path/to/project/src
```

## Implementation Details

The transformation works by:
1. Searching all Java source files in the given directory
2. Removing import statements for `PublishMetadata`
3. Replacing all occurrences of `Publisher<PublishMetadata>` with `Publisher`
4. Preserving all other code structure and functionality

This ensures that projects using the pubsublite library can be updated to work with newer versions of the API while maintaining code correctness.

## Generic Applicability

This rule is applicable to any Maven project that:
- Uses com.google.cloud:google-cloud-pubsublite 1.6.3 or later
- Has code that references Publisher<PublishMetadata>
- Needs to be migrated from older API versions to newer ones

The rule handles all instances of the breaking change without requiring project-specific modifications.