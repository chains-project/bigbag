# SnakeYAML 2.0 Constructor Fix Transformation

A generic Spoon transformation that fixes the breaking API change in SnakeYAML 2.0 where `Constructor(Class)` constructor now requires a `LoaderOptions` parameter.

## Breaking Change

- **Old API (SnakeYAML 1.x):** `Constructor(Class<?> rootClass)`
- **New API (SnakeYAML 2.0):** `Constructor(Class<?> rootClass, LoaderOptions loaderOptions)`

## Transformation Rule

The transformation finds all occurrences of:
```java
new Constructor(SomeClass.class)
```

And transforms them to:
```java
new Constructor(SomeClass.class, new LoaderOptions())
```

## Usage

1. Compile the transformation:
   ```bash
   mvn package
   ```

2. Run the transformation on your project:
   ```bash
   java -jar target/spoon-base-1.0-SNAPSHOT.jar /path/to/your/project/src/main/java
   ```

3. The transformed code will be written to `/tmp/spoon-output`

## Scope

This transformation is **generic and reusable** for any Java project affected by this SnakeYAML 2.0 breaking change. It:
- Uses pattern matching to identify `Constructor(Class)` calls
- Only transforms calls to `org.yaml.snakeyaml.constructor.Constructor`
- Adds the required `LoaderOptions` parameter
- Maintains proper imports
- Preserves code formatting and comments

## Example

Before:
```java
import org.yaml.snakeyaml.constructor.Constructor;
// ...
Constructor yamlTargetClass = new Constructor(Configuration.class);
```

After:
```java
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.constructor.Constructor;
// ...
Constructor yamlTargetClass = new Constructor(Configuration.class, new LoaderOptions());
```

## Requirements

- Java 17+
- Spoon 11.2.1+
- Maven 3.6+