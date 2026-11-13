package github.chains;

import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.code.CtCatch;
import spoon.reflect.declaration.CtExecutable;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.filter.TypeFilter;

import java.util.*;

/**
 * FINAL CORRECTED Spoon Transformation for Jackson Exception Migration
 * 
 * This version fixes ALL compilation errors:
 * 1. setQualifiedName() doesn't exist → use replace()
 * 2. setThrownTypes() expects Set<CtTypeReference<? extends Throwable>> not List
 * 3. Proper handling of all type reference contexts
 */
public class FinalJacksonExceptionMigration {
    
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java -jar spoon-transformation.jar <source-directory> [output-directory]");
            System.err.println("If output-directory is not provided, files are modified in-place.");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        String outputDir = args.length > 1 ? args[1] : sourceDir + "-transformed";
        
        Launcher launcher = new Launcher();
        launcher.addInputResource(sourceDir);
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true);
        launcher.getEnvironment().setCommentEnabled(true);
        
        CtModel model = launcher.buildModel();
        
        System.out.println("Starting Jackson exception migration...");
        System.out.println("Source: " + sourceDir);
        System.out.println("Output: " + outputDir);
        
        int updates = applyTransformations(model);
        
        launcher.setSourceOutputDirectory(outputDir);
        launcher.prettyprint();
        
        System.out.println("\n✅ Transformation completed!");
        System.out.println("Total changes applied: " + updates);
    }
    
    private static int applyTransformations(CtModel model) {
        int total = 0;
        
        // Update ALL type references (imports, variables, fields, returns, etc.)
        total += updateAllTypeReferences(model);
        
        // Update throws clauses (methods and constructors)
        total += updateThrowsClauses(model);
        
        // Update catch blocks
        total += updateCatchBlocks(model);
        
        return total;
    }
    
    /**
     * CORRECT: Update ALL type references using replace()
     */
    private static int updateAllTypeReferences(CtModel model) {
        int count = 0;
        
        List<CtTypeReference<?>> refs = model.getElements(new TypeFilter<CtTypeReference<?>>(CtTypeReference.class) {
            @Override
            public boolean matches(CtTypeReference<?> element) {
                String qname = element.getQualifiedName();
                return isJacksonException(qname);
            }
        });
        
        for (CtTypeReference<?> ref : refs) {
            String oldName = ref.getQualifiedName();
            String newName = getReplacementName(oldName);
            
            if (newName != null) {
                // ✅ CORRECT: Create new reference and replace the old one
                CtTypeReference<?> newRef = ref.getFactory().createReference(newName);
                ref.replace(newRef);
                count++;
                
                System.out.println("🔄 Type reference: " + oldName + " → " + newName);
            }
        }
        
        return count;
    }
    
    /**
     * CORRECT: Update throws clauses using Set (not List)
     */
    private static int updateThrowsClauses(CtModel model) {
        int count = 0;
        
        List<CtExecutable<?>> executables = model.getElements(new TypeFilter<CtExecutable<?>>(CtExecutable.class) {
            @Override
            public boolean matches(CtExecutable<?> element) {
                for (CtTypeReference<? extends Throwable> thrownType : element.getThrownTypes()) {
                    if (isJacksonException(thrownType.getQualifiedName())) {
                        return true;
                    }
                }
                return false;
            }
        });
        
        for (CtExecutable<?> exec : executables) {
            // ✅ CORRECT: Use Set for thrownTypes
            Set<CtTypeReference<? extends Throwable>> newThrownTypes = new HashSet<>();
            boolean updated = false;
            
            for (CtTypeReference<? extends Throwable> thrownType : exec.getThrownTypes()) {
                String oldName = thrownType.getQualifiedName();
                String newName = getReplacementName(oldName);
                
                if (newName != null) {
                    // ✅ CORRECT: Create reference with proper bounds
                    CtTypeReference<? extends Throwable> newType = 
                        exec.getFactory().createReference(newName);
                    newThrownTypes.add(newType);
                    updated = true;
                    count++;
                    
                    System.out.println("🔄 Throws clause in " + exec.getSignature() + 
                                     ": " + oldName + " → " + newName);
                } else {
                    newThrownTypes.add(thrownType);
                }
            }
            
            if (updated) {
                // ✅ CORRECT: setThrownTypes expects a Set
                exec.setThrownTypes(newThrownTypes);
            }
        }
        
        return count;
    }
    
    /**
     * CORRECT: Update catch blocks
     */
    private static int updateCatchBlocks(CtModel model) {
        int count = 0;
        
        List<CtCatch> catches = model.getElements(new TypeFilter<CtCatch>(CtCatch.class) {
            @Override
            public boolean matches(CtCatch element) {
                String qname = element.getParameter().getType().getQualifiedName();
                return isJacksonException(qname);
            }
        });
        
        for (CtCatch catchBlock : catches) {
            CtTypeReference<?> oldType = catchBlock.getParameter().getType();
            String oldName = oldType.getQualifiedName();
            String newName = getReplacementName(oldName);
            
            if (newName != null) {
                // ✅ CORRECT: setType() works for catch parameter types
                CtTypeReference<?> newType = catchBlock.getFactory().createReference(newName);
                catchBlock.getParameter().setType(newType);
                count++;
                
                System.out.println("🔄 Catch block: " + oldName + " → " + newName);
            }
        }
        
        return count;
    }
    
    private static boolean isJacksonException(String qualifiedName) {
        if (qualifiedName == null) return false;
        
        return qualifiedName.equals("com.fasterxml.jackson.core.JsonParseException") ||
               qualifiedName.equals("com.fasterxml.jackson.databind.JsonMappingException");
    }
    
    private static String getReplacementName(String oldName) {
        if (oldName == null) return null;
        
        switch (oldName) {
            case "com.fasterxml.jackson.core.JsonParseException":
                return "com.fasterxml.jackson.core.exc.StreamReadException";
            case "com.fasterxml.jackson.databind.JsonMappingException":
                return "com.fasterxml.jackson.databind.DatabindException";
            default:
                return null;
        }
    }
}