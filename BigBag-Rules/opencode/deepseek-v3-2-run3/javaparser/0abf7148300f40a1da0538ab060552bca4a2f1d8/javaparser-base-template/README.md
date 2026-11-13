# Generic JavaParser Transformation for JasperReports 6.19.1 Breaking Changes

## Overview
This JavaParser-based transformation fixes compilation errors caused by JasperReports 6.19.1 API breaking changes where method signatures changed from primitive types to boxed types.

## Breaking Change Characterization
- **Old API pattern**: `methodName(primitiveType)` (e.g., `setLineWidth(int)`, `setFontSize(float)`)
- **New API pattern**: `methodName(BoxedType)` (e.g., `setLineWidth(Float)`, `setFontSize(Float)`)
- **Structural transformation**: Wrap primitive expressions with `BoxedType.valueOf(primitiveExpr)`

## Affected Methods
The transformation handles these JasperReports 6.19.1 method changes:
- `setLineWidth` → `Float.valueOf()`
- `setFontSize` → `Float.valueOf()`  
- `setLineSpacingSize` → `Float.valueOf()`
- `setBackgroundAlpha` → `Float.valueOf()`
- `setForegroundAlpha` → `Float.valueOf()`
- `setAxisLineWidth` → `Float.valueOf()`
- `setFloatFontSize` → `Float.valueOf()`
- `setMinPercentage` → `Float.valueOf()`

## Usage
```bash
java -cp "target/classes:dependencies/*" github.chains.Main /path/to/source/code
```

## How It Works
1. Scans all `.java` files in the specified directory
2. Identifies method calls to affected methods
3. Checks if arguments are primitive expressions needing boxing
4. Wraps primitive arguments with appropriate `BoxedType.valueOf()` calls
5. Avoids double-wrapping (checks for existing boxing)

## Example Transformation
**Before:**
```java
textField.getLineBox().getPen().setLineWidth(border.getLineWidth());
```

**After:**
```java
textField.getLineBox().getPen().setLineWidth(Float.valueOf(border.getLineWidth()));
```

## Extending for Other Breaking Changes
To handle additional breaking changes:
1. Add method name and wrapper type to `METHOD_WRAPPERS` map in `Main.java`
2. The transformation will automatically apply to new methods

## Limitations
- Uses heuristic detection (no full type inference)
- May miss some edge cases
- Assumes 1-argument methods
- Primitive detection is based on string patterns

## Testing
The transformation was tested on the biapi project and successfully fixed:
- `setLineWidth(int)` → `setLineWidth(Float.valueOf(int))`
- Compilation succeeded after transformation
- All tests passed