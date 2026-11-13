# Generic Maven Archiver Breaking Change Fixer

This is a generic transformation rule designed to fix breaking changes in Maven Archiver API calls across any Maven project.

## Problem Analysis

Based on the MavenArchiver API specification, the most likely breaking changes involve:
- Changes to method signatures in `createArchive` method
- Modifications to parameter types or addition/removal of parameters
- Changes in method availability or deprecation

## Solution Approach

The transformation follows this structure:
1. Parse Java source files in the target directory
2. Identify MavenArchiver API calls with problematic patterns
3. Apply appropriate fixes to align with the newer API
4. Save corrected files

## Usage

```bash
java -cp target/classes github.chains.Main /path/to/target/project
```

## Implementation Details

This transformation is designed to be generic and reusable:
- Uses fully qualified type names (not project-specific)
- Targets MavenArchiver API patterns structurally
- Can be easily customized for specific breaking changes