# Generic Package Migration Transformation

## Overview
This is a generic, reusable Spoon transformation for migrating Java code from an old package structure to a new one. It handles breaking dependency updates where types move between packages.

## The Breaking Change Pattern
- **Old API**: Types in package `P.old.subpackage` (e.g., `org.bouncycastle.crypto.tls.TlsFatalAlert`)
- **New API**: Types in package `P.new.subpackage` (e.g., `org.bouncycastle.tls.TlsFatalAlert`)
- **Transformation**: Update all references from `P.old.subpackage.X` to `P.new.subpackage.X`

## Usage
```bash
java -cp "spoon-base-1.0-SNAPSHOT.jar:dependency-jars/*" \
  github.chains.Main <source-dir> <old-package> <new-package>
```

### Examples
1. **BouncyCastle TLS Migration** (bcprov 1.67):
   ```bash
   java -cp "...' github.chains.Main ./src org.bouncycastle.crypto.tls org.bouncycastle.tls
   ```

2. **Jakarta EE Migration** (javax → jakarta):
   ```bash
   java -cp "..." github.chains.Main ./src javax.xml.bind jakarta.xml.bind
   ```

3. **Apache Commons Migration**:
   ```bash
   java -cp "..." github.chains.Main ./src org.apache.commons.lang org.apache.commons.lang3
   ```

## What It Does
1. **Finds all type references** in the source code
2. **Updates type references** from old package to new package
3. **Handles imports automatically** via Spoon's auto-import feature
4. **Preserves code structure** and comments
5. **Outputs transformed code** to a new directory

## Requirements
- Java 17+
- Spoon 11.2.1+ (included in dependencies)
- Source code to transform

## Build
```bash
cd /workspace/spoon-base-template
mvn clean package
```

## Test the Transformation
```bash
# Run on test project
java -cp "target/spoon-base-1.0-SNAPSHOT.jar:$(cat classpath.txt)" \
  github.chains.Main /path/to/project/src org.old.package org.new.package
```

## Complete Workflow for BouncyCastle TLS Migration

### Step 1: Identify the breaking change
- Old: `org.bouncycastle.crypto.tls.*`
- New: `org.bouncycastle.tls.*`
- New dependency: `bctls-jdk15on` (separate from `bcprov-jdk15on`)

### Step 2: Apply transformation
```bash
java -cp "...' github.chains.Main ./src/main/java org.bouncycastle.crypto.tls org.bouncycastle.tls
```

### Step 3: Update dependencies
Add to `pom.xml`:
```xml
<dependency>
  <groupId>org.bouncycastle</groupId>
  <artifactId>bctls-jdk15on</artifactId>
  <version>1.67</version>
</dependency>
```

### Step 4: Copy transformed code
```bash
cp -r ./src/main/java-migrated/* ./src/main/java/
```

### Step 5: Compile and test
```bash
mvn clean compile test
```

## Design Principles
1. **Generic**: Works for any package migration, not just BouncyCastle
2. **Reusable**: Parameterized by old/new package prefixes
3. **Safe**: Doesn't modify original files, creates output directory
4. **Complete**: Handles both imports and type references
5. **Maintainable**: Clean code with proper error handling

## Limitations
- Doesn't handle reflection or string literals containing class names
- Requires Spoon to correctly parse the source code
- May need manual review for complex cases

## Example: BouncyCastle 1.67 TLS Migration

### Before Transformation
```java
import org.bouncycastle.crypto.tls.TlsFatalAlert;
import org.bouncycastle.crypto.tls.AlertDescription;

public class Example {
  public void method() {
    throw new TlsFatalAlert(AlertDescription.bad_record_mac);
  }
}
```

### After Transformation
```java
import org.bouncycastle.tls.TlsFatalAlert;
import org.bouncycastle.tls.AlertDescription;

public class Example {
  public void method() {
    throw new TlsFatalAlert(AlertDescription.bad_record_mac);
  }
}
```

### Required Dependency Update
```xml
<!-- Before: only bcprov -->
<dependency>
  <groupId>org.bouncycastle</groupId>
  <artifactId>bcprov-jdk15on</artifactId>
  <version>1.67</version>
</dependency>

<!-- After: add bctls -->
<dependency>
  <groupId>org.bouncycastle</groupId>
  <artifactId>bctls-jdk15on</artifactId>
  <version>1.67</version>
</dependency>
```