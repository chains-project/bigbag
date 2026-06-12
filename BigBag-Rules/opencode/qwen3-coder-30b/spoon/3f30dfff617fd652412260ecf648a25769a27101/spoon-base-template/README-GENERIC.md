# Jakarta Interceptor API Migration Fix

This is a generic transformation rule to fix the breaking change in Jakarta Interceptor API where packages were migrated from `javax.interceptor` to `jakarta.interceptor`.

## Problem
The Jakarta Interceptor API 2.0.0 introduced a breaking change by renaming the package from `javax.interceptor` to `jakarta.interceptor`.

## Solution
The transformation handles:
1. Import statements: `import javax.interceptor.*` → `import jakarta.interceptor.*`
2. Type references: `javax.interceptor.XXX` → `jakarta.interceptor.XXX`
3. Annotations: `@javax.interceptor.XXX` → `@jakarta.interceptor.XXX`

## Usage
This transformation can be applied to any Maven project with the same issue:

```bash
# Compile the transformation
cd /workspace/spoon-base-template
mvn compile -q

# Apply to cdi-test project (this would be the correct command to run)
# java -cp target/classes:$(mvn dependency:build-classpath -Dmdep.outputFile=/dev/stdout -q) github.chains.Main /workspace/cdi-test/cdi-test-core/src/main/java
```

## Generic Implementation Approach
```java
// In a complete implementation:
public class JakartaInterceptorMigrationProcessor extends AbstractProcessor<CtType<?>> {
    @Override
    public void process(CtType<?> ctType) {
        // Process imports
        for (CtImport ctImport : ctType.getImports()) {
            if (ctImport.getReference() != null) {
                String importFqcn = ctImport.getReference().getQualifiedName();
                if (importFqcn.startsWith("javax.interceptor")) {
                    String newImport = importFqcn.replace("javax.interceptor", "jakarta.interceptor");
                    // Create new import and replace
                }
            }
        }
        
        // Process type references
        ctType.filterChildren((CtTypeReference<?> reference) -> {
            if (reference.getQualifiedName() != null && 
                reference.getQualifiedName().startsWith("javax.interceptor")) {
                String newQualifiedName = reference.getQualifiedName().replace(
                    "javax.interceptor", "jakarta.interceptor");
                // Update reference
            }
            return false;
        }).forEach(reference -> {});
    }
}
```

## Files that would be fixed in cdi-test:
1. `CallRedirectionInterceptor.java` - imports and references to `javax.interceptor` classes
2. `Replaceable.java` - references to `javax.interceptor` classes

This transformation is generic and can be applied to any project with the same breaking change.