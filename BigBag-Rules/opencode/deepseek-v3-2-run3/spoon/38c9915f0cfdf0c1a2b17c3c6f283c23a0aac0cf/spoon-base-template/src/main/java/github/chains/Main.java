package github.chains;

import spoon.MavenLauncher;
import spoon.reflect.CtModel;
import spoon.reflect.declaration.CtImport;
import spoon.reflect.declaration.CtType;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.Filter;
import spoon.reflect.code.CtConstructorCall;
import spoon.reflect.code.CtNewClass;
import spoon.reflect.code.CtExpression;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.code.CtFieldRead;
import spoon.reflect.code.CtFieldWrite;
import spoon.reflect.code.CtVariableAccess;
import spoon.reflect.visitor.Query;
import spoon.reflect.visitor.filter.TypeFilter;
import spoon.support.visitor.ProcessingTemplate;
import spoon.support.visitor.ReplaceTemplate;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.ArrayList;

public class Main {
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java -jar spoon-transformation.jar <source-directory>");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        System.out.println("Processing source directory: " + sourceDir);
        
        // Create Maven launcher for the project
        MavenLauncher launcher = new MavenLauncher(sourceDir, MavenLauncher.SOURCE_TYPE.APP_SOURCE);
        
        // Build the model
        CtModel model = launcher.buildModel();
        
        // Apply transformations
        applyCactoosBreakingChanges(model);
        
        // Write transformed code back
        launcher.setSourceOutputDirectory(sourceDir);
        launcher.prettyprint();
        
