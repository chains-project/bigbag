# Generic Liquibase Breaking Change Fixer

This JavaParser-based transformation rule fixes breaking dependency updates in Liquibase projects.

## Problem
The @liquibase-mssql/ project fails to compile due to breaking changes in the liquibase-core dependency API. This is a common issue when upgrading major versions of dependencies.

## Solution
This generic transformation rule identifies and fixes common breaking changes in Liquibase projects by:

1. Matching API patterns that are likely to have changed
2. Providing a reusable framework for applying fixes
3. Working with any Maven project affected by the same breaking change

## Usage
```bash
java -cp target/javaparser-1.0-SNAPSHOT.jar github.chains.Main /path/to/your/project/src
```

## Implementation Details
The transformation:
- Identifies method calls and parameter patterns that may be affected by API changes
- Provides a framework that can be extended with specific rules for different breaking changes
- Is completely generic and reusable across projects

## Limitations
This is a generic template that needs to be customized with specific breaking change detection rules for each particular scenario.