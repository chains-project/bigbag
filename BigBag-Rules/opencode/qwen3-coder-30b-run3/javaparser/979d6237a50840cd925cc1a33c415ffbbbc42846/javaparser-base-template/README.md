# Generic JavaParser Transformation for Breaking Dependency Updates

This tool provides a generic framework for detecting and fixing breaking changes in Java dependencies. It uses JavaParser to analyze Java source code and can be customized to fix specific breaking changes commonly encountered when upgrading dependencies.

## Purpose

When a dependency library updates and introduces breaking changes (e.g., removed constructors, changed method signatures), projects that depend on that library may fail to compile. This tool helps automate the process of identifying and fixing these breaking changes.

## Usage

```bash
# Run on a specific directory
java -cp target/classes:$(mvn dependency:build-classpath -Dmdep.outputFile=/dev/stdout -q) github.chains.Main /path/to/source

# Default runs on Guice source
java -cp target/classes:$(mvn dependency:build-classpath -Dmdep.outputFile=/dev/stdout -q) github.chains.Main
```

## How It Works

The transformation:
1. Parses all Java source files in the specified directory
2. Uses JavaParser to traverse the AST (Abstract Syntax Tree)
3. Identifies patterns that match breaking changes (constructors, method calls)
4. Applies fixes based on customizable rules

## Customization

To customize this tool for a specific breaking change:

1. Modify the `BreakingChangeFixer` class
2. Add specific detection logic for the breaking change you're fixing
3. Implement appropriate fix strategies

## Example Patterns

Currently supports detecting:
- Constructor calls to classes with breaking changes
- Method calls that may have changed signatures

## Requirements

- Java 11+
- Maven 3.6+

## License

MIT