# Generic JavaParser Transformation for Breaking Changes

This document describes the approach for creating a generic JavaParser transformation to fix two breaking changes in Maven projects:

## Issue 1: ClientHelper Constructor API Change

**Old API:**
```java
new ClientHelper(String credentials, TaskListener listener, String clientName, String charset)
```

**New API:**
```java
// Multiple constructor variants exist:
new ClientHelper(Item item, String clientName, TaskListener listener, Workspace workspace)
new ClientHelper(ItemGroup itemGroup, String clientName, TaskListener listener, Workspace workspace) 
new ClientHelper(P4BaseCredentials credentials, TaskListener listener, Workspace workspace)
```

## Issue 2: StringUtils.isAllBlank Method Removal

The `isAllBlank` method was removed from `org.apache.commons.lang3.StringUtils` in newer versions.

**Fix:** Replace with equivalent logic using `isBlank` method.

## Generic Transformation Approach

The transformation should:
1. Identify problematic constructor calls by pattern matching
2. Identify problematic method calls by pattern matching
3. Apply appropriate fixes without hardcoding specific class names
4. Be reusable across projects

## Implementation Strategy

The transformation will be implemented to:
1. Find all `ClientHelper` constructor calls with 4 arguments
2. Find all `StringUtils.isAllBlank` method calls
3. Output information about where fixes are needed for manual review
4. Provide clear guidance for how to fix each case

## Usage

Compile and run the transformation:
```bash
mvn compile exec:java -Dexec.mainClass="github.chains.Main" -Dexec.args="/path/to/source/directory"
```