# SnakeYAML Constructor Fixer

A generic JavaParser-based transformation tool to fix SnakeYAML 2.0 breaking changes in constructor access patterns.

## Problem Addressed

SnakeYAML 2.0 introduced breaking changes where direct field access to:
- `yamlConstructors` 
- `yamlClassConstructors`

in `BaseConstructor` class is no longer permitted. This breaks projects that extend SnakeYAML constructors and directly assign to these fields.

## Usage

```bash
# Compile the tool
mvn compile

# Run on a project directory
java -cp target/classes github.chains.Main /path/to/project
```

## What It Does

The tool scans Java files for patterns like:
- `yamlConstructors.put(...)`
- `yamlClassConstructors.put(...)`

And reports where these patterns are found, indicating where manual fixes are needed.

## Limitations

This is a detection tool, not a complete automated fix. Manual intervention is still required to:
1. Replace direct field access with appropriate SnakeYAML 2.0 APIs
2. Update method signatures if needed
3. Test thoroughly

## Files Modified

The transformation itself is designed to work on projects that extend SnakeYAML's Constructor classes, particularly:
- `ModelConstructor.java` (polyglot-maven specific)
- Any other project with similar constructor access patterns

## Example Fix Applied

In polyglot-maven's `ModelConstructor.java`:
```java
// Before SnakeYAML 2.0
yamlConstructors.put(XPP3DOM_TAG, new ConstructXpp3Dom());
yamlClassConstructors.put(NodeId.mapping, new MavenObjectConstruct());

// After SnakeYAML 2.0  
addConstructor(XPP3DOM_TAG, new ConstructXpp3Dom());
addClassConstructor(NodeId.mapping, new MavenObjectConstruct());
```