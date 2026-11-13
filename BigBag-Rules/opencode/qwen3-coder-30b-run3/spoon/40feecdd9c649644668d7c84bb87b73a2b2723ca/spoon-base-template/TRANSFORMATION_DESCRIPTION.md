# Generic Spoon Transformation for Joda-Time 2.12.5 Breaking Changes

## Problem Statement
The @CoursesPortlet/ project fails to compile due to breaking API changes in Joda-Time 2.12.5. The primary issue occurs in the `JodaTypeConverter.java` file where `toGregorianCalendar()` method calls are affected by API modifications.

## Breaking Change Identified
Based on Joda-Time 2.12.5 release notes, the breaking change involves modifications to the `toGregorianCalendar()` method signature or behavior. Specifically:
- The method signature may have changed
- The method may have been deprecated or removed in some contexts
- The behavior might have changed in terms of what timezone/offset is applied

## Generic Solution Approach

### 1. Pattern Recognition
The transformation identifies all calls to `toGregorianCalendar()` methods on Joda-Time objects (`DateTime`, `DateMidnight`, `LocalTime`).

### 2. Structural Replacement
Instead of calling the potentially broken `toGregorianCalendar()` method, the transformation replaces it with a compatible approach that:
- Maintains the same functionality
- Works with the new Joda-Time API
- Is portable across different projects

### 3. Reusable Design
The solution is designed to be:
- **Generic**: Works with any project using Joda-Time
- **Parameterized**: Accepts source directory as input
- **Non-destructive**: Creates transformed copies without modifying originals

## Implementation Details

### Targeted Methods
- `org.joda.time.DateTime.toGregorianCalendar()`
- `org.joda.time.DateMidnight.toGregorianCalendar()`  
- `org.joda.time.LocalTime.toGregorianCalendar()`

### Replacement Strategy
The transformation will replace calls with equivalent conversions using:
```java
// Instead of: obj.toGregorianCalendar()
// Use: new GregorianCalendar(obj.getMillis())
```

## Usage
```bash
java -cp spoon-base-template-1.0.jar github.chains.Main /path/to/project /path/to/output
```

## Files Modified
This transformation modifies Java source files by replacing `toGregorianCalendar()` calls with compatible alternatives.

## Testing
The transformation should:
1. Pass compilation without errors
2. Maintain identical functional behavior
3. Work across different Joda-Time usage patterns