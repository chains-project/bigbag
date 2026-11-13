# Generic JavaParser Transformation Tool for Breaking Dependency Updates

This tool provides a reusable framework for fixing breaking API changes in Maven projects.

## Usage

```bash
java -cp target/classes:$(mvn dependency:build-classpath -Dmdep.outputFile=/dev/stdout -q) github.chains.Main <sourceDirectory> <outputDirectory>
```

## How It Works

1. Traverses all Java files in the specified source directory
2. Applies transformation rules to detect and fix breaking API changes
3. Outputs transformed files to the specified output directory

## Customization

To customize this tool for a specific breaking change:

1. Modify the `BreakingChangeVisitor` class in `Main.java`
2. Update the method signature detection logic
3. Implement the specific transformation needed for your breaking change

## Key Features

- Generic design that works across Maven projects
- Structural pattern matching using JavaParser
- Preserves original file structure while making necessary changes
- Parameterized by specific API change details
- No hardcoded project-specific identifiers
- Easy to adapt for different breaking changes

## Example Transformation

For a breaking change where a method signature changes from:
```java
public String scriptForPipelineFromResourceWithParameters(String p0, String... p1)
```
to:
```java
public String scriptForPipelineFromResourceWithParameters(String p0, String... p1, boolean p2)
```

The tool would insert the missing boolean parameter where needed.