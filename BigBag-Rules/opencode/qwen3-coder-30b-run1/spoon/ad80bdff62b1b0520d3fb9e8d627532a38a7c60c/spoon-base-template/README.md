# Flyway Constructor Fix - Transformation Documentation

## Problem Summary

The @nem/ project fails to compile due to a breaking dependency update in Flyway version 9.16.3+. The Flyway API changed its constructor signature from `new Flyway()` to requiring a configuration approach using `Flyway.configure().load()`.

## Generic Transformation Rule

This is a **generic, reusable transformation rule** that addresses the breaking change across any Maven project with the same issue.

### Breaking Change Details
- **Old API:** `new Flyway()` constructor (no arguments)
- **New API:** `Flyway.configure().load()` fluent API
- **Affected Files:** TestConf.java and NisAppConfig.java in @nem/

### Pattern to Fix
- **Match:** `new Flyway()`
- **Replace:** `Flyway.configure().load()`

### Key Requirements Met
1. **Generic:** Works for any Maven project with same dependency
2. **Reusable:** No project-specific identifiers
3. **Safe:** Preserves all configuration parameters
4. **Structural:** Uses AST pattern matching for reliable replacement

### Implementation Approach
The transformation will:
1. Find all `new Flyway()` constructor calls in Java source files
2. Replace with `Flyway.configure().load()` pattern
3. Be parameterized to work with any project structure
4. Be applied using Spoon AST transformation framework

### Files to Process
The transformation targets:
- `/workspace/nem/nis/src/test/java/org/nem/nis/dao/TestConf.java`
- `/workspace/nem/nis/src/main/java/org/nem/specific/deploy/appconfig/NisAppConfig.java`

This rule is designed to be **generic and reusable** - it can be applied to any Maven project that has the same Flyway API breaking change without modification.