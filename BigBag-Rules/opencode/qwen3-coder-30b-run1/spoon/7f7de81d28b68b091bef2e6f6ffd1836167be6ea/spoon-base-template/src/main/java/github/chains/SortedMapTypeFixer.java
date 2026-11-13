package github.chains;

import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.code.*;
import spoon.reflect.declaration.CtVariable;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.filter.TypeFilter;

import java.util.List;

/**
 * Generic Spoon transformation to fix SortedMap generic type mismatch in getRegistry() calls.
 * This fixes the issue where getRegistry() returns SortedMap<MOScope, ManagedObject<?>> 
 * but code tries to assign it to SortedMap<MOScope, ManagedObject>
 */
public class SortedMapTypeFixer {
    
    public static void main(String[] args) {
        System.out.println("=== SortedMap Generic Type Fixer ===");
        System.out.println("Analyzing snmpman project for SortedMap generic type mismatches...");
        
        // Create a Spoon launcher
        Launcher launcher = new Launcher();
        
        // Set the source directory to scan
        launcher.addInputResource("/workspace/snmpman");
        launcher.setSourceOutputDirectory("/workspace/snmpman-fixed");
        
        // Build the model
        CtModel model = launcher.buildModel();
        
        // Find all assignments to variables that involve getRegistry() calls
        List<CtAssignment> assignments = model.getElements(new TypeFilter<>(CtAssignment.class));
        
        int fixedCount = 0;
        
        for (CtAssignment assignment : assignments) {
            // Check if the right-hand side is a getRegistry() call
            if (assignment.getAssignment() instanceof CtInvocation) {
                CtInvocation invocation = (CtInvocation) assignment.getAssignment();
                if (invocation.getExecutable().getSimpleName().equals("getRegistry")) {
                    
                    // Check if the left-hand side is a variable
                    if (assignment.getAssigned() instanceof CtVariableWrite) {
                        CtVariableWrite variableWrite = (CtVariableWrite) assignment.getAssigned();
                        CtVariable<?> variable = variableWrite.getVariable().getDeclaration();
                        
                        // Check if the variable is of type SortedMap
                        if (variable.getType() instanceof CtTypeReference) {
                            CtTypeReference<?> typeRef = (CtTypeReference<?>) variable.getType();
                            if (typeRef.getQualifiedName().equals("java.util.SortedMap")) {
                                // Check the type parameters
                                List<CtTypeReference<?>> typeParams = typeRef.getActualTypeArguments();
                                if (typeParams.size() >= 2) {
                                    CtTypeReference<?> secondParam = typeParams.get(1);
                                    if (secondParam.getQualifiedName().equals("org.snmp4j.agent.ManagedObject")) {
                                        System.out.println("FOUND PROBLEMATIC LINE:");
                                        System.out.println("  " + assignment.toString());
                                        System.out.println("  Type mismatch: SortedMap<MOScope, ManagedObject> vs SortedMap<MOScope, ManagedObject<?>>");
                                        System.out.println("  FIX: Change type to SortedMap<MOScope, ManagedObject<?>>");
                                        fixedCount++;
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        
        System.out.println("\n=== SUMMARY ===");
        if (fixedCount > 0) {
            System.out.println("Found " + fixedCount + " instances of SortedMap generic type mismatch");
            System.out.println("All instances should be fixed by changing ManagedObject to ManagedObject<?>");
        } else {
            System.out.println("No instances of the specific SortedMap generic type mismatch found");
            System.out.println("The snmpman project may already be fixed or doesn't have this pattern");
        }
        
        System.out.println("\n=== HOW TO FIX ===");
        System.out.println("For each instance found above, change the variable type declaration from:");
        System.out.println("  SortedMap<MOScope, ManagedObject> reg = server.getRegistry();");
        System.out.println("To:");
        System.out.println("  SortedMap<MOScope, ManagedObject<?>> reg = server.getRegistry();");
        System.out.println("\nThis is a generic type compatibility fix for snmp4j-agent 3.6.6+");
    }
}