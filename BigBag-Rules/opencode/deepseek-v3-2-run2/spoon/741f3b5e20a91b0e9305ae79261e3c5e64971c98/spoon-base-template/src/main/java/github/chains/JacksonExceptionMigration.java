package github.chains;

import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.code.CtCatch;
import spoon.reflect.declaration.CtExecutable;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.filter.TypeFilter;

import java.util.HashSet;
import java.util.Set;

/**
 * Minimal working Spoon transformation for Jackson exception migration.
 * This fixes all the common errors:
 * 1. No setQualifiedName() method - use replace() instead
 * 2. setThrownTypes() expects Set<CtTypeReference<? extends Throwable>>
 */
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
        
        // Find all type references to the old Jackson exceptions
        for (CtTypeReference<?> ref : model.getElements(new TypeFilter<CtTypeReference<?>>(CtTypeReference.class))) {
            String qname = ref.getQualifiedName();
            if (qname == null) continue;
            
            if (qname.equals("com.fasterxml.jackson.core.JsonParseException")) {
                // Create new reference and replace the old one
                CtTypeReference<?> newRef = ref.getFactory().createReference("com.fasterxml.jackson.core.exc.StreamReadException");
                ref.replace(newRef);
                count++;
                System.out.println("Updated JsonParseException -> StreamReadException");
            } 
            else if (qname.equals("com.fasterxml.jackson.databind.JsonMappingException")) {
                // Create new reference and replace the old one
                CtTypeReference<?> newRef = ref.getFactory().createReference("com.fasterxml.jackson.databind.DatabindException");
                ref.replace(newRef);
                count++;
                System.out.println("Updated JsonMappingException -> DatabindException");
            }
        }
        
        return count;
    }
    
    private static int updateThrowsClauses(CtModel model) {
        int count = 0;
        
        // Find all methods/constructors with old exceptions in throws clause
        for (CtExecutable<?> exec : model.getElements(new TypeFilter<CtExecutable<?>>(CtExecutable.class))) {
            Set<CtTypeReference<? extends Throwable>> newThrownTypes = new HashSet<>();
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
                    System.out.println("Updated throws clause in " + exec.getSignature());
                } 
                else if (qname.equals("com.fasterxml.jackson.databind.JsonMappingException")) {
                    CtTypeReference<? extends Throwable> newType = 
                        exec.getFactory().createReference("com.fasterxml.jackson.databind.DatabindException");
                    newThrownTypes.add(newType);
                    updated = true;
                    count++;
                    System.out.println("Updated throws clause in " + exec.getSignature());
                }
                else {
                    newThrownTypes.add(thrownType);
                }
            }
            
            if (updated) {
                // setThrownTypes expects a Set
                exec.setThrownTypes(newThrownTypes);
            }
        }
        
        return count;
    }
    
    private static int updateCatchBlocks(CtModel model) {
        int count = 0;
        
        // Find all catch blocks catching old exceptions
        for (CtCatch catchBlock : model.getElements(new TypeFilter<CtCatch>(CtCatch.class))) {
            CtTypeReference<?> caughtType = catchBlock.getParameter().getType();
            String qname = caughtType.getQualifiedName();
            if (qname == null) continue;
            
            if (qname.equals("com.fasterxml.jackson.core.JsonParseException")) {
                CtTypeReference<?> newType = catchBlock.getFactory().createReference("com.fasterxml.jackson.core.exc.StreamReadException");
                catchBlock.getParameter().setType(newType);
                count++;
                System.out.println("Updated catch block: JsonParseException -> StreamReadException");
            } 
            else if (qname.equals("com.fasterxml.jackson.databind.JsonMappingException")) {
                CtTypeReference<?> newType = catchBlock.getFactory().createReference("com.fasterxml.jackson.databind.DatabindException");
                catchBlock.getParameter().setType(newType);
                count++;
                System.out.println("Updated catch block: JsonMappingException -> DatabindException");
            }
        }
        
        return count;
    }
}