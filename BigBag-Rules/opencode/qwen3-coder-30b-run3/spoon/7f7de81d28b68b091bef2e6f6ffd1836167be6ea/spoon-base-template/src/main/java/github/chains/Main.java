package github.chains;

import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.code.CtAssignment;
import spoon.reflect.code.CtExpression;
import spoon.reflect.code.CtLocalVariable;
import spoon.reflect.declaration.CtField;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.filter.TypeFilter;

import java.util.List;

/**
 * Generic Spoon transformation to fix API breaking changes in snmp4j-agent related to
 * SortedMap<MOScope, ManagedObject> vs SortedMap<MOScope, ManagedObject<?>>
 */
public class Main {
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java -cp <classpath> github.chains.Main <source-directory>");
            System.exit(1);
        }

        String sourceDirectory = args[0];
        
        Launcher launcher = new Launcher();
        launcher.addInputResource(sourceDirectory);
        launcher.setSourceOutputDirectory(sourceDirectory + "-transformed");
        launcher.getEnvironment().setAutoImports(true);
        launcher.getEnvironment().setComplianceLevel(17);

        CtModel model = launcher.buildModel();
        
        // Find all assignments where a variable is assigned a call to getRegistry()
        List<CtAssignment<?, ?>> assignments = model.getElements(new TypeFilter<>(CtAssignment.class));
        
        for (CtAssignment<?, ?> assignment : assignments) {
            // Check the right-hand side for a method call to getRegistry()
            if (assignment.getAssignment() instanceof CtExpression) {
                CtExpression<?> rhs = (CtExpression<?>) assignment.getAssignment();
                if (rhs.toString().contains(".getRegistry()")) {
                    
                    // Check the left-hand side for a variable declaration with SortedMap<..., ManagedObject>
                    if (assignment.getAssigned() instanceof CtLocalVariable) {
                        CtLocalVariable<?> localVariable = (CtLocalVariable<?>) assignment.getAssigned();
                        CtTypeReference<?> variableType = localVariable.getType();
                        
                        // Check if it's a SortedMap with ManagedObject (non-generic form)
                        if (variableType != null && 
                            "java.util.SortedMap".equals(variableType.getQualifiedName()) &&
                            variableType.getActualTypeArguments().size() >= 2) {
                            
                            CtTypeReference<?> secondArg = variableType.getActualTypeArguments().get(1);
                            if (secondArg != null && 
                                "org.snmp4j.agent.ManagedObject".equals(secondArg.getQualifiedName()) &&
                                !secondArg.toString().contains("<")) { // Not the generic form with <?>
                                
                                System.out.println("Found pattern to fix: " + assignment.toString());
                                
                                // Change the type to ManagedObject<?>
                                CtTypeReference<?> newSecondArg = secondArg.clone();
                                newSecondArg.setActualTypeArguments(List.of(
                                    launcher.getFactory().createWildcardReference()
                                ));
                                
                                // Update the type of the SortedMap
                                List<CtTypeReference<?>> newArgs = variableType.getActualTypeArguments();
                                newArgs.set(1, newSecondArg);
                                variableType.setActualTypeArguments(newArgs);
                                
                                System.out.println("Fixed type to: " + variableType.toString());
                            }
                        }
                    }
                }
            }
        }
        
        // Also look for field declarations that might have the same issue
        List<CtField> fields = model.getElements(new TypeFilter<>(CtField.class));
        for (CtField field : fields) {
            CtTypeReference<?> fieldType = field.getType();
            if (fieldType != null && 
                "java.util.SortedMap".equals(fieldType.getQualifiedName()) &&
                fieldType.getActualTypeArguments().size() >= 2) {
                
                CtTypeReference<?> secondArg = fieldType.getActualTypeArguments().get(1);
                if (secondArg != null && 
                    "org.snmp4j.agent.ManagedObject".equals(secondArg.getQualifiedName()) &&
                    !secondArg.toString().contains("<")) {
                    
                    System.out.println("Found field pattern to fix: " + field.getSimpleName() + " " + fieldType.toString());
                    
                    // Change the type to ManagedObject<?>
                    CtTypeReference<?> newSecondArg = secondArg.clone();
                    newSecondArg.setActualTypeArguments(List.of(
                        launcher.getFactory().createWildcardReference()
                    ));
                    
                    // Update the type of the SortedMap
                    List<CtTypeReference<?>> newArgs = fieldType.getActualTypeArguments();
                    newArgs.set(1, newSecondArg);
                    fieldType.setActualTypeArguments(newArgs);
                    
                    System.out.println("Fixed field type to: " + fieldType.toString());
                }
            }
        }
        
        launcher.process();
        System.out.println("Transformation completed.");
    }
}