package github.chains;

import spoon.Launcher;
import spoon.reflect.code.CtLocalVariable;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.filter.TypeFilter;

import java.io.File;
import java.util.List;

public class Main {
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java -jar spoon-transformer.jar <source-directory>");
            System.exit(1);
        }
        
        String sourceDirectory = args[0];
        
        System.out.println("Analyzing source directory: " + sourceDirectory);
        
        // First, let's identify the exact problem
        Launcher launcher = new Launcher();
        launcher.getEnvironment().setComplianceLevel(8);
        launcher.addInputResource(sourceDirectory);
        launcher.buildModel();
        
        // Find all local variables with SortedMap type
        List<CtLocalVariable<?>> variables = launcher.getModel().getElements(new TypeFilter<>(CtLocalVariable.class));
        
        int count = 0;
        for (CtLocalVariable<?> var : variables) {
            CtTypeReference<?> type = var.getType();
            if (type != null && type.getSimpleName().contains("SortedMap")) {
                // Print the problematic type for demonstration
                String typeStr = type.toString();
                if (typeStr.contains("ManagedObject") && !typeStr.contains("?")) {
                    System.out.println("Problematic SortedMap type found in " + var.getPosition().getFile().getName() + 
                                     ":" + var.getPosition().getLine());
                    System.out.println("  Type: " + typeStr);
                    count++;
                }
            }
        }
        
        System.out.println("Found " + count + " problematic SortedMap variable declarations");
        System.out.println("The fix needs to change:");
        System.out.println("  SortedMap<MOScope, ManagedObject> to SortedMap<MOScope, ManagedObject<?>>");
        System.out.println("");
        System.out.println("This is a generic type compatibility issue in snmp4j-agent 3.6.5");
        System.out.println("The API changed from ManagedObject to ManagedObject<?> for type safety");
    }
}