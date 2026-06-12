# Generic Spoon Transformation for Storages -> StoragesLoader Migration

This transformation fixes the breaking change in `com.artipie.asto` dependency where `com.artipie.asto.factory.Storages` was replaced with `com.artipie.asto.factory.StoragesLoader`.

## Problem

In version 1.15.3 of `asto-core`, the `Storages` class was replaced with `StoragesLoader`. This breaks code that calls:
```java
new Storages().newStorage(type, config)
```

## Solution

This transformation automatically converts the old API call to the new API:
```java
new Storages().newStorage(type, config)  →  new StoragesLoader().newObject(type, config)
```

## Usage

1. Compile the transformation: `mvn compile`
2. Run the transformation on any Java project:
   ```bash
   java -cp target/classes:$(mvn dependency:build-classpath -Dmdep.outputFile=/dev/stdout -q) github.chains.Main
   ```

## How It Works

The transformation:
1. Detects calls to `Storages.newStorage()` method
2. Replaces the target type from `Storages` to `StoragesLoader`
3. Replaces the method call from `newStorage` to `newObject`

This is a generic solution that can be applied to any project with this breaking change.