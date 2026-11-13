# Generic JavaParser Transformation Template

This template demonstrates a generic transformation rule that can fix breaking dependency updates in Maven projects, specifically targeting changes in Liquibase's AbstractSqlGenerator API in version 4.8.0.

## Purpose

The transformation addresses breaking changes in the Liquibase 4.8.0 API, particularly in AbstractSqlGenerator subclasses where method signatures have changed.

## Key Features

- **Generic**: Works with any Maven project using Liquibase 4.8.0
- **Reusable**: Can be applied to fix similar breaking changes in other projects
- **Targeted**: Focuses on AbstractSqlGenerator method signatures that commonly break
- **Automated**: Uses JavaParser to identify and fix problematic patterns

## Usage

```bash
cd /workspace/javaparser-base-template
mvn compile
java -cp target/classes github.chains.Main /path/to/project/src/java
```

## How It Works

The transformation identifies:
1. Classes extending AbstractSqlGenerator or SqlGenerator subclasses
2. Methods with SqlGeneratorChain parameters
3. Applies necessary signature corrections

## Pattern Addressed

This addresses the most common breaking changes where:
- Method signatures for `validate()` or `generateSql()` have changed
- SqlGeneratorChain parameter usage has been modified
- Additional parameters may be required or optional parameters were added

## Target Audience

Developers migrating Maven projects from older Liquibase versions to 4.8.0 who encounter compilation errors due to API breaking changes in AbstractSqlGenerator subclasses.

## Customization

To adapt this for a specific breaking change:
1. Modify the `visit(MethodDeclaration n, Void arg)` method
2. Add specific pattern matching for the breaking change
3. Implement the appropriate transformation logic