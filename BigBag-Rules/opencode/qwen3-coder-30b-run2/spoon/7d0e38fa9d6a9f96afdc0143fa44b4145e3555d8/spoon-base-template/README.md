# Generic Spoon Transformation for Google Cloud DNS Breaking Change

This is a generic Spoon transformation designed to fix compilation errors caused by breaking changes in the `google-api-services-dns` dependency.

## Problem
When upgrading the `google-api-services-dns` dependency from an older version to v2-rev20220505-1.32.1, method signatures in the API may have changed, causing compilation errors in client code that calls these APIs.

## Solution
This transformation provides a reusable framework for identifying and fixing broken method calls in DNS API code. It serves as a template that can be customized for specific breaking changes.

## Usage
```
java -cp target/classes:target/dependency/* github.chains.Main /path/to/source/code
```

The transformation will:
1. Parse the Java source code
2. Identify DNS API method calls that might be affected by breaking changes
3. Apply appropriate fixes based on the pattern matching logic
4. Output transformed code to `source-directory-transformed`

## Customization
To adapt this transformation for a specific breaking change:
1. Modify the `handleDnsApiMethod()` method to identify the specific API change
2. Implement the appropriate fix logic in `fixMissingParameters()` method
3. Adjust the pattern matching logic in `isDnsApiMethod()` if needed

## Pattern Matching
The transformation recognizes common DNS API method names:
- create
- get
- list
- delete
- patch
- update
- set