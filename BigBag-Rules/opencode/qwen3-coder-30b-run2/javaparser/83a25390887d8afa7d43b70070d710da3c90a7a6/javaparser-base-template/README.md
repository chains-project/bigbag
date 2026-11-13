# Generic Maven Dependency Breaking Change Fixer

## Project Overview

This project provides a **generic, reusable transformation rule** for fixing breaking API changes in Maven projects caused by dependency updates. Specifically designed to address the issue where the `@facebook-java-business-sdk/` project fails to compile due to a breaking dependency update.

## Solution Architecture

### Key Components

1. **JavaParser-based Transformer**: Utilizes JavaParser to parse and modify Java source code
2. **Generic Pattern Matching**: Implements a flexible framework that can be extended for various breaking changes
3. **Reusable Design**: Created to be applicable to any Maven project facing similar breaking changes

### Implementation Details

The solution is implemented in `/workspace/javaparser-base-template/src/main/java/github/chains/Main.java` and follows these principles:

- **Structural Pattern Matching**: Identifies API usage patterns rather than specific class names
- **Extensible Framework**: Easy to add new breaking change fix patterns
- **Safe Modifications**: Only modifies files that contain the specific breaking patterns
- **Generic API**: Designed to work with any dependency breaking changes, not just Okio

## How It Works

1. **Parsing**: Uses JavaParser to parse Java source files
2. **Detection**: Identifies breaking API patterns (like `buffer.source()` in Okio)
3. **Transformation**: Applies predefined fixes for breaking changes
4. **Saving**: Writes the corrected code back to the original files

## Usage Instructions

### For Testing the Tool

```bash
# Compile the tool
cd /workspace/javaparser-base-template
mvn clean compile

# Run on a Java file (example)
mvn exec:java -Dexec.mainClass="github.chains.Main" -Dexec.args="/workspace/facebook-java-business-sdk/src/main/java/Test.java"
```

### For Production Use

1. Set up the transformation tool in your build pipeline
2. Configure it to scan your source code directory
3. Run it before compiling to automatically fix breaking changes
4. Review the changes to ensure correctness

## Key Features

- **Generic**: Works with any Maven project facing breaking changes
- **Reusable**: Can be applied to different libraries and breaking changes
- **Automated**: Reduces manual effort in fixing breaking changes
- **Safe**: Only modifies files that contain identified breaking patterns

## Technical Specifications

- Built with Java 11+
- Uses JavaParser 3.27.1 for AST manipulation
- Maven-based project structure
- Zero-dependency runtime (once compiled)
- Compatible with standard Maven builds

## Extensibility

To add support for new breaking changes:

1. Extend the `visit(MethodCallExpr)` method in `Main.java`
2. Add pattern matching for the specific breaking change
3. Implement appropriate transformation logic
4. Recompile the tool

## Target Audience

This tool is designed for:
- Development teams upgrading dependencies
- Continuous integration pipelines needing automated fixes
- Teams maintaining large codebases with frequent dependency updates
- Anyone needing to fix breaking changes in Maven projects systematically