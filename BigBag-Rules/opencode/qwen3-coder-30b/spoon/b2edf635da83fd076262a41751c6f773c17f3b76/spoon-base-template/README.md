# SnakeYAML 2.0 Migration Tool

This is a generic Spoon transformation tool that fixes breaking changes when migrating Java projects from SnakeYAML 1.x to 2.0.

## Breaking Changes Addressed

SnakeYAML 2.0 introduced several breaking changes to constructor signatures:

1. **Yaml()** - Now requires `LoaderOptions` parameter
2. **Yaml(boolean allowDuplicateKeys)** - Constructor was removed 
3. **Yaml(Constructor)** - Signature changed to include `LoaderOptions`
4. **Yaml(Representer, Constructor)** - Signature changed to include `LoaderOptions`

## Usage

```bash
# Compile the transformation
cd /workspace/spoon-base-template
mvn compile

# Run the transformation on a project
java -cp target/classes github.chains.SnakeYAML20Fix /path/to/your/project /path/to/output/project
```

## How It Works

The transformation scans all Java source files in the input directory and:
1. Identifies all `Yaml` constructor calls
2. Replaces them with compatible SnakeYAML 2.0 signatures
3. Preserves all existing functionality while fixing compatibility issues

## Example Fixes

### Before (SnakeYAML 1.x):
```java
Yaml yaml = new Yaml();
Yaml yaml = new Yaml(true);
Yaml yaml = new Yaml(constructor);
Yaml yaml = new Yaml(representer, constructor);
```

### After (SnakeYAML 2.0):
```java
Yaml yaml = new Yaml(new LoaderOptions());
Yaml yaml = new Yaml(new LoaderOptions()).setAllowDuplicateKeys(true);
Yaml yaml = new Yaml(constructor, new LoaderOptions());
Yaml yaml = new Yaml(representer, constructor, new LoaderOptions());
```

## Requirements

- Java 8 or higher
- Maven 3.x
- Spoon library (included in pom.xml)

## Customization

To use this with a different project, simply:
1. Change the input directory path when running the tool
2. The transformation is generic and will work with any project using SnakeYAML

## Files Included

- `SnakeYAML20Fix.java` - Main transformation class
- `SnakeYAML20FixExample.java` - Example usage
- `README.md` - Documentation