package github.chains;

import spoon.Launcher;
import spoon.reflect.code.CtConstructorCall;
import spoon.reflect.code.CtExpression;
import spoon.reflect.declaration.CtCompilationUnit;
import spoon.reflect.declaration.CtImport;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.filter.TypeFilter;
import spoon.support.SpoonClassNotFoundException;
import java.util.AbstractMap;
import java.util.Map;
import java.util.List;
import java.util.ArrayList;

public class Main {
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java -jar spoon-transformation.jar <source-directory>");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        System.out.println("Applying transformation to: " + sourceDir);
        
        Launcher launcher = new Launcher();
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true);
        launcher.getEnvironment().setIgnoreDuplicateDeclarations(true);
        launcher.addInputResource(sourceDir);
        
        try {
            launcher.buildModel();
            
            // Find all constructor calls to MapEntry
            List<CtConstructorCall<?>> mapEntryCalls = new ArrayList<>();
            launcher.getModel().getRootPackage().filterChildren(new TypeFilter<CtConstructorCall<?>>(CtConstructorCall.class) {
                @Override
                public boolean matches(CtConstructorCall<?> constructorCall) {
                    try {
                        CtTypeReference<?> typeRef = constructorCall.getType();
                        if (typeRef != null) {
                            String qualifiedName = typeRef.getQualifiedName();
                            // Match org.cactoos.map.MapEntry or just MapEntry if imported
                            return qualifiedName.equals("org.cactoos.map.MapEntry") || 
                                   qualifiedName.equals("MapEntry");
                        }
                    } catch (SpoonClassNotFoundException e) {
                        // Type not found on classpath, check by simple name
                        String typeName = constructorCall.getType().getSimpleName();
                        return typeName.equals("MapEntry");
                    }
                    return false;
                }
            }).forEach((CtConstructorCall<?> call) -> mapEntryCalls.add(call));
            
            System.out.println("Found " + mapEntryCalls.size() + " MapEntry constructor calls");
            
            // Transform each MapEntry constructor call to AbstractMap.SimpleEntry
            for (CtConstructorCall<?> mapEntryCall : mapEntryCalls) {
                System.out.println("Transforming MapEntry at: " + mapEntryCall.getPosition());
                
                // Create new constructor call for AbstractMap.SimpleEntry
                CtTypeReference<AbstractMap.SimpleEntry> simpleEntryTypeRef = 
                    launcher.getFactory().createCtTypeReference(AbstractMap.SimpleEntry.class);
                
                // Get arguments and convert to array for varargs
                List<CtExpression<?>> argsList = mapEntryCall.getArguments();
                CtExpression<?>[] argsArray = argsList.toArray(new CtExpression<?>[0]);
                
                // Create new constructor call with same arguments
                CtConstructorCall<AbstractMap.SimpleEntry> newCall = 
                    launcher.getFactory().Code().createConstructorCall(
                        simpleEntryTypeRef,
                        argsArray
                    );
                
                // Replace the old constructor call with the new one
                mapEntryCall.replace(newCall);
                
                // AutoImports should handle imports automatically
                // We'll let Spoon's autoImport feature manage the imports
            }
            
            // Write transformed code back
            launcher.setSourceOutputDirectory(sourceDir);
            launcher.prettyprint();
            
            System.out.println("Transformation completed successfully");
            
        } catch (Exception e) {
            System.err.println("Error during transformation: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
}