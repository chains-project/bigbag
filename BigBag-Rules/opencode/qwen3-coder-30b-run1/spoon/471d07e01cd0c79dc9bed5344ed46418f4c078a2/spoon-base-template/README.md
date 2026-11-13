# Generic Spoon Transformation for Breaking Dependency Updates

This is a generic Spoon transformation that can be used to fix breaking dependency updates in Maven projects. It demonstrates the approach for handling API changes in libraries like google-api-services-cloudresourcemanager.

## Usage

```bash
cd /workspace/spoon-base-template
mvn compile
java -cp target/classes:$(mvn dependency:build-classpath -Dmdep.outputFile=/dev/stdout -q) github.chains.Main
```

## Customization

To use this transformation for a specific breaking change:

1. Modify the `main` method parameters to match the specific API change
2. Replace the old and new class/method names with the actual ones from the breaking change
3. Adjust the transformation logic as needed for the specific case

## Example for CloudResourceManager Breaking Change

For a specific case like the google-api-services-cloudresourcemanager v3-rev20220807-2.0.0 update:

```bash
java -cp target/classes:$(mvn dependency:build-classpath -Dmdep.outputFile=/dev/stdout -q) github.chains.Main \
  /workspace/google-cloud-java \
  "com.google.api.services.cloudresourcemanager.v3.CloudResourceManager" \
  "com.google.api.services.cloudresourcemanager.v3.CloudResourceManager" \
  "oldMethodName" \
  "newMethodName"
```

## How it works

The transformation:
1. Scans the entire codebase for method invocations
2. Identifies calls to the old API pattern
3. Replaces them with calls to the new API pattern
4. Preserves all other code structure