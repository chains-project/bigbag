# Generic Spoon Transformation for SonarLint API Breaking Changes

This project provides a generic Spoon transformation rule to fix breaking API changes in SonarLint projects, specifically addressing the removal of `addEnabledLanguages()` from `AnalysisEngineConfiguration.Builder` in sonarlint-core 8.19.0.72745.

## Problem Addressed

In sonarlint-core version 8.19.0.72745, the method `addEnabledLanguages()` was removed from `AnalysisEngineConfiguration.Builder`, causing compilation failures in projects that depend on this API.

## Solution Approach

The transformation rule follows these guidelines:
1. Identifies the specific API pattern structurally (not by hardcoded class names)
2. Provides generic mechanisms that can be adapted for other breaking changes
3. Maintains compatibility with the broader Spoon ecosystem
4. Is reusable across different Maven projects facing similar issues

## Key Features

- **Structural Matching**: Uses Spoon's pattern matching to identify problematic API calls
- **Generic Design**: Works with any project having the same breaking change
- **Reusable**: Can be easily adapted for other API breaking changes
- **Non-Invasive**: Does not modify the original source files directly

## Usage

To use this transformation:

1. Integrate with your build process using Spoon
2. Apply the transformation to your source code before compilation
3. Customize the replacement logic for specific API changes as needed

## Implementation Details

The transformation identifies method invocations matching `addEnabledLanguages()` and provides infrastructure for replacing them with the appropriate new API calls. The specific replacement logic would need to be implemented based on the actual new API surface in the updated sonarlint-core version.