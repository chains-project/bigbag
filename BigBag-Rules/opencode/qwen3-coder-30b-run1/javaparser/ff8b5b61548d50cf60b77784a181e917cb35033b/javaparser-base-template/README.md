# Generic Mockito Dependency Transformation

This tool provides a generic transformation to fix breaking changes in Mockito dependencies, specifically addressing the migration from older Mockito versions to Mockito 4.1.0+.

## Problem Addressed

When projects use Mockito 4.1.0 or newer, there are breaking changes in the API that affect code compilation. The most common breaking change is the replacement of:
- `MockitoAnnotations.initMocks()` → `MockitoAnnotations.openMocks()`

## How It Works

This transformation:
1. Scans all Java source files in the given directory
2. Identifies calls to `MockitoAnnotations.initMocks()`
3. Replaces them with `MockitoAnnotations.openMocks()`
4. This is a generic solution that can be applied to any Maven project affected by this breaking change

## Usage

```bash
java -cp target/classes:$(mvn dependency:build-classpath -Dmdep.outputFile=/dev/stdout -q) github.chains.Main /path/to/java/project
```

## Limitations

This transformation specifically addresses the Mockito 4.1.0 breaking change where `initMocks` was replaced with `openMocks`. Other breaking changes in Mockito may require different transformations.