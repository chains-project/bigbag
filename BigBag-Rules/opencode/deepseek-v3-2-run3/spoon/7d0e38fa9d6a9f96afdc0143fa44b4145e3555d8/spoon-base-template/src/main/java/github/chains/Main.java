package github.chains;

import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.code.*;
import spoon.reflect.declaration.CtClass;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.filter.TypeFilter;
import spoon.reflect.visitor.filter.NamedElementFilter;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.ArrayList;

public class Main {
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java Main <source-directory>");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        System.out.println("Transforming source directory: " + sourceDir);
        
        Launcher launcher = new Launcher();
        launcher.addInputResource(sourceDir);
        launcher.getEnvironment().setNoClasspath(false); // Need classpath to resolve types
        launcher.getEnvironment().setAutoImports(true);
        launcher.getEnvironment().setCommentEnabled(true);
        
        CtModel model = launcher.buildModel();
        
        // Apply transformations by looking for specific method call patterns
        // We'll transform based on the actual code patterns we see
        int transformationsApplied = 0;
        
        // Find all method invocations
        List<CtInvocation<?>> invocations = model.getElements(new TypeFilter<>(CtInvocation.class));
        System.out.println("Found " + invocations.size() + " total method invocations");
        
        for (CtInvocation<?> invocation : invocations) {
            try {
                String target = invocation.toString();
                List<CtExpression<?>> invocationArgs = invocation.getArguments();
                
                // Debug: print first few invocations
                if (invocations.indexOf(invocation) < 10) {
                    System.out.println("Invocation " + invocations.indexOf(invocation) + ": " + target);
                    System.out.println("  Args: " + invocationArgs.size());
                }
                
                // Pattern 1: .create(projectId, zone) -> .create(projectId, "global", zone)
                if (target.contains(".create(") && !target.contains("\"global\"")) {
                    if (invocationArgs.size() == 2) {
                        // Check if second argument might be a ManagedZone
                        String secondArg = invocationArgs.get(1).toString();
                        if (secondArg.contains("ManagedZone") || secondArg.contains("zone")) {
                            // Insert "global" as second argument
                            CtExpression<?> globalLiteral = invocation.getFactory().createLiteral("global");
                            invocationArgs.add(1, globalLiteral);
                            invocation.setArguments(invocationArgs);
                            transformationsApplied++;
                            System.out.println("Transformed create() call at line " + invocation.getPosition().getLine());
                        }
                    } else if (invocationArgs.size() == 3) {
                        // Could be Changes.create(projectId, zoneName, changeRequest)
                        // Need to insert "global" as second argument
                        CtExpression<?> globalLiteral = invocation.getFactory().createLiteral("global");
                        invocationArgs.add(1, globalLiteral);
                        invocation.setArguments(invocationArgs);
                        transformationsApplied++;
                        System.out.println("Transformed create() call (3 args) at line " + invocation.getPosition().getLine());
                    }
                }
                
                // Pattern 2: .get(projectId, zoneName) -> .get(projectId, "global", zoneName)
                // Also .delete(projectId, zoneName) -> .delete(projectId, "global", zoneName)
                // Also .list(projectId, zoneName) -> .list(projectId, "global", zoneName)
                else if ((target.contains(".get(") || target.contains(".delete(") || 
                         target.contains(".list(")) && !target.contains("\"global\"")) {
                    if (invocationArgs.size() == 2) {
                        // Insert "global" as second argument
                        CtExpression<?> globalLiteral = invocation.getFactory().createLiteral("global");
                        invocationArgs.add(1, globalLiteral);
                        invocation.setArguments(invocationArgs);
                        transformationsApplied++;
                        System.out.println("Transformed " + invocation.getExecutable().getSimpleName() + 
                                         " call at line " + invocation.getPosition().getLine());
                    } else if (invocationArgs.size() == 1 && target.contains(".list(")) {
                        // ManagedZones.list(projectId) -> ManagedZones.list(projectId, "global")
                        CtExpression<?> globalLiteral = invocation.getFactory().createLiteral("global");
                        invocationArgs.add(globalLiteral);
                        invocation.setArguments(invocationArgs);
                        transformationsApplied++;
                        System.out.println("Transformed list() call (1 arg) at line " + invocation.getPosition().getLine());
                    } else if (invocationArgs.size() == 3 && target.contains(".get(")) {
                        // Changes.get(projectId, zoneName, changeId) -> Changes.get(projectId, "global", zoneName, changeId)
                        CtExpression<?> globalLiteral = invocation.getFactory().createLiteral("global");
                        invocationArgs.add(1, globalLiteral);
                        invocation.setArguments(invocationArgs);
                        transformationsApplied++;
                        System.out.println("Transformed get() call (3 args) at line " + invocation.getPosition().getLine());
                    }
                }
                
                // Pattern 3: .get(projectId) -> .get(projectId, projectId) for Projects.get
                else if (target.contains(".get(") && invocationArgs.size() == 1) {
                    // Check if it's Projects.get by looking at context
                    String parent = invocation.getParent().toString();
                    if (parent.contains("Projects") || parent.contains("projects()")) {
                        // Duplicate first argument
                        CtExpression<?> firstArg = invocationArgs.get(0).clone();
                        invocationArgs.add(firstArg);
                        invocation.setArguments(invocationArgs);
                        transformationsApplied++;
                        System.out.println("Transformed Projects.get() call at line " + invocation.getPosition().getLine());
                    }
                }
                
            } catch (Exception e) {
                System.err.println("Error processing invocation: " + e.getMessage());
            }
        }
        
        System.out.println("Total transformations applied: " + transformationsApplied);
        
        // Write transformed code
        launcher.setSourceOutputDirectory(sourceDir);
        launcher.prettyprint();
    }
}