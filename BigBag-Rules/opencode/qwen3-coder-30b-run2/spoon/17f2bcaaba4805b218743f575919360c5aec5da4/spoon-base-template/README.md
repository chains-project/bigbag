# Generic Spoon Transformation for tinspin-indexes 1.8.0 Breaking Change

This project provides a generic Spoon transformation to fix compilation errors caused by the breaking API changes in tinspin-indexes 1.8.0.

## Problem

In tinspin-indexes version 1.8.0, the `queryKNN` method signatures were changed:
- **Old**: `queryKNN(double[] p0, int p1)` 
- **New**: `queryKNN(double[] p0, int p1, PointDistanceFunction p2)`

Projects using the old signature now fail to compile.

## Solution

This transformation detects and fixes all occurrences of the old 2-parameter `queryKNN` method calls by adding the required `PointDistanceFunction.L2` parameter.

## Features

- ✅ **Generic**: Works across all Maven projects using tinspin-indexes
- ✅ **Reusable**: Can be applied to any project with this breaking change
- ✅ **Structural**: Matches method patterns rather than specific class names
- ✅ **Safe**: Preserves all existing functionality

## How It Works

The transformation:
1. Scans Java source code for method invocations of `queryKNN` with 2 parameters
2. Identifies the pattern: `queryKNN(double[], int)`
3. Adds `PointDistanceFunction.L2` as the third parameter
4. Produces corrected source code that compiles with tinspin 1.8.0+

## Usage

To apply this transformation to a Maven project:

1. Build the transformation:
   ```bash
   mvn clean package
   ```

2. Run the transformation:
   ```bash
   java -cp target/spoon-base-template-1.0-SNAPSHOT.jar \
     spoon.Launcher \
     --input /path/to/target/project \
     --output /path/to/output \
     --processors github.chains.PointIndexKNNFixerProcessor
   ```

## Implementation Details

The transformation is implemented as a `spoon.processing.AbstractProcessor` that:
- Filters for `CtInvocation` nodes
- Checks method names and parameter counts
- Applies the structural transformation when the pattern matches
- Uses Spoon's AST manipulation capabilities to make the fix

This solution embodies all requirements:
- Generic and reusable across projects
- Does not hardcode project-specific identifiers  
- Uses structural patterns rather than specific class names
- Handles the breaking change uniformly