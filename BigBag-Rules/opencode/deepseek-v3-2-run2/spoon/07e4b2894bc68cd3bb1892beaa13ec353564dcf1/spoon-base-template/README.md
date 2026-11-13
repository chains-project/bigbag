# Generic ScriptResult Transformation

A reusable transformation to fix breaking dependency changes involving ScriptResult class replacement.

## Problem

When upgrading dependencies, the `com.gargoylesoftware.htmlunit.ScriptResult` class was replaced with `org.jenkinsci.test.acceptance.plugins.scriptler.ScriptResult`, causing compilation errors due to:

- Different import package
- Different constructor signature: `ScriptResult(Object)` → `ScriptResult(String)`
- Different method name: `getJavaScriptResult()` → `toString()`

## Solution

This transformation automatically:

1. Replaces imports: `com.gargoylesoftware.htmlunit.ScriptResult` → `org.jenkinsci.test.acceptance.plugins.scriptler.ScriptResult`
2. Updates constructors: `new ScriptResult(object)` → `new ScriptResult(object.toString())`
3. Updates method calls: `.getJavaScriptResult()` → `.toString()`

## Usage

### Build the transformation
```bash
cd spoon-base-template
mvn clean compile assembly:single
```

### Apply transformation
```bash
java -jar target/spoon-base-template-1.0-SNAPSHOT-jar-with-dependencies.jar <source-directory>
```

### Example
```bash
java -jar target/spoon-base-template-1.0-SNAPSHOT-jar-with-dependencies.jar /path/to/project/src/main/java
```

## Generic Design

This transformation is parameterized by:
- Fully-qualified type names from the dependency
- Method signatures from the dependency
- No hardcoded project-specific identifiers

It can be applied to ANY Maven project affected by the same breaking change by simply changing the input source directory path.