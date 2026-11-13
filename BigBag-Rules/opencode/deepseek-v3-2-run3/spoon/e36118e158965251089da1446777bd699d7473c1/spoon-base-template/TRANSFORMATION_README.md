# Generic Spoon Transformation for Breaking Dependency Updates

## Problem
The Artipie HTTP library v1.0.1 introduced a breaking change:
- **Old API**: `org.cactoos.map.MapEntry` (class from cactoos library)
- **New API**: `java.util.Map.Entry` (standard Java interface)

Projects using the old API fail to compile with errors:
```
[ERROR] package org.cactoos.map does not exist
[ERROR] cannot find symbol: class MapEntry
```

## Solution
This Spoon transformation automatically fixes the breaking change by:
1. **Replacing imports**: `import org.cactoos.map.MapEntry;` → `import java.util.AbstractMap;`
2. **Replacing constructor calls**: `new MapEntry<>("key", "value")` → `new AbstractMap.SimpleEntry<>("key", "value")`

## Transformation Pattern
- **Old Pattern**: `new MapEntry<K, V>(key, value)`
- **New Pattern**: `new AbstractMap.SimpleEntry<K, V>(key, value)`
- **Compatibility**: `AbstractMap.SimpleEntry` implements `Map.Entry<K, V>`

## Usage
```bash
# Compile the transformation
mvn clean compile

# Run on a project
java -cp target/spoon-base-1.0-SNAPSHOT.jar:$(find ~/.m2/repository -name "*.jar" | tr '\n' ':') \
  github.chains.Main /path/to/project/src
```

## Generic Applicability
This transformation is NOT project-specific. It can be applied to ANY Maven project affected by the same breaking change by:
1. Changing the source directory parameter
2. The transformation works structurally, not by hardcoded project identifiers

## Verification
After transformation:
- Compilation succeeds
- Tests pass
- Code uses standard Java `Map.Entry` API instead of third-party `org.cactoos.map.MapEntry`

## Extensibility
To adapt for other breaking changes:
1. Update the configuration constants (`OLD_FULLY_QUALIFIED_TYPE`, `NEW_FULLY_QUALIFIED_TYPE`)
2. Adjust the transformation logic in `transformImports()` and `transformConstructorCalls()`
3. Recompile and apply to affected projects

## Example Output
```
Transforming source directory: /workspace/files-adapter/src
Breaking change: org.cactoos.map.MapEntry -> java.util.AbstractMap.SimpleEntry
Transformed 4 constructor calls
Transformed 1 imports
Transformation completed successfully!
```