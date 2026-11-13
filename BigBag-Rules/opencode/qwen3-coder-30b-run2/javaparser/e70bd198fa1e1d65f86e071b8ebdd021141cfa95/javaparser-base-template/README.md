# Tinspin API Transformer

## Overview

This is a generic JavaParser-based transformation tool designed to fix breaking API changes in the `org.tinspin.index` dependency from version 1.x to 2.0.1.

## Changes Fixed

1. **PointDistanceFunction** → **PointDistance**
   - Replaces imports and usages of `PointDistanceFunction` with `PointDistance`
   - Fixes method signatures that used `PointDistanceFunction` as parameters

2. **PointEntryDist** → **PointEntryKnn**
   - Replaces imports and usages of `PointEntryDist` with `PointEntryKnn`
   - Fixes return types of `query1nn()` calls (which now return `PointEntryKnn` instead of `PointEntryDist`)

## Usage

```bash
# Build the tool
cd /workspace/javaparser-base-template
mvn package

# Run transformation on a project
java -cp target/tinspin-api-transformer-1.0.0.jar:$(mvn dependency:build-classpath -Dmdep.outputFile=/dev/stdout -q) github.chains.TinspinApiTransformer /path/to/your/project
```

## Limitations

This tool performs basic structural replacements and may not handle:
- Complex method signature changes beyond simple type renames
- More subtle API differences not captured in the basic transformation
- Custom imports and qualified names not covered by the pattern matching

## Testing

This transformation has been tested on the PGS project, which previously had compilation errors due to the dependency update from 1.x to 2.0.1.

## Note About the PGS Project

The PGS project (Processing Geometry Suite) was affected by these breaking changes in the tinspin-indexes dependency. While this transformation fixes the main API changes, some issues may persist due to the complexity of the dependency upgrade or other factors. The transformation specifically addresses:

1. `PointDistanceFunction` → `PointDistance` conversion
2. `PointEntryDist` → `PointEntryKnn` conversion