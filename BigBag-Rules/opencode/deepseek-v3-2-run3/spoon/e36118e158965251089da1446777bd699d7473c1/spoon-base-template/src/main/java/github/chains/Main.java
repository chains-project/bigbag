package github.chains;

import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.code.CtConstructorCall;
import spoon.reflect.declaration.CtCompilationUnit;
import spoon.reflect.declaration.CtImport;
import spoon.reflect.declaration.CtType;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.filter.TypeFilter;
import java.util.ArrayList;
import java.util.List;

/**
 * Generic Spoon transformation to fix breaking change from org.cactoos.map.MapEntry
 * to java.util.AbstractMap.SimpleEntry in Artipie HTTP library v1.0.1.
 */
public class Main {
    
    private static final String OLD_FULLY_QUALIFIED_TYPE = "org.cactoos.map.MapEntry";
    private static final String OLD_SIMPLE_TYPE_NAME = "MapEntry";
    private static final String NEW_FULLY_QUALIFIED_TYPE = "java.util.AbstractMap.SimpleEntry";
    
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java Main <source-directory>");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        System.out.println("Transforming: " + sourceDir);
        System.out.println("Change: " + OLD_FULLY_QUALIFIED_TYPE + " -> " + NEW_FULLY_QUALIFIED_TYPE);
        
        try {
            Launcher launcher = new Launcher();
            launcher.addInputResource(sourceDir);
            launcher.getEnvironment().setAutoImports(true);
            launcher.getEnvironment().setNoClasspath(true);
            
            CtModel model = launcher.buildModel();
            
            int transformedCalls = transformConstructorCalls(model);
            
            System.out.println("Transformed " + transformedCalls + " constructor calls");
            
            launcher.setSourceOutputDirectory(sourceDir);
            launcher.prettyprint();
            
            System.out.println("Success! Note: Imports may need manual adjustment.");
            System.out.println("Replace 'import org.cactoos.map.MapEntry;' with 'import java.util.AbstractMap;'");
            
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            System.exit(1);
        }
    }
    
    private static int transformConstructorCalls(CtModel model) {
        int count = 0;
        
        List<CtConstructorCall<?>> calls = model.getElements(new TypeFilter<>(CtConstructorCall.class));
        
        for (CtConstructorCall<?> call : calls) {
            CtTypeReference<?> constructedType = call.getType();
            if (constructedType == null) continue;
            
            String typeName = constructedType.getQualifiedName();
            String simpleName = constructedType.getSimpleName();
            
            if (typeName.equals(OLD_FULLY_QUALIFIED_TYPE) || simpleName.equals(OLD_SIMPLE_TYPE_NAME)) {
                try {
                    CtTypeReference<?> newTypeRef = call.getFactory().Type().createReference(NEW_FULLY_QUALIFIED_TYPE);
                    CtConstructorCall<?> newCall = call.getFactory().createConstructorCall(newTypeRef);
                    
                    newCall.setArguments(call.getArguments());
                    
                    call.replace(newCall);
                    count++;
                    
                } catch (Exception e) {
                    System.err.println("Failed at " + call.getPosition() + ": " + e.getMessage());
                }
            }
        }
        
        return count;
    }
}