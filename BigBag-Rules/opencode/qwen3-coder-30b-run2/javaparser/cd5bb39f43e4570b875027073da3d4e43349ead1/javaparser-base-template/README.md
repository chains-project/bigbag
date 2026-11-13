# Generic Xpp3Dom Fix Transformation

This is a generic JavaParser-based transformation to fix breaking changes in plexus-utils 4.0.0 where `org.codehaus.plexus.util.xml.Xpp3Dom` was removed.

## Problem

In plexus-utils 4.0.0, the `org.codehaus.plexus.util.xml.Xpp3Dom` class was removed, causing compilation errors in Maven projects that depend on it.

## Solution

This transformation handles:
1. Removal of import statements referencing Xpp3Dom
2. Identification of instanceof checks that reference Xpp3Dom
3. Provides guidance for manual fixes needed

## Usage

```bash
mvn compile
# Run the transformation on source files
java -cp target/classes:target/dependency/* github.chains.Main /path/to/project/src
```

## Limitations

This is a structural transformation that:
- Removes Xpp3Dom imports
- Identifies problematic instanceof checks
- Does not automatically fix the underlying logic that depends on Xpp3Dom
- Requires manual intervention for the functional changes needed

## What Users Need to Do

After applying this transformation:
1. Review warning messages for instanceof checks
2. Manually replace Xpp3Dom-dependent logic with appropriate alternatives
3. Consider adding back the required dependencies or implementations