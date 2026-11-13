# How to Use This Transformation

## Prerequisites
- Java 17+
- Maven 3+

## Building the Project
```bash
cd /workspace/spoon-base-template
mvn clean compile
```

## Running the Transformation
```bash
# Replace with actual path to your source code
java -cp target/classes:target/dependency/* github.chains.Main /path/to/your/java/sources
```

## Example of What This Fixes

### Before (broken):
```java
dns.managedZones().create(project, zone, change);
```

### After (fixed):
```java
dns.managedZones().create(project, zone, change, false); // added missing boolean parameter
```

## Extending for Specific Breaking Changes

To customize this for a particular breaking change:

1. Locate the specific method signature differences in the API documentation
2. Update the `fixMissingParameters()` method to handle the exact pattern
3. Add specific pattern matching logic for your breaking change scenario