package github.chains;

import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.code.*;
import spoon.reflect.declaration.*;
import spoon.reflect.reference.*;
import spoon.reflect.visitor.Query;
import spoon.reflect.visitor.filter.*;
import java.util.*;

/**
 * Simplified Spoon transformation to fix Joda-Time DateMidnight deprecation.
 * Focuses on specific files to avoid compilation issues.
 */
public class SimpleMain {
    
    private static final String OLD_TYPE = "org.joda.time.DateMidnight";
    private static final String NEW_TYPE = "org.joda.time.LocalDate";
    
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java -jar spoon-transformation.jar <source-file-or-dir>");
            System.exit(1);
        }
        
        String sourcePath = args[0];
        System.out.println("Transforming: " + sourcePath);
        
        // Create Spoon launcher
        Launcher launcher = new Launcher();
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true);
        launcher.getEnvironment().setCommentEnabled(true);
        launcher.addInputResource(sourcePath);
        
        try {
            // Build the model
            CtModel model = launcher.buildModel();
            
            // Get the root package
            CtPackage rootPackage = model.getRootPackage();
            
            // Apply transformations
            int changes = transformAll(rootPackage);
            
            System.out.println("Total changes applied: " + changes);
            
            // Write transformed code back
            launcher.setSourceOutputDirectory(sourcePath + "-transformed");
            launcher.prettyprint();
            
            System.out.println("Transformation completed. Output in: " + sourcePath + "-transformed");
            
        } catch (Exception e) {
            System.err.println("Error during transformation: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    private static int transformAll(CtElement root) {
        int count = 0;
        
        // Transform type references
        List<CtTypeReference<?>> typeRefs = Query.getElements(root, 
            new TypeFilter<CtTypeReference<?>>(CtTypeReference.class) {
                @Override
                public boolean matches(CtTypeReference<?> typeRef) {
                    return OLD_TYPE.equals(typeRef.getQualifiedName());
                }
            });
        
        for (CtTypeReference<?> typeRef : typeRefs) {
            System.out.println("Found type reference at: " + typeRef.getPosition());
            
            CtTypeReference<?> newTypeRef = typeRef.getFactory().Type().createReference(NEW_TYPE);
            
            // Simple replacement for common cases
            if (typeRef.getParent() instanceof CtVariable) {
                ((CtVariable<?>) typeRef.getParent()).setType(newTypeRef);
                count++;
            } else if (typeRef.getParent() instanceof CtField) {
                ((CtField<?>) typeRef.getParent()).setType(newTypeRef);
                count++;
            } else if (typeRef.getParent() instanceof CtParameter) {
                ((CtParameter<?>) typeRef.getParent()).setType(newTypeRef);
                count++;
            }
        }
        
        // Transform constructor calls
        List<CtConstructorCall<?>> constructorCalls = Query.getElements(root,
            new TypeFilter<CtConstructorCall<?>>(CtConstructorCall.class) {
                @Override
                public boolean matches(CtConstructorCall<?> constructorCall) {
                    CtTypeReference<?> constructedType = constructorCall.getType();
                    return constructedType != null && 
                           OLD_TYPE.equals(constructedType.getQualifiedName());
                }
            });
        
        for (CtConstructorCall<?> constructorCall : constructorCalls) {
            System.out.println("Found constructor call at: " + constructorCall.getPosition());
            
            spoon.reflect.factory.Factory factory = constructorCall.getFactory();
            CtTypeReference<?> newType = factory.Type().createReference(NEW_TYPE);
            CtConstructorCall<?> newConstructorCall = factory.createConstructorCall(newType);
            
            for (CtExpression<?> arg : constructorCall.getArguments()) {
                newConstructorCall.addArgument(arg.clone());
            }
            
            constructorCall.replace(newConstructorCall);
            count++;
        }
        
        return count;
    }
}