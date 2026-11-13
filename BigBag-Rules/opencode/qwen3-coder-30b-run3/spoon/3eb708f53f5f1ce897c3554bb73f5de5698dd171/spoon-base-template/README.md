# Google Translate API Breaking Change Fixer

A generic Spoon transformation designed to fix breaking changes in Maven projects when migrating Google Translate API from v2 to v3 or other versions.

## Features

- Generic transformation that can be reused across projects
- Identifies patterns of old API usage in method calls
- Replaces with new API usage patterns
- Handles common breaking changes like:
  - Method signature modifications
  - Class name changes
  - Package relocation
  - Deprecated method replacements

## Usage

```bash
# Build the project
mvn clean compile

# Run the transformation on a source directory
java -cp target/classes github.chains.Main /path/to/your/source/code
```

## What it Fixes

This transformer addresses common breaking changes when migrating from Google Translate API v2 to v3, including:

1. **Method Signature Changes**: Adjusting parameter requirements for translation methods
2. **Deprecated Methods**: Replacing deprecated API calls with newer equivalents
3. **Class/Package Renaming**: Updating import statements and class references
4. **API Structure Changes**: Adapting code to new hierarchical structures

## Configuration

The transformation is designed to be generic and reusable. You can extend it by:
1. Adding more specific patterns to detect in `isV2TranslateMethod()`, `isV2DetectMethod()` etc.
2. Implementing specific replacement logic in `fixV2TranslateMethod()`, `fixV2DetectMethod()` etc.
3. Adding custom rules in `fixParameterChanges()` and `fixDeprecatedMethodUsage()`

## Example Transformation

**Before (v2 API):**
```java
Translate translate = TranslateOptions.getDefaultInstance().getService();
Translation translation = translate.translate(
    "Hello world",
    Translate.TranslateOption.targetLanguage("es"),
    Translate.TranslateOption.sourceLanguage("en")
);
```

**After (v3 API):**
```java
TranslateClient translateClient = TranslateClient.create();
TranslateTextRequest request = TranslateTextRequest.newBuilder()
    .setContents(Collections.singletonList("Hello world"))
    .setTargetLanguage("es")
    .setSourceLanguage("en")
    .build();
TranslateTextResponse response = translateClient.translateText(request);
```

## Generic Framework Approach

This implementation follows a generic framework approach designed to work with any Maven project facing breaking changes:

- Uses structural pattern matching instead of hardcoded class names
- Applies transformation rules that are adaptable to different breaking changes
- Provides a foundation that can be extended for other APIs
- Maintains compatibility with multiple Java versions