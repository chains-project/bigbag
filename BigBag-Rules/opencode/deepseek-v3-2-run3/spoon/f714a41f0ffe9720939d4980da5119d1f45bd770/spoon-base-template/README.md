# PubNub 6.3.2 API Migration Transformation

## Overview
This is a generic Spoon transformation that migrates Java code from older PubNub API versions to version 6.3.2. The transformation handles the breaking changes introduced in PubNub 6.3.2 automatically.

## Breaking Changes Addressed

### 1. PNConfiguration Constructor Change
- **Old API**: `new PNConfiguration()` (no-arg constructor)
- **New API**: `new PNConfiguration(String)` or `new PNConfiguration(UserId)`
- **Transformation**: Converts no-arg constructor calls to use a generated UUID string

### 2. Object API Package Reorganization
- **Old imports**: `com.pubnub.api.models.consumer.pubsub.objects.*`
- **New imports**: `com.pubnub.api.models.consumer.objects_api.*`
- **Specific changes**:
  - `PNUserResult` → `PNUUIDMetadataResult` (different package)
  - `PNMembershipResult` → `PNMembershipResult` (different package)
  - `PNSpaceResult` removed (no direct equivalent)

### 3. SubscribeCallback Interface Redesign
- **Old interface**: Methods `user()`, `space()`, `membership()`
- **New interface**: Methods `uuid()`, `channel()`, `file()`, `membership()`, etc.
- **Transformation**:
  - Renames `user()` method to `uuid()`
  - Removes `space()` method (no equivalent)
  - Updates classes to extend `BaseSubscribeCallback` instead of implementing `SubscribeCallback` directly

## Usage

### Prerequisites
- Java 17+
- Maven
- Spoon dependency (included in pom.xml)

### Building the Transformation
```bash
cd /workspace/spoon-base-template
mvn clean package
```

### Running the Transformation
```bash
# Basic usage
java -cp "target/spoon-base-1.0-SNAPSHOT.jar:$(find ~/.m2/repository -name '*.jar' | grep -E '(spoon-core|commons-io|eclipse-jdt|jsap|slf4j-api)' | tr '\n' ':')" \
  github.chains.Main <input-source-directory> <output-directory>

# Example
java -cp "target/classes:$(find ~/.m2/repository -path '*/fr.inria.gforge.spoon/*' -name '*.jar' | head -5 | tr '\n' ':')" \
  github.chains.Main /path/to/project/src /path/to/transformed-output
```

### Example: Transforming XChange Project
```bash
java -cp "target/spoon-base-1.0-SNAPSHOT.jar:..." \
  github.chains.Main \
  /workspace/XChange/xchange-stream-service-pubnub/src/main/java \
  /workspace/transformed-pubnub
```

## Transformation Details

### What the Transformation Does
1. **PNConfiguration Constructor Fix**:
   - Finds all `new PNConfiguration()` calls
   - Replaces them with `new PNConfiguration("generated-uuid-...")`

2. **Type Reference Updates**:
   - Updates import statements for moved classes
   - Updates type references in method signatures and variable declarations

3. **SubscribeCallback Updates**:
   - Renames `user()` methods to `uuid()`
   - Removes `space()` methods
   - Changes classes implementing `SubscribeCallback` to extend `BaseSubscribeCallback`

### What the Transformation Does NOT Do
1. Does not add missing required methods to `SubscribeCallback` implementations
   - Instead, changes classes to extend `BaseSubscribeCallback` which provides empty implementations
2. Does not handle other PubNub API changes not listed above
3. Does not guarantee compilation success after transformation (manual fixes may still be needed)

## Output
The transformation preserves the original code structure while making the minimal necessary changes. The output will be in the specified output directory with the same package structure as the input.

## Limitations
- Requires Spoon and its dependencies to be available in the classpath
- May not handle all edge cases (complex inheritance hierarchies, reflection, etc.)
- Source code must be compilable by Spoon's Java parser

## Customization
To adapt this transformation for other API migrations:
1. Modify the type mappings in `updateTypeReferences()` method
2. Adjust the constructor fixes in `fixPNConfigurationConstructors()` method  
3. Update the interface/class name checks in `updateSubscribeCallbackClasses()` method

## Testing
The transformation has been:
1. Compiled successfully with Maven
2. Designed to handle the specific PubNub 6.3.2 breaking changes
3. Structured generically to work on any Java project using the affected PubNub APIs