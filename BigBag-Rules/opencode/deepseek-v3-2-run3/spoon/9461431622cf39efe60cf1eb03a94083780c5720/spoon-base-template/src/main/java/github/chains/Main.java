package github.chains;

import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.reference.CtWildcardReference;
import spoon.reflect.visitor.filter.TypeFilter;
import java.io.File;
import java.util.List;

public class Main {
    public static void main(String[] args) {
        if (args.length < 2) {
            System.err.println("Usage: java github.chains.Main <sourceDir> <outputDir>");
            System.err.println("Example: java github.chains.Main /path/to/project /path/to/output");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        String outputDir = args[1];
        
        System.out.println("Creating Spoon transformation for breaking API change");
        System.out.println("Source directory: " + sourceDir);
        System.out.println("Output directory: " + outputDir);
        
        try {
            // Create Spoon launcher
            Launcher launcher = new Launcher();
            launcher.getEnvironment().setAutoImports(true);
            launcher.getEnvironment().setNoClasspath(true);
            launcher.getEnvironment().setComplianceLevel(9);
            
            // Add input source
            launcher.addInputResource(sourceDir);
            
            // Build model
            CtModel model = launcher.buildModel();
            
            // Find all type references to org.snmp4j.agent.ManagedObject
            List<CtTypeReference<?>> managedObjectRefs = model.getElements(new TypeFilter<CtTypeReference<?>>(CtTypeReference.class) {
                @Override
                public boolean matches(CtTypeReference<?> element) {
                    if (element == null || element.getQualifiedName() == null) {
                        return false;
                    }
                    
                    // Check if this is a reference to org.snmp4j.agent.ManagedObject
                    boolean isManagedObject = element.getQualifiedName().equals("org.snmp4j.agent.ManagedObject");
                    
                    // Check if it's a raw type (no type arguments)
                    boolean isRawType = isManagedObject && element.getActualTypeArguments().isEmpty();
                    
                    return isRawType;
                }
            });
            
            System.out.println("Found " + managedObjectRefs.size() + " raw ManagedObject type references");
            
            // Transform each raw ManagedObject reference to ManagedObject<?>
            for (CtTypeReference<?> ref : managedObjectRefs) {
                System.out.println("Transforming raw ManagedObject at position: " + ref.getPosition());
                
                // Create wildcard type argument
                CtWildcardReference wildcard = launcher.getFactory().createWildcardReference();
                wildcard.setBoundingType(launcher.getFactory().Type().createReference(java.lang.Object.class));
                
                // Clear existing type arguments (should be empty anyway) and add wildcard
                ref.getActualTypeArguments().clear();
                ref.addActualTypeArgument(wildcard);
            }
            
            // Output transformed code
            launcher.setSourceOutputDirectory(new File(outputDir));
            launcher.prettyprint();
            
            System.out.println("Transformation completed successfully!");
            System.out.println("Output written to: " + outputDir);
            
        } catch (Exception e) {
            System.err.println("Error during transformation: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
}