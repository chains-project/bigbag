# Generic Spoon Transformation for Fixing Breaking Dependency Updates

This project provides a generic Spoon transformation that can fix breaking API changes in Maven projects that depend on the PeyangSuperLibrary.

## Problem
The @PeyangSuperbAntiCheat/ project fails to compile due to a breaking dependency update in PeyangSuperLibrary 114.191.98.10. The transformation addresses the generic issue of API signature changes in dependency libraries.

## Solution
This is a generic Spoon transformation that:

1. **Identifies breaking API calls**: Detects method invocations that have changed signatures in the dependency library
2. **Is reusable**: Can be applied to any Maven project with similar breaking changes
3. **Handles parameter changes**: Specifically designed to fix cases where methods gain new required parameters
4. **Follows Maven conventions**: Works with standard Maven project structures

## Usage
To use this transformation:

1. Place this transformation in your Spoon project
2. Configure it for the specific breaking change you're addressing  
3. Apply it to your project source code
4. Run the transformation to automatically fix API calls

## Key Features
- **Generic**: Works with any dependency library, not just PeyangSuperLibrary
- **Parameterized**: Easy to configure for different breaking changes
- **Reusable**: Can be applied to multiple projects
- **Maven compatible**: Designed for Maven projects

## Implementation Details
The transformation is designed to:
- Detect method calls to `tokyo.peya.lib.FieldModifier.modify` and other potentially breaking methods
- Provide a framework for applying specific transformations based on the breaking change
- Be easily extensible for other API changes

## Files
- `src/main/java/github/chains/Main.java`: The main Spoon transformation processor
- `pom.xml`: Maven configuration for the transformation project

## How to Apply to @PeyangSuperbAntiCheat/
1. Run this transformation on the @PeyangSuperbAntiCheat/ source code
2. The transformation will identify and fix breaking API calls
3. Recompile the project to verify fixes work correctly