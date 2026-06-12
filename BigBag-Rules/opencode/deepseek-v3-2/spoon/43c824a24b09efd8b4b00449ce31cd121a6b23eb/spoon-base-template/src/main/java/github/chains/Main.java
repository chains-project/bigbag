package github.chains;

import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.visitor.filter.TypeFilter;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.reference.CtExecutableReference;

import java.util.List;

public class Main {
    @SuppressWarnings({"rawtypes", "unchecked"})
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java -jar spoon-transformation.jar <source-directory>");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        System.out.println("Processing source directory: " + sourceDir);
        
        // Create Spoon launcher
        Launcher launcher = new Launcher();
        launcher.getEnvironment().setNoClasspath(false);
        launcher.getEnvironment().setAutoImports(true);
        launcher.addInputResource(sourceDir);
        
        // Build the model
        CtModel model = launcher.buildModel();
        
        System.out.println("Model built successfully. Starting transformations...");
        
        // Apply transformation for JsonBranch to DataElement migration
        int jsonBranchChanges = transformJsonBranchToDataElement(model);
        System.out.println("Transformed " + jsonBranchChanges + " JsonBranch type references to DataElement");
        
        // Apply transformation for getJsonPath() to getPath() migration  
        int methodChanges = transformGetJsonPathToGetPath(model);
        System.out.println("Transformed " + methodChanges + " getJsonPath() calls to getPath()");
        
        // Output the transformed code
        launcher.setSourceOutputDirectory(sourceDir);
        launcher.prettyprint();
        
        System.out.println("Transformation completed successfully!");
        System.out.println("Total changes: " + (jsonBranchChanges + methodChanges));
    }
    
    private static int transformJsonBranchToDataElement(CtModel model) {
        int changes = 0;
        
        // Find all type references to JsonBranch
        List<CtTypeReference<?>> jsonBranchRefs = model.getElements(new TypeFilter<CtTypeReference<?>>(CtTypeReference.class) {
            @Override
            public boolean matches(CtTypeReference<?> typeRef) {
                return typeRef.getQualifiedName() != null && 
                       typeRef.getQualifiedName().equals("de.gwdg.metadataqa.api.json.JsonBranch");
            }
        });
        
        // Replace JsonBranch type references with DataElement
        for (CtTypeReference<?> ref : jsonBranchRefs) {
            CtTypeReference<?> dataElementRef = ref.getFactory().Type().createReference("de.gwdg.metadataqa.api.json.DataElement");
            ref.replace(dataElementRef);
            changes++;
        }
        
        return changes;
    }
    
    @SuppressWarnings({"rawtypes", "unchecked"})
    private static int transformGetJsonPathToGetPath(CtModel model) {
        int changes = 0;
        
        // Find all method invocations of getJsonPath()
        List<CtInvocation<?>> invocations = model.getElements(new TypeFilter<CtInvocation<?>>(CtInvocation.class) {
            @Override
            public boolean matches(CtInvocation<?> invocation) {
                CtExecutableReference<?> execRef = invocation.getExecutable();
                return execRef != null && "getJsonPath".equals(execRef.getSimpleName());
            }
        });
        
        // Replace getJsonPath() calls with getPath()
        for (CtInvocation invocation : invocations) {
            // Get the factory from the invocation
            var factory = invocation.getFactory();
            
            // Create type reference for DataElement
            CtTypeReference<?> dataElementType = factory.Type().createReference("de.gwdg.metadataqa.api.json.DataElement");
            
            // Create type reference for String return type
            CtTypeReference<?> stringType = factory.Type().createReference(String.class);
            
            // Create new executable reference for getPath()
            CtExecutableReference<?> newExecRef = factory.Executable().createReference(
                dataElementType,
                stringType,
                "getPath"
            );
            
            // Replace the executable reference
            invocation.setExecutable(newExecRef);
            changes++;
        }
        
        return changes;
    }
}