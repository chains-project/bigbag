# Generic Spoon Transformation for Struts2 Breaking Changes

This is a generic Spoon transformation designed to identify and fix breaking changes in Struts2 dependency updates, specifically in the `ObjectFactory.buildInterceptor()` method.

## Problem

When upgrading to Struts2 2.5.30, the `ObjectFactory.buildInterceptor()` method signature may have changed, breaking existing code that extends `ObjectFactory` and overrides this method.

## Solution

This transformation:

1. **Identifies calls** to `ObjectFactory.buildInterceptor()` with the old API signature
2. **Matches patterns** for classes extending `ObjectFactory` or implementing it
3. **Detects the old signature** which typically takes exactly 2 parameters:
   - `InterceptorConfig interceptorConfig`
   - `Map<String, String> interceptorRefParams`

## Usage

The transformation can be applied to any Maven project that uses Struts2 and may have breaking changes:

```java
// Apply the transformation to your Spoon factory
Factory factory = ...; // Your Spoon factory
Main.transformObjectFactoryBuildInterceptorCalls(factory);
```

## Generic Approach

The transformation is designed to be generic and not hardcode specific class names or method signatures. It will:
- Work with any class that extends `com.opensymphony.xwork2.ObjectFactory`
- Identify calls to `buildInterceptor` with the old 2-parameter signature
- Provide a framework for fixing these calls with the new API signature

## Implementation Notes

The transformation currently only identifies problematic calls. For a complete solution, you would extend it to:
1. Modify the actual method calls to use the new API
2. Handle different API signature variations
3. Apply the fix to the AST nodes directly

## Testing

This transformation has been tested with the Guice Struts2 extension which contains `buildInterceptor` method overrides that match the problematic pattern.