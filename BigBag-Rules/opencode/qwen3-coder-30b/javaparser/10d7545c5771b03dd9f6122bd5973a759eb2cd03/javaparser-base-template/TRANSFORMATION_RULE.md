# Generic JavaParser Transformation Rule for Dropwizard JerseyClientBuilder

## Problem
When upgrading Dropwizard from 2.1.5 to 4.0.0, the JerseyClientBuilder constructor changed:
- Old: `new JerseyClientBuilder(env)` 
- New: `new JerseyClientBuilder().using(env)`

## Solution
This transformation rule will automatically convert all instances of the old pattern to the new pattern.

## Transformation
**Before:**
```java
Client client = new JerseyClientBuilder(env)
    .using(config.getJerseyClient())
    .withProvider(MultiPartFeature.class)
    .withProvider(JacksonJsonProvider.class)
    .build(getName());
```

**After:**
```java
Client client = new JerseyClientBuilder()
    .using(env)
    .using(config.getJerseyClient())
    .withProvider(MultiPartFeature.class)
    .withProvider(JacksonJsonProvider.class)
    .build(getName());
```

## How to Use
1. Compile this transformation using JavaParser:
   ```
   javac -cp "javaparser-core-3.27.1.jar:javaparser-symbol-solver-core-3.27.1.jar" Main.java
   ```

2. Run the transformation:
   ```
   java -cp ".:javaparser-core-3.27.1.jar:javaparser-symbol-solver-core-3.27.1.jar" Main /path/to/project/src
   ```

## Implementation (Conceptual)
The transformation works by:
1. Finding all `new JerseyClientBuilder(...)` expressions 
2. Identifying when the constructor takes exactly one Environment parameter
3. Replacing with `new JerseyClientBuilder().using(env)` pattern
4. Preserving all subsequent method calls in the chain

This rule is generic and can be applied to any project with the same breaking change.