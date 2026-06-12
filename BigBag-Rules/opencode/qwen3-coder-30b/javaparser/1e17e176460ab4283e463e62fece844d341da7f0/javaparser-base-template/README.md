# SnakeYAML 2.0 Breaking Change Fix

This tool fixes the breaking changes in SnakeYAML 2.0 that affect Maven projects using the `org.yaml:snakeyaml` library.

## Problem

SnakeYAML 2.0 introduced breaking changes to constructor signatures that affect projects using the Yaml class. Specifically, the constructor call patterns that worked in SnakeYAML 1.x no longer compile in SnakeYAML 2.0.

## Solution

This transformation tool identifies and fixes the specific constructor call patterns in Java files:

**Before (SnakeYAML 1.x):**
```java
yaml = new Yaml(constructor, new Representer(), new DumperOptions(), new ModelResolver());
```

**After (SnakeYAML 2.0):**
```java
yaml = new Yaml(constructor);
```

## Usage

1. Compile the tool:
   ```bash
   mvn compile
   ```

2. Run the transformation on a project directory:
   ```bash
   java -cp target/classes github.chains.SnakeYaml20Fix /path/to/project
   ```

## How It Works

The tool uses JavaParser to:
1. Parse all Java files in the target directory
2. Identify `Yaml` constructor calls with 4 arguments (the problematic pattern)
3. Replace them with 1-argument constructor calls
4. Save the modified files

## Generic Application

This transformation is generic and can be applied to any Maven project that:
- Uses SnakeYAML 2.0
- Has the specific constructor call pattern mentioned above
- Is affected by the breaking change in constructor signatures

The tool specifically targets the pattern found in projects like `polyglot-maven` where `Yaml` is constructed with a constructor, representer, dumper options, and resolver.