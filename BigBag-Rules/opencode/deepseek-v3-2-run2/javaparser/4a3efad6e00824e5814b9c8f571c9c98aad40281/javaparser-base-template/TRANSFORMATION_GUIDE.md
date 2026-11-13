# Generic Transformation Guide

## Problem Statement

When dependencies undergo breaking API changes (e.g., class relocations), manual code updates are:
- Time-consuming
- Error-prone
- Inconsistent across projects

## Solution Architecture

### Generic Transformation Pattern

```java
// Parameterized transformation - NO hardcoded project identifiers
String oldType = "eu.europa.esig.dss.pades.CertificationPermission";
String newType = "eu.europa.esig.dss.enumerations.CertificationPermission";

visit(ImportDeclaration.class, importDecl -> {
    if (importDecl.getNameAsString().equals(oldType)) {
        importDecl.setName(newType);
    }
});
```

### Key Design Principles

1. **Parameterization**: All type names are passed as parameters
2. **Reusability**: No project-specific hardcoding
3. **Preservation**: Maintains code formatting and structure
4. **Completeness**: Handles all AST node types containing type references

## Transformation Scope

### Currently Supported
- Import declarations
- Fully-qualified type references in code
- Method parameter types
- Field type declarations
- Static field references using fully-qualified names

### Future Extensions
- Static imports
- Annotation types
- Generic type parameters
- Reflection-based class loading

## Usage Patterns

### Single Project Transformation
```bash
java -jar transformation.jar /path/to/project/src
```

### Batch Processing
```bash
# Transform multiple projects
for project in project1 project2 project3; do
    java -jar transformation.jar /path/to/$project/src
```

### CI/CD Integration
```bash
# Pre-compilation step in build pipeline
java -jar transformation.jar $SOURCE_DIR
mvn compile
```

## Validation Strategy

### Pre-Transformation
1. Identify breaking changes from dependency documentation
2. Characterize old vs new API patterns
3. Test transformation on sample code snippets

### Post-Transformation
1. Verify compilation success
2. Run existing test suites
3. Validate functionality preservation
4. Check code formatting consistency

## Extension Framework

### Adding New Transformations

1. **Identify Pattern**: Analyze API breaking change
2. **Define Parameters**: Old type, new type
3. **Implement Visitor**: Handle relevant AST nodes
4. **Test**: Validate on sample projects

### Example Extension Template

```java
public void addTransformation(String oldType, String newType) {
    // Import statements
    visit(ImportDeclaration.class, importDecl -> {
        if (importDecl.getNameAsString().equals(oldType)) {
            importDecl.setName(newType);
        }
    });
    
    // Type references in code
    visit(NameExpr.class, nameExpr -> {
        if (nameExpr.getNameAsString().equals(oldType)) {
            nameExpr.setName(newType);
        }
    });
}
```

## Best Practices

### Transformation Safety
- Always backup code before transformation
- Use version control to track changes
- Test on non-production code first

### Parameter Management
- Externalize type mappings to configuration files
- Support multiple simultaneous transformations
- Provide dry-run mode for preview

### Error Handling
- Graceful failure on parse errors
- Log transformation statistics
- Report unchanged files

## Case Study: DSS CertificationPermission

### Problem
- Class moved from `eu.europa.esig.dss.pades` to `eu.europa.esig.dss.enumerations`
- Compilation failures across dependent projects

### Solution
- Generic transformation rule applied to @open-pdf-sign/
- Fixed import in Signer.java:7
- Maintained code formatting with DefaultPrettyPrinter
- Verified compilation and test success

### Results
- Zero manual code changes required
- Consistent transformation across codebase
- Preserved project functionality