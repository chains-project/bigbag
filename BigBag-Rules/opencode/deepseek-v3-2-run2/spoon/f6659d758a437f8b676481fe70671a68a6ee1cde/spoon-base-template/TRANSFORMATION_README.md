# SnakeYAML 2.1 TrustedTagInspector Transformation

## Breaking Change Analysis

**Problem:** SnakeYAML 2.1 removed the `TrustedTagInspector` class that existed in earlier versions.

**Old API Pattern (SnakeYAML < 2.1):**
```java
import org.yaml.snakeyaml.inspector.TrustedTagInspector;
// ...
LoaderOptions loadingConfig = new LoaderOptions();
loadingConfig.setTagInspector(new TrustedTagInspector());
```

**New API Pattern (SnakeYAML ≥ 2.1):**
```java
// No import needed for TrustedTagInspector
LoaderOptions loadingConfig = new LoaderOptions();
loadingConfig.setTagInspector(tag -> true);
```

**Breaking Change Details:**
- `TrustedTagInspector` class was completely removed
- Replacement: Use lambda expression or anonymous class implementing `TagInspector` interface
- `TagInspector` is a functional interface with single method: `boolean isGlobalTagAllowed(Tag tag)`
- `TrustedTagInspector` allowed all tags, equivalent to `tag -> true`

## Spoon Transformation

The transformation in `/workspace/spoon-base-template/src/main/java/github/chains/Main.java`:

1. **Finds**: All constructor calls to `TrustedTagInspector` (any package)
2. **Replaces**: `new TrustedTagInspector()` with lambda `tag -> true`
3. **Cleans up**: Removes `TrustedTagInspector` imports
4. **Preserves**: Code structure and formatting

**Transformation Rule:**
```java
// Before transformation
loadingConfig.setTagInspector(new TrustedTagInspector());

// After transformation  
loadingConfig.setTagInspector(tag -> true);
```

## Usage

```bash
# Compile the transformation
cd /workspace/spoon-base-template
mvn clean package

# Run on your project
java -cp "target/spoon-base-1.0-SNAPSHOT.jar:$(find ~/.m2/repository -name '*.jar' | grep -E '(spoon|commons-io|jsap)' | tr '\n' ':')" \
  github.chains.Main /path/to/your/project/src
```

## Testing

The transformation was tested on the Billy project:
- Successfully fixed `AbstractTest.java:66`
- Removed `import org.yaml.snakeyaml.inspector.TrustedTagInspector;`
- Project compiles and tests pass with snakeyaml 2.1

## Generic Applicability

This transformation works for:
- Any project upgrading from snakeyaml < 2.1 to ≥ 2.1
- Any usage of `TrustedTagInspector` constructor
- Projects using Maven, Gradle, or other build systems
- Java 8+ codebases (lambda support required)

## Edge Cases Handled

1. Fully qualified class names
2. Import statements (removed when no longer used)
3. Static imports
4. Multiple occurrences in same file
5. Comments and formatting preserved