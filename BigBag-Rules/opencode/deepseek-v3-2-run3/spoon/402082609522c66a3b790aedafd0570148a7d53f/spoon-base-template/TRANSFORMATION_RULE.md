# Generic SpongeAPI 8.0.0 Migration Transformation Rule

## Overview
This document describes a generic, reusable Spoon transformation rule for migrating SpongeAPI 7.x projects to SpongeAPI 8.0.0.

## Breaking Change Characterization

### 1. Type Renames (Old → New)
- `org.spongepowered.api.command.CommandSource` → `org.spongepowered.api.command.CommandCause`
- `org.spongepowered.api.command.args.CommandContext` → `org.spongepowered.api.command.parameter.CommandContext`
- `org.spongepowered.api.command.spec.CommandSpec` → `org.spongepowered.api.command.Command$Parameterized`
- `org.spongepowered.api.command.spec.CommandExecutor` → `org.spongepowered.api.command.CommandExecutor`
- `org.spongepowered.api.command.args.GenericArguments` → `org.spongepowered.api.command.parameter.Parameter`
- `org.spongepowered.api.text.Text` → `net.kyori.adventure.text.Component`
- `org.spongepowered.api.text.serializer.TextSerializers` → `net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer`

### 2. Method Signature Changes
**Old pattern:**
```java
public CommandResult execute(CommandSource src, CommandContext args)
```

**New pattern:**
```java
public CommandResult execute(CommandContext context) throws CommandException
```

### 3. Package Reorganizations
- `org.spongepowered.api.command.args` → `org.spongepowered.api.command.parameter`
- `org.spongepowered.api.command.spec` → `org.spongepowered.api.command`
- `org.spongepowered.api.text` → `net.kyori.adventure.text`

### 4. Static Method Changes
- `GenericArguments.string(Text)` → `Parameter.string()`
- `CommandSpec.builder()` → `Command.builder()`
- `Text.of(String)` → `Component.text(String)`

## Generic Spoon Transformation Template

```java
package github.chains;

import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.declaration.*;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.filter.TypeFilter;
import java.util.*;

public class GenericSpongeAPITransformer {
    
    // Generic type mappings - parameterize these for any project
    private static final Map<String, String> TYPE_MAPPINGS = Map.of(
        "org.spongepowered.api.command.CommandSource", 
        "org.spongepowered.api.command.CommandCause",
        "org.spongepowered.api.command.args.CommandContext",
        "org.spongepowered.api.command.parameter.CommandContext",
        "org.spongepowered.api.command.spec.CommandSpec",
        "org.spongepowered.api.command.Command$Parameterized",
        "org.spongepowered.api.command.spec.CommandExecutor",
        "org.spongepowered.api.command.CommandExecutor"
    );
    
    public void transform(String sourceDir) {
        Launcher launcher = new Launcher();
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true);
        launcher.addInputResource(sourceDir);
        
        CtModel model = launcher.buildModel();
        
        // 1. Transform type references
        for (Map.Entry<String, String> entry : TYPE_MAPPINGS.entrySet()) {
            transformType(model, entry.getKey(), entry.getValue());
        }
        
        // 2. Transform CommandExecutor implementations
        transformCommandExecutors(model);
        
        launcher.prettyprint();
    }
    
    private void transformType(CtModel model, String oldType, String newType) {
        model.getElements(new TypeFilter<CtTypeReference<?>>(CtTypeReference.class) {
            @Override
            public boolean matches(CtTypeReference<?> typeRef) {
                return oldType.equals(typeRef.getQualifiedName());
            }
        }).forEach(ref -> {
            ref.replace(ref.getFactory().createReference(newType));
        });
    }
    
    private void transformCommandExecutors(CtModel model) {
        model.getElements(new TypeFilter<CtClass<?>>(CtClass.class) {
            @Override
            public boolean matches(CtClass<?> clazz) {
                return clazz.getSuperInterfaces().stream()
                    .anyMatch(i -> "org.spongepowered.api.command.spec.CommandExecutor"
                        .equals(i.getQualifiedName()));
            }
        }).forEach(clazz -> {
            clazz.getSuperInterfaces().forEach(iface -> {
                if ("org.spongepowered.api.command.spec.CommandExecutor"
                    .equals(iface.getQualifiedName())) {
                    iface.replace(iface.getFactory()
                        .createReference("org.spongepowered.api.command.CommandExecutor"));
                }
            });
            
            // Update execute method signatures would go here
            // This requires more complex AST transformation
        });
    }
}
```

## Application to Any Project

To apply this transformation to **any** Maven project affected by the SpongeAPI 8.0.0 breaking change:

1. **Copy the template** to your transformation project
2. **Update `TYPE_MAPPINGS`** with any additional type changes specific to the project
3. **Run the transformation**:
   ```bash
   java -cp "spoon-core.jar:your-transformer.jar" \
        github.chains.GenericSpongeAPITransformer \
        /path/to/project/src/main/java
   ```

## Expected Results

After applying the transformation:

1. **Compilation errors fixed** for type mismatches
2. **Imports automatically updated** by Spoon
3. **Basic API compatibility restored**

## Limitations & Manual Work Required

The transformation handles:
- ✓ Type replacements
- ✓ Import updates  
- ✓ Interface reference updates

Manual work still needed:
- ✗ Method body transformations (e.g., `args.getOne()` → `context.requireOne()`)
- ✗ Complex API usage patterns
- ✗ Event listener updates
- ✗ Text serialization changes

## Validation

After transformation, run:
```bash
cd /path/to/project
mvn clean compile
```

Check for remaining compilation errors and apply manual fixes as needed.

## Generic Nature

This transformation rule is **generic** because:
1. It uses **pattern matching** on fully-qualified type names
2. It **traverses all files** in the source directory  
3. It applies the **same transformations** regardless of project structure
4. It's **parameterized** by type mappings, not hardcoded to specific classes

Any project with `CommandSource`, `CommandExecutor`, or other SpongeAPI 7.x types will receive the appropriate 8.0.0 replacements.