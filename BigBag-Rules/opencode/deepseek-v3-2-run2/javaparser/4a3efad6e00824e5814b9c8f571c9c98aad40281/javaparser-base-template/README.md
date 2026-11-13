# JavaParser Generic Transformation Tool

A reusable JavaParser-based transformation tool for automated code refactoring across dependency version updates.

## Overview

This tool provides a generic framework for automatically transforming Java source code to handle breaking API changes during dependency updates. It was specifically created to address the relocation of `CertificationPermission` class from `eu.europa.esig.dss.pades` to `eu.europa.esig.dss.enumerations` in DSS library version 5.10.2.

## Features

- **Generic Transformation**: Parameterized by fully-qualified type names
- **Reusable**: Apply to any Maven project with similar breaking changes
- **Safe**: Preserves code formatting and structure
- **Batch Processing**: Transform entire source directories

## Usage

### Prerequisites
- Java 8+
- Maven

### Building
```bash
cd javaparser-base-template
mvn clean compile assembly:single
```

### Running
```bash
java -jar target/javaparser-1.0-SNAPSHOT-jar-with-dependencies.jar <source-directory>
```

### Example
To fix the DSS CertificationPermission relocation:
```bash
java -jar target/javaparser-1.0-SNAPSHOT-jar-with-dependencies.jar /path/to/your/project/src
```

## Transformation Rules

The tool currently implements:

### CertificationPermission Relocation
- **Old Import**: `eu.europa.esig.dss.pades.CertificationPermission`
- **New Import**: `eu.europa.esig.dss.enumerations.CertificationPermission`

This transformation handles:
- Import statements
- Fully-qualified type references in code
- Method parameter types
- Field declarations
- Static field references

## Extending for Other Transformations

To add new transformation rules, modify `src/main/java/github/chains/Main.java`:

```java
// Add new transformation patterns
visit(ImportDeclaration.class, importDecl -> {
    if (importDecl.getNameAsString().equals("old.package.ClassName")) {
        importDecl.setName("new.package.ClassName");
    }
});
```

## Architecture

- **JavaParser 3.27.1**: AST parsing and manipulation
- **DefaultPrettyPrinter**: Code formatting preservation
- **Visitor Pattern**: AST traversal and transformation
- **Maven Assembly Plugin**: Executable JAR packaging

## Testing

After transformation, verify:
1. Project compiles successfully
2. All tests pass
3. Functionality remains unchanged

## Limitations

- Currently handles import statements and type references
- Does not handle reflection-based class loading
- May require manual intervention for complex transformations

## Contributing

1. Fork the repository
2. Add new transformation rules
3. Test with sample projects
4. Submit pull request

## License

MIT License