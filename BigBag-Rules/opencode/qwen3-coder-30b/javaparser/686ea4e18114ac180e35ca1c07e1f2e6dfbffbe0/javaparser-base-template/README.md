# DropWizard 4.0.0 Breaking Changes Transformation Tool

This is a generic JavaParser-based transformation tool that fixes common DropWizard breaking changes when migrating to version 4.0.0 and later.

## Features

- Handles method signature changes in Configuration classes
- Updates import statements that may have changed
- Fixes API changes in Environment and related classes
- Processes all Java files in a directory recursively

## Usage

```bash
# Build the tool
mvn clean package

# Run transformation on your source code
java -jar target/dropwizard-transformation-1.0.0.jar /path/to/your/src
```

## How It Works

The transformation tool:

1. Parses all Java files in the specified directory
2. Identifies DropWizard-specific patterns and API usage
3. Applies rules to fix common breaking changes
4. Writes the modified files back to disk

## Supported Patterns

- Classes extending `io.dropwizard.Configuration`
- Method calls to `getServerFactory()`, `getDatabaseFactory()`, etc.
- Import statements that may have changed
- Environment-related method signatures

## Customization

To extend this transformation for your specific needs:

1. Modify the `applyDropWizardTransformation` method in `DropWizardTransformation.java`
2. Add new visitor patterns for additional breaking changes
3. Update the method detection logic for your specific use case

## Example

To use with a Maven project:

```bash
# Build the tool
mvn clean package

# Run transformation on your project
java -jar target/dropwizard-transformation-1.0.0.jar /path/to/your/project/src/main/java
```

## Implementation Details

The tool implements the following transformation rules:

1. **Configuration Class Detection**: Identifies classes extending `io.dropwizard.Configuration`
2. **Method Call Pattern Matching**: Detects common method signature changes in DropWizard 4.0.0
3. **Import Statement Analysis**: Identifies potentially affected import statements
4. **Environment Method Detection**: Finds methods that may have changed in DropWizard 4.0.0

## Requirements

- Java 11 or higher
- Maven 3.6 or higher

## License

MIT License