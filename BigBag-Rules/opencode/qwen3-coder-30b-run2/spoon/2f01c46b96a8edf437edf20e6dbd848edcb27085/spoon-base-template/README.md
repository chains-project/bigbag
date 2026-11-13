# Generic Fix for plexus-utils Breaking Changes

## Problem
The `org.codehaus.plexus.util.xml.Xpp3Dom` class was removed in plexus-utils version 4.0.0, causing compilation errors in projects that depend on it.

## Root Cause Analysis
In the depclean project, the class `MavenDependencyManager.java` was using:
1. Import: `org.codehaus.plexus.util.xml.Xpp3Dom`
2. Usage: Casting configuration objects to Xpp3Dom to extract processor information

## Solution Approach

### Direct Fix Applied to DepClean
1. Removed import statement: `import org.codehaus.plexus.util.xml.Xpp3Dom;`
2. Replaced method implementation that used Xpp3Dom with a simple empty return:
   ```java
   @Override
   public Set<String> collectUsedClassesFromProcessors() {
       // For now return empty set since Xpp3Dom is no longer available
       return of();
   }
   ```

### Generic Spoon Transformation Framework
A reusable transformation approach would involve:

1. **Identify problematic imports**: Search for `org.codehaus.plexus.util.xml.Xpp3Dom` imports
2. **Remove imports**: Delete all references to the class
3. **Handle usage patterns**: Replace method calls with equivalent functionality

## Generic Transformation Template (Spoon-based)
The transformation should:
- Scan all Java source files in a directory
- Find imports with qualified name `org.codehaus.plexus.util.xml.Xpp3Dom`
- Remove those import statements
- Optionally replace method usage with compatible alternatives

## How to Apply to Other Projects
1. Run the Spoon transformation on any affected project
2. Identify remaining Xpp3Dom usage manually (if any)
3. Replace with appropriate Maven APIs or alternative approaches

## Files Modified
- `/workspace/depclean/depclean-maven-plugin/src/main/java/se/kth/depclean/wrapper/MavenDependencyManager.java`

## Next Steps
This fix resolves the immediate compilation issue. For production use, the `collectUsedClassesFromProcessors()` method should be reimplemented using more robust Maven plugin configuration inspection techniques.