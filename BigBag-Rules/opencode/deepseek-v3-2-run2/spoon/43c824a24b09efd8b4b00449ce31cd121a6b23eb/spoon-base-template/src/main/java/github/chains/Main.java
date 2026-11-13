package github.chains;

import spoon.Launcher;
import spoon.processing.AbstractProcessor;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.declaration.CtCompilationUnit;
import spoon.reflect.declaration.CtType;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.filter.TypeFilter;

import java.util.List;

/**
 * GENERIC Spoon transformation rule for JsonBranch -> DataElement migration.
 * 
 * Breaking Change Characterization:
 * - Old API: de.gwdg.metadataqa.api.json.JsonBranch
 * - New API: de.gwdg.metadataqa.api.json.DataElement  
 * - Method transformations:
 *   - getJsonPath() -> getPath()
 *   - getParent() -> getParent() (same)
 *   - getLabel() -> getLabel() (same)
 * 
 * This transformation is applicable to ANY Maven project affected by this
 * breaking dependency update from metadata-qa-api.
 * 
 * Usage: java Main <sourceDirectory> [<outputDirectory>]
 */
public class Main {
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java Main <sourceDirectory> [<outputDirectory>]");
            System.err.println("Example: java Main /path/to/project/src");
            System.exit(1);
        }
        
        String sourceDirectory = args[0];
        String outputDirectory = args.length > 1 ? args[1] : sourceDirectory + "-transformed";
        
        System.out.println("Applying JsonBranch -> DataElement transformation");
        System.out.println("Source: " + sourceDirectory);
        System.out.println("Output: " + outputDirectory);
        
        Launcher launcher = new Launcher();
        launcher.addInputResource(sourceDirectory);
        launcher.setSourceOutputDirectory(outputDirectory);
        launcher.getEnvironment().setNoClasspath(true); // Don't need dependencies for transformation
        launcher.getEnvironment().setAutoImports(true);
        launcher.getEnvironment().setCommentEnabled(true);
        
        // Add our processor to fix JsonBranch -> DataElement migration
        launcher.addProcessor(new JsonBranchToDataElementProcessor());
        
        launcher.run();
        
        System.out.println("Transformation complete!");
    }
    
    static class JsonBranchToDataElementProcessor extends AbstractProcessor<CtType<?>> {
        @Override
        public void process(CtType<?> element) {
            // Get the compilation unit to access imports
            CtCompilationUnit cu = element.getPosition().getCompilationUnit();
            if (cu != null) {
                replaceImports(cu);
            }
            
            // Replace type references from JsonBranch to DataElement
            replaceTypeReferences(element);
            
            // Replace method calls from getJsonPath() to getPath()
            replaceMethodCalls(element);
        }
        
        private void replaceImports(CtCompilationUnit cu) {
            // Process imports in the compilation unit
            cu.getImports().forEach(imp -> {
                if (imp.getReference() != null && 
                    "de.gwdg.metadataqa.api.json.JsonBranch".equals(imp.getReference().toString())) {
                    System.out.println("  Replacing import in: " + cu.getFile().getName());
                    // Create new import for DataElement
                    CtTypeReference dataElementRef = getFactory().Type().createReference("de.gwdg.metadataqa.api.json.DataElement");
                    // The import will be automatically updated when we replace the reference
                }
            });
        }
        
        private void replaceTypeReferences(CtType<?> element) {
            // Find all type references to JsonBranch and replace with DataElement
            List<CtTypeReference> typeRefs = element.getElements(new TypeFilter<CtTypeReference>(CtTypeReference.class) {
                @Override
                public boolean matches(CtTypeReference typeRef) {
                    return typeRef != null && 
                           "de.gwdg.metadataqa.api.json.JsonBranch".equals(typeRef.getQualifiedName());
                }
            });
            
            for (CtTypeReference typeRef : typeRefs) {
                System.out.println("  Replacing type reference in: " + element.getQualifiedName());
                CtTypeReference dataElementRef = getFactory().Type().createReference("de.gwdg.metadataqa.api.json.DataElement");
                typeRef.replace(dataElementRef);
            }
        }
        
        private void replaceMethodCalls(CtType<?> element) {
            // Find all method calls to getJsonPath() and replace with getPath()
            List<CtInvocation<?>> invocations = element.getElements(new TypeFilter<CtInvocation<?>>(CtInvocation.class) {
                @Override
                public boolean matches(CtInvocation<?> invocation) {
                    if (invocation.getExecutable() == null) return false;
                    String methodName = invocation.getExecutable().getSimpleName();
                    return "getJsonPath".equals(methodName);
                }
            });
            
            for (CtInvocation<?> invocation : invocations) {
                System.out.println("  Replacing getJsonPath() call in: " + element.getQualifiedName());
                // Create a new invocation with getPath instead
                try {
                    // Clone the invocation and change the method name
                    CtInvocation<?> newInvocation = invocation.clone();
                    newInvocation.getExecutable().setSimpleName("getPath");
                    invocation.replace(newInvocation);
                } catch (Exception e) {
                    System.err.println("Failed to replace invocation: " + e.getMessage());
                }
            }
        }
    }
}