# Summary: Correct Spoon Transformation for Jackson Exception Migration

## The Problem
You were getting compilation errors with your Spoon transformation code:
1. `setQualifiedName()` method doesn't exist on `CtTypeReference`
2. Type mismatch for `setThrownTypes()` method

## The Solution

### Error 1: `setQualifiedName()` doesn't exist
**Wrong:**
```java
ref.setQualifiedName("com.fasterxml.jackson.core.exc.StreamReadException");
```

**Correct:**
```java
CtTypeReference<?> newRef = ref.getFactory().createReference("com.fasterxml.jackson.core.exc.StreamReadException");
ref.replace(newRef);
```

### Error 2: Type mismatch for `setThrownTypes()`
**Wrong:**
```java
List<CtTypeReference<?>> newThrownTypes = new ArrayList<>();
method.setThrownTypes(newThrownTypes); // Compilation error!
```

**Correct:**
```java
Set<CtTypeReference<? extends Throwable>> newThrownTypes = new HashSet<>();
// ... populate set ...
method.setThrownTypes(newThrownTypes); // Works!
```

## Complete Working Transformation

Here's the complete corrected transformation code:

```java
package github.chains;

import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.code.CtCatch;
import spoon.reflect.declaration.CtExecutable;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.filter.TypeFilter;

import java.util.HashSet;
import java.util.Set;

public class JacksonExceptionMigration {
    
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java -jar spoon-transformation.jar <source-directory>");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        
        Launcher launcher = new Launcher();
        launcher.addInputResource(sourceDir);
        launcher.getEnvironment().setNoClasspath(false);
        launcher.getEnvironment().setAutoImports(true);
        launcher.getEnvironment().setCommentEnabled(true);
        
        CtModel model = launcher.buildModel();
        
        System.out.println("Applying Jackson 2.13+ exception migration...");
        
        int updates = 0;
        
        // 1. Update ALL type references (imports, variables, fields, returns, etc.)
        updates += updateTypeReferences(model);
        
        // 2. Update throws clauses
        updates += updateThrowsClauses(model);
        
        // 3. Update catch blocks
        updates += updateCatchBlocks(model);
        
        // Apply transformations
        launcher.setSourceOutputDirectory(sourceDir);
        launcher.prettyprint();
        
        System.out.println("\nTransformation completed!");
        System.out.println("Total updates: " + updates);
    }
    
    private static int updateTypeReferences(CtModel model) {
        int count = 0;
        
        for (CtTypeReference<?> ref : model.getElements(new TypeFilter<CtTypeReference<?>>(CtTypeReference.class))) {
            String qname = ref.getQualifiedName();
            if (qname == null) continue;
            
            if (qname.equals("com.fasterxml.jackson.core.JsonParseException")) {
                CtTypeReference<?> newRef = ref.getFactory().createReference("com.fasterxml.jackson.core.exc.StreamReadException");
                ref.replace(newRef); // CORRECT: Use replace(), not setQualifiedName()
                count++;
            } 
            else if (qname.equals("com.fasterxml.jackson.databind.JsonMappingException")) {
                CtTypeReference<?> newRef = ref.getFactory().createReference("com.fasterxml.jackson.databind.DatabindException");
                ref.replace(newRef); // CORRECT: Use replace(), not setQualifiedName()
                count++;
            }
        }
        
        return count;
    }
    
    private static int updateThrowsClauses(CtModel model) {
        int count = 0;
        
        for (CtExecutable<?> exec : model.getElements(new TypeFilter<CtExecutable<?>>(CtExecutable.class))) {
            Set<CtTypeReference<? extends Throwable>> newThrownTypes = new HashSet<>(); // CORRECT: Use Set, not List
            boolean updated = false;
            
            for (CtTypeReference<? extends Throwable> thrownType : exec.getThrownTypes()) {
                String qname = thrownType.getQualifiedName();
                if (qname == null) {
                    newThrownTypes.add(thrownType);
                    continue;
                }
                
                if (qname.equals("com.fasterxml.jackson.core.JsonParseException")) {
                    CtTypeReference<? extends Throwable> newType = 
                        exec.getFactory().createReference("com.fasterxml.jackson.core.exc.StreamReadException");
                    newThrownTypes.add(newType);
                    updated = true;
                    count++;
                } 
                else if (qname.equals("com.fasterxml.jackson.databind.JsonMappingException")) {
                    CtTypeReference<? extends Throwable> newType = 
                        exec.getFactory().createReference("com.fasterxml.jackson.databind.DatabindException");
                    newThrownTypes.add(newType);
                    updated = true;
                    count++;
                }
                else {
                    newThrownTypes.add(thrownType);
                }
            }
            
            if (updated) {
                exec.setThrownTypes(newThrownTypes); // CORRECT: Now accepts the Set
            }
        }
        
        return count;
    }
    
    private static int updateCatchBlocks(CtModel model) {
        int count = 0;
        
        for (CtCatch catchBlock : model.getElements(new TypeFilter<CtCatch>(CtCatch.class))) {
            CtTypeReference<?> caughtType = catchBlock.getParameter().getType();
            String qname = caughtType.getQualifiedName();
            if (qname == null) continue;
            
            if (qname.equals("com.fasterxml.jackson.core.JsonParseException")) {
                CtTypeReference<?> newType = catchBlock.getFactory().createReference("com.fasterxml.jackson.core.exc.StreamReadException");
                catchBlock.getParameter().setType(newType);
                count++;
            } 
            else if (qname.equals("com.fasterxml.jackson.databind.JsonMappingException")) {
                CtTypeReference<?> newType = catchBlock.getFactory().createReference("com.fasterxml.jackson.databind.DatabindException");
                catchBlock.getParameter().setType(newType);
                count++;
            }
        }
        
        return count;
    }
}
```

## Key Takeaways

1. **`setQualifiedName()` doesn't exist** on `CtTypeReference`. Use `replace()` with a new type reference instead.
2. **`setThrownTypes()` expects a `Set<CtTypeReference<? extends Throwable>>`**, not a `List`.
3. **Process ALL type references** to handle imports, variable types, field types, return types, etc.
4. **Use `getFactory().createReference()`** to create new type references.
5. **Spoon handles imports automatically** when `setAutoImports(true)` is enabled.

## Build and Run

1. **Build:**
   ```bash
   cd /workspace/spoon-base-template
   mvn clean compile
   ```

2. **Create JAR:**
   ```bash
   mvn package
   ```

3. **Run:**
   ```bash
   java -jar target/spoon-base-1.0-SNAPSHOT.jar /path/to/source/code
   ```

The transformation will update all references to the old Jackson exceptions with their new counterparts, handling imports, throws clauses, catch blocks, and all other type references automatically.