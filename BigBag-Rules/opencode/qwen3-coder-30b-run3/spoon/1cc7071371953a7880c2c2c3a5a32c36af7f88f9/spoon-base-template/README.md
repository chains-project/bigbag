# Generic Spoon Transformation for AssertJ Breaking Changes

This is a generic Spoon transformation designed to fix compilation errors caused by breaking changes in AssertJ 3.23.0 related to the removal of the `bytebuddy` package.

## Problem

In AssertJ 3.23.0, the internal `bytebuddy` package (`org.assertj.core.internal.bytebuddy`) was removed, causing compilation failures in projects that depend on it. Specifically, the `assertj-vavr` project fails to compile with errors like:

```
package org.assertj.core.internal.bytebuddy does not exist
cannot find symbol: class ByteBuddy
```

## Solution

This transformation removes all references to the removed bytebuddy package from Java source code, making it compatible with AssertJ 3.23.0 and later versions.

## Usage

```bash
# Run with Spoon on any Java project
java -cp target/classes:target/lib/* spoon.SpoonAPI \
  -i /path/to/source \
  -o /path/to/output \
  -p github.chains.Main
```

## Features

- **Generic**: Works on any Maven project with bytebuddy package dependencies
- **Safe**: Only removes problematic imports, preserves all other functionality
- **Reusable**: Can be applied to multiple projects with the same breaking change

## Target Files

The transformation processes files that contain:
- Imports referencing `org.assertj.core.internal.bytebuddy`
- Direct references to bytebuddy classes

## Limitations

This transformation only handles the import removal aspect. Projects that rely on bytebuddy functionality may require additional logic updates beyond what this generic rule provides.