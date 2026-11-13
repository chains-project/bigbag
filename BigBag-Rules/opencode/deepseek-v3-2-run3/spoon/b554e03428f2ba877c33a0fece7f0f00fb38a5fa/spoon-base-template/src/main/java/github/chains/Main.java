package github.chains;

import spoon.Launcher;
import spoon.reflect.code.*;
import spoon.reflect.declaration.*;
import spoon.reflect.reference.*;
import spoon.processing.AbstractProcessor;
import java.util.List;

/**
 * Generic transformation rule for fixing breaking API changes where
 * a class with a no-arg constructor was removed and was used as a 
 * default in Optional.ofNullable(...).orElse(...) pattern.
 * 
 * This is parameterized and can be adapted for different removed classes.
 * 
 * Transformation pattern:
 *   Old: Optional.ofNullable(param).orElse(new RemovedClass())
 *   New: param
 *   
 * Also removes imports and type references to the removed class.
 * 
 * Usage: java github.chains.Main <source-dir> <removed-class-fqn>
 * Example: java github.chains.Main /path/to/src org.apache.maven.shared.dependency.graph.internal.Maven31DependencyGraphBuilder
 */
public class Main {
    private static String removedClassFQN;
    
    public static void main(String[] args) {
        if (args.length < 2) {
            System.err.println("Usage: java github.chains.Main <source-directory> <removed-class-fqn>");
            System.err.println("Example: java github.chains.Main ./src org.apache.maven.shared.dependency.graph.internal.Maven31DependencyGraphBuilder");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        removedClassFQN = args[1];
        String outputDir = args.length > 2 ? args[2] : sourceDir + "-transformed";
        
        System.out.println("Applying transformation for removal of: " + removedClassFQN);
        System.out.println("Source: " + sourceDir);
        System.out.println("Output: " + outputDir);
        
        Launcher launcher = new Launcher();
        launcher.addInputResource(sourceDir);
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true);
        launcher.getEnvironment().setCommentEnabled(true);
        
        // Processor for Optional.ofNullable(...).orElse(new RemovedClass())
        launcher.addProcessor(new AbstractProcessor<CtNewClass<?>>() {
            @Override
            public void process(CtNewClass<?> newClass) {
                try {
                    CtTypeReference<?> typeRef = newClass.getType();
                    if (typeRef != null && removedClassFQN.equals(typeRef.getQualifiedName())) {
                        System.out.println("Found instantiation of removed class at: " + newClass.getPosition());
                        
                        // Check if it's inside an orElse() call
                        CtElement parent = newClass.getParent();
                        if (parent instanceof CtInvocation) {
                            CtInvocation<?> orElseInvocation = (CtInvocation<?>) parent;
                            if ("orElse".equals(orElseInvocation.getExecutable().getSimpleName())) {
                                // Check if target is Optional.ofNullable(...)
                                CtExpression<?> target = orElseInvocation.getTarget();
                                if (target instanceof CtInvocation) {
                                    CtInvocation<?> optionalInvocation = (CtInvocation<?>) target;
                                    if ("ofNullable".equals(optionalInvocation.getExecutable().getSimpleName())) {
                                        // Get the parameter from Optional.ofNullable(...)
                                        List<CtExpression<?>> optionalArgs = optionalInvocation.getArguments();
                                        if (!optionalArgs.isEmpty()) {
                                            CtExpression<?> param = optionalArgs.get(0);
                                            
                                            // Replace the entire Optional.ofNullable(...).orElse(...) expression
                                            CtElement grandParent = orElseInvocation.getParent();
                                            if (grandParent != null) {
                                                // Try to replace with just the parameter
                                                try {
                                                    grandParent.replace(param.clone());
                                                    System.out.println("  Fixed: Replaced Optional.ofNullable(...).orElse(...) with parameter");
                                                } catch (Exception e) {
                                                    System.err.println("  Error replacing: " + e.getMessage());
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                } catch (Exception e) {
                    // Continue processing other elements
                }
            }
        });
        
        // Processor to remove imports
        launcher.addProcessor(new AbstractProcessor<CtImport>() {
            @Override
            public void process(CtImport importDecl) {
                try {
                    CtReference importedRef = importDecl.getReference();
                    if (importedRef instanceof CtTypeReference) {
                        CtTypeReference<?> typeRef = (CtTypeReference<?>) importedRef;
                        if (removedClassFQN.equals(typeRef.getQualifiedName())) {
                            importDecl.delete();
                            System.out.println("Removed import at: " + importDecl.getPosition());
                        }
                    }
                } catch (Exception e) {
                    // Continue
                }
            }
        });
        
        // Processor for other type references
        launcher.addProcessor(new AbstractProcessor<CtTypeReference<?>>() {
            @Override
            public void process(CtTypeReference<?> typeRef) {
                try {
                    if (removedClassFQN.equals(typeRef.getQualifiedName())) {
                        System.out.println("WARNING: Type reference (not in import) at: " + typeRef.getPosition());
                        System.out.println("  This may need manual fixing if not handled by other processors");
                    }
                } catch (Exception e) {
                    // Continue
                }
            }
        });
        
        try {
            launcher.run();
            
            // Write transformed code
            launcher.setSourceOutputDirectory(outputDir);
            launcher.prettyprint();
            
            System.out.println("\n=== TRANSFORMATION COMPLETE ===");
            System.out.println("Applied fixes for: " + removedClassFQN);
            System.out.println("Pattern handled: Optional.ofNullable(param).orElse(new " + 
                removedClassFQN.substring(removedClassFQN.lastIndexOf('.') + 1) + "())");
            System.out.println("Transformation: Replaced with just 'param'");
            System.out.println("\nOutput written to: " + outputDir);
            System.out.println("\nNOTE: This transformation assumes:");
            System.out.println("1. The removed class was used as a default in Optional.orElse()");
            System.out.println("2. No default replacement is available (API breaking change)");
            System.out.println("3. Caller should handle null case or dependency injection provides non-null");
            
        } catch (Exception e) {
            System.err.println("Transformation failed: " + e.getMessage());
            e.printStackTrace();
        }
    }
}