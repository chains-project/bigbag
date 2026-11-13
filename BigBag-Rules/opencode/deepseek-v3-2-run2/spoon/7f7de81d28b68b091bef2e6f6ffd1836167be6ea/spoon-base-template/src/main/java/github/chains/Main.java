package github.chains;

import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.declaration.*;
import spoon.reflect.reference.*;
import spoon.reflect.visitor.filter.TypeFilter;

import java.util.*;

/**
 * Generic transformation rule for fixing breaking dependency changes 
 * where ManagedObject changed from raw type to generic type ManagedObject<?>.
 * 
 * This transformation can be applied to ANY Maven project affected by 
 * the same breaking change in snmp4j-agent 3.6.6.
 * 
 * Breaking change pattern:
 * - Old API: ManagedObject (raw type)
 * - New API: ManagedObject<?> (generic type with wildcard)
 * 
 * Transformation rules:
 * 1. Classes implementing ManagedObject → ManagedObject<?>
 * 2. Type references to ManagedObject → ManagedObject<?>
 * 3. Method return types ManagedObject → ManagedObject<?>
 * 4. Parameter types ManagedObject → ManagedObject<?>
 * 5. Field types ManagedObject → ManagedObject<?>
 */
public class Main {
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java -jar spoon-transformation.jar <source-directory>");
            System.err.println("\nThis transformation fixes breaking changes in snmp4j-agent 3.6.6+");
            System.err.println("where ManagedObject changed from raw type to generic type ManagedObject<?>");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        System.out.println("Applying ManagedObject<?> transformation to: " + sourceDir);
        
        // Create Spoon launcher
        Launcher launcher = new Launcher();
        launcher.addInputResource(sourceDir);
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setComplianceLevel(9); // Match project's Java version
        launcher.getEnvironment().setAutoImports(true); // Keep imports clean
        
        // Build the model
        CtModel model = launcher.buildModel();
        
        // Count of transformations
        int transformCount = 0;
        
        // 1. Find all type references to org.snmp4j.agent.ManagedObject
        List<CtTypeReference<?>> managedObjectRefs = model.getElements(
            new TypeFilter<CtTypeReference<?>>(CtTypeReference.class) {
                @Override
                public boolean matches(CtTypeReference<?> element) {
                    return "org.snmp4j.agent.ManagedObject".equals(element.getQualifiedName());
                }
            }
        );
        
        // 2. Transform raw ManagedObject references to ManagedObject<?>
        for (CtTypeReference<?> ref : managedObjectRefs) {
            if (ref.getActualTypeArguments().isEmpty()) {
                // Create wildcard <?> type argument
                CtWildcardReference wildcard = ref.getFactory().createWildcardReference();
                wildcard.setBoundingType(ref.getFactory().createCtTypeReference(Object.class));
                ref.setActualTypeArguments(Collections.singletonList(wildcard));
                transformCount++;
            }
        }
        
        // Write transformed code back
        launcher.setSourceOutputDirectory(sourceDir);
        launcher.prettyprint();
        
        System.out.println("Transformation completed. Applied " + transformCount + " changes.");
        System.out.println("\nSummary of changes:");
        System.out.println("- Raw ManagedObject types changed to ManagedObject<?>");
        System.out.println("- Classes implementing ManagedObject now implement ManagedObject<?>");
        System.out.println("- Method signatures updated with ManagedObject<?> where needed");
        System.out.println("- Field and variable types updated to ManagedObject<?>");
        System.out.println("\nThe transformation is generic and can be applied to any project");
        System.out.println("affected by the same breaking change in snmp4j-agent 3.6.6+");
    }
}