        System.out.println("Transformation completed!");
    }
    
    private static void applyCactoosBreakingChanges(CtModel model) {
        System.out.println("Applying cactoos 0.55.0 breaking changes...");
        
        // Map of old fully-qualified names to new fully-qualified names
        Map<String, String> classRenames = new HashMap<>();
        
        // Scalar classes: remove "Scalar" suffix
        classRenames.put("org.cactoos.scalar.IoCheckedScalar", "org.cactoos.scalar.IoChecked");
        classRenames.put("org.cactoos.scalar.StickyScalar", "org.cactoos.scalar.Sticky");
        classRenames.put("org.cactoos.scalar.UncheckedScalar", "org.cactoos.scalar.Unchecked");
        classRenames.put("org.cactoos.scalar.SolidScalar", "org.cactoos.scalar.Solid");
        
        // Text classes: remove "Text" suffix
        classRenames.put("org.cactoos.text.JoinedText", "org.cactoos.text.Joined");
        classRenames.put("org.cactoos.text.SplitText", "org.cactoos.text.Split");
        classRenames.put("org.cactoos.text.TrimmedText", "org.cactoos.text.Trimmed");
        
        // Package change
        classRenames.put("org.cactoos.iterable.LengthOf", "org.cactoos.scalar.LengthOf");
        
        // Special case: CollectionOf doesn't exist in new API
        // We'll handle this separately
        
        // Apply all renames
        applyAllRenames(model, classRenames);
        
        // Special handling for CollectionOf -> needs to be replaced with LengthOf
        replaceCollectionOfWithLengthOf(model);
    }
    
    private static void applyAllRenames(CtModel model, Map<String, String> renames) {
        // Apply to all type references first
        applyTypeReferenceRenames(model, renames);
        
        // Apply to constructor calls
        applyConstructorCallRenames(model, renames);
        
        // Apply to imports (must be done after type references to avoid conflicts)
        applyImportRenames(model, renames);
    }
    
    private static void applyImportRenames(CtModel model, Map<String, String> renames) {
        List<CtImport> imports = model.getElements(new TypeFilter<>(CtImport.class));
        for (CtImport imp : imports) {
            // Get the imported type reference
            CtTypeReference<?> importedType = imp.getReference();
            if (importedType != null) {
                String qualifiedName = importedType.getQualifiedName();
                
                for (Map.Entry<String, String> entry : renames.entrySet()) {
                    String oldFqn = entry.getKey();
                    String newFqn = entry.getValue();
                    
                    if (qualifiedName.equals(oldFqn)) {
                        System.out.println("Renaming import: " + oldFqn + " -> " + newFqn);
                        
                        // Create new import reference
                        CtTypeReference<?> newRef = imp.getFactory().Type().createReference(newFqn);
                        
                        // Create new import statement
                        CtImport newImport = imp.getFactory().createImport(newRef);
                        
                        // Replace the old import
                        imp.replace(newImport);
                    }
                }
            }
        }
    }
    
    private static void applyTypeReferenceRenames(CtModel model, Map<String, String> renames) {
        // Find all type references in the code (excluding imports which we handle separately)
        List<CtTypeReference<?>> typeRefs = model.getElements(new TypeFilter<>(CtTypeReference.class));
        
        for (CtTypeReference<?> typeRef : typeRefs) {
            // Skip references that are part of imports (we'll handle imports separately)
            if (typeRef.getParent() instanceof CtImport) {
                continue;
            }
            
            String qualifiedName = typeRef.getQualifiedName();
            
            for (Map.Entry<String, String> entry : renames.entrySet()) {
                String oldFqn = entry.getKey();
                String newFqn = entry.getValue();
                
                if (qualifiedName.equals(oldFqn)) {
                    System.out.println("Renaming type reference: " + oldFqn + " -> " + newFqn);
                    
                    // Replace the type reference
                    CtTypeReference<?> newRef = typeRef.getFactory().Type().createReference(newFqn);
                    typeRef.replace(newRef);
                }
            }
        }
    }
    
    private static void applyConstructorCallRenames(CtModel model, Map<String, String> renames) {
        // Find all constructor calls
        List<CtConstructorCall<?>> constructorCalls = model.getElements(new TypeFilter<>(CtConstructorCall.class));
        
        for (CtConstructorCall<?> constrCall : constructorCalls) {
            CtTypeReference<?> constrType = constrCall.getType();
            String qualifiedName = constrType.getQualifiedName();
            
            for (Map.Entry<String, String> entry : renames.entrySet()) {
                String oldFqn = entry.getKey();
                String newFqn = entry.getValue();
                
                if (qualifiedName.equals(oldFqn)) {
                    System.out.println("Renaming constructor call: " + oldFqn + " -> " + newFqn);
                    
                    // Create new constructor call with updated type
                    CtTypeReference<?> newTypeRef = constrType.getFactory().Type().createReference(newFqn);
                    
                    // Create new constructor call
                    CtConstructorCall<?> newConstrCall = constrCall.getFactory().createConstructorCall(
                        newTypeRef,
                        constrCall.getArguments()
                    );
                    
                    constrCall.replace(newConstrCall);
                }
            }
        }
    }
    
    private static void replaceCollectionOfWithLengthOf(CtModel model) {
        // Find invocations of size() on CollectionOf instances
        List<CtInvocation<?>> invocations = model.getElements(new TypeFilter<>(CtInvocation.class));
        
        for (CtInvocation<?> invoc : invocations) {
            // Check if this is a size() invocation
            if ("size".equals(invoc.getExecutable().getSimpleName())) {
                CtExpression<?> target = invoc.getTarget();
                if (target != null) {
                    // Check if target is a CollectionOf constructor call
                    if (target instanceof CtConstructorCall) {
                        CtConstructorCall<?> constrCall = (CtConstructorCall<?>) target;
                        String typeName = constrCall.getType().getQualifiedName();
                        
                        if ("org.cactoos.collection.CollectionOf".equals(typeName)) {
                            System.out.println("Replacing CollectionOf().size() with LengthOf()");
                            
                            // Get the argument passed to CollectionOf constructor
                            CtExpression<?> collectionArg = constrCall.getArguments().get(0);
                            
                            // Create new LengthOf constructor call
                            CtTypeReference<?> lengthOfType = invoc.getFactory().Type().createReference("org.cactoos.scalar.LengthOf");
                            CtConstructorCall<?> newLengthOfCall = invoc.getFactory().createConstructorCall(
                                lengthOfType,
                                collectionArg
                            );
                            
                            // Replace the entire invocation with the new LengthOf constructor call
                            invoc.replace(newLengthOfCall);
                        }
                    }
                }
            }
        }
        
        // Also need to handle imports of CollectionOf
        List<CtImport> imports = model.getElements(new TypeFilter<>(CtImport.class));
        for (CtImport imp : imports) {
            CtTypeReference<?> importedType = imp.getReference();
            if (importedType != null && "org.cactoos.collection.CollectionOf".equals(importedType.getQualifiedName())) {
                System.out.println("Removing import of CollectionOf");
                // Since CollectionOf doesn't exist anymore, we should remove the import
                // The code using it will have been transformed above
                imp.delete();
            }
        }
    }
}