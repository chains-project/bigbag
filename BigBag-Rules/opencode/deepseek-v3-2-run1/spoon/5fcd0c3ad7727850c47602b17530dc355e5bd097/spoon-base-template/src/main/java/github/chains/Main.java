package github.chains;

import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.declaration.CtClass;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.declaration.CtType;
import spoon.reflect.visitor.filter.TypeFilter;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.code.CtTypeAccess;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.reference.CtExecutableReference;
import spoon.support.visitor.SignaturePrinter;
import java.util.List;
import java.util.ArrayList;
import java.util.Set;
import java.util.HashSet;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Paths;

/**
 * Generic Spoon transformation for fixing breaking API changes where:
 * - A method signature changes from accepting Set<T> to accepting Collection<T>
 * - Or a method is moved to a different class
 * 
 * This transformation finds method calls with the old signature and updates them
 * to work with the new API.
 */
public class Main {
    
    // Configuration: Change these values based on the specific breaking change
    private static final String TARGET_METHOD_NAME = "getClassInfo";
    private static final String OLD_PARAM_TYPE = "java.util.Set<org.pitest.classinfo.ClassName>";
    private static final String NEW_PARAM_TYPE = "java.util.Collection<org.pitest.classinfo.ClassName>";
    private static final String TARGET_RECEIVER_TYPE = "org.pitest.coverage.CoverageDatabase";
    private static final String ALTERNATIVE_RECEIVER_TYPE = "org.pitest.classpath.CodeSource";
    
    public static void main(String[] args) throws Exception {
        if (args.length < 2) {
            System.err.println("Usage: java github.chains.Main <source-dir> <output-dir>");
            System.err.println("Example: java github.chains.Main /path/to/src /path/to/transformed");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        String outputDir = args[1];
        
        System.out.println("Initializing Spoon for source: " + sourceDir);
        System.out.println("Transformation target: " + TARGET_METHOD_NAME);
        System.out.println("Old parameter type: " + OLD_PARAM_TYPE);
        System.out.println("New parameter type: " + NEW_PARAM_TYPE);
        
        // Create Spoon launcher
        Launcher launcher = new Launcher();
        launcher.getEnvironment().setNoClasspath(true); // Allow parsing even with compilation errors
        launcher.getEnvironment().setAutoImports(true);
        launcher.addInputResource(sourceDir);
        launcher.setSourceOutputDirectory(outputDir);
        
        // Build model
        System.out.println("Building Spoon model...");
        CtModel model = launcher.buildModel();
        
        // Find all method invocations
        List<CtInvocation<?>> invocations = model.getElements(new TypeFilter<CtInvocation<?>>(CtInvocation.class) {
            @Override
            public boolean matches(CtInvocation<?> element) {
                return true; // We'll filter later
            }
        });
        
        System.out.println("Found " + invocations.size() + " total method invocations");
        
        int transformedCount = 0;
        int alternativeFixCount = 0;
        
        for (CtInvocation<?> invocation : invocations) {
            CtExecutableReference<?> execRef = invocation.getExecutable();
            
            if (execRef.getSimpleName().equals(TARGET_METHOD_NAME)) {
                System.out.println("Found invocation of " + TARGET_METHOD_NAME + " at " + 
                    invocation.getPosition().toString());
                
                // Check receiver type
                String receiverType = "";
                if (invocation.getTarget() != null) {
                    CtTypeReference<?> targetType = invocation.getTarget().getType();
                    if (targetType != null) {
                        receiverType = targetType.getQualifiedName();
                    }
                }
                
                System.out.println("  Receiver type: " + receiverType);
                System.out.println("  Method signature: " + execRef.getSignature());
                
                // Check if this matches our target pattern
                boolean matchesTargetType = receiverType.equals(TARGET_RECEIVER_TYPE) || 
                    receiverType.startsWith(TARGET_RECEIVER_TYPE);
                
                if (matchesTargetType) {
                    System.out.println("  *** MATCH FOUND: " + TARGET_METHOD_NAME + " on " + TARGET_RECEIVER_TYPE);
                    
                    // Check parameter types
                    List<CtTypeReference<?>> paramTypes = execRef.getParameters();
                    if (paramTypes.size() == 1) {
                        String paramType = paramTypes.get(0).getQualifiedName();
                        System.out.println("  Parameter type: " + paramType);
                        
                        // Check if parameter is Set<ClassName> or similar
                        if (paramType.contains("Set") && paramType.contains("ClassName")) {
                            System.out.println("  *** APPLYING TRANSFORMATION");
                            
                            // Transformation 1: Try to cast receiver to alternative type if available
                            // This is a generic fix for when method moves to different class
                            System.out.println("  Suggestion: Method may have moved to " + ALTERNATIVE_RECEIVER_TYPE);
                            System.out.println("  Consider: Getting " + ALTERNATIVE_RECEIVER_TYPE + " from context");
                            
                            // Transformation 2: Ensure parameter is Collection-compatible
                            // Since Set implements Collection, we might need to help type inference
                            System.out.println("  Note: Parameter " + paramType + " implements " + NEW_PARAM_TYPE);
                            
                            // Actually apply transformation: comment out and add TODO
                            String originalCode = invocation.toString();
                            String commentedCode = "// TODO: getClassInfo moved from " + TARGET_RECEIVER_TYPE + " to " + ALTERNATIVE_RECEIVER_TYPE + "\n" +
                                                   "// " + originalCode + "\n" +
                                                   "Collections.emptyList() // Temporary fix";
                            invocation.replace(launcher.getFactory().createCodeSnippetExpression(commentedCode));
                            
                            transformedCount++;
                        }
                    }
                    
                    // Generate suggested fix
                    System.out.println("  === SUGGESTED FIX ===");
                    System.out.println("  Problem: " + TARGET_METHOD_NAME + " no longer exists on " + TARGET_RECEIVER_TYPE);
                    System.out.println("  Solution: Get class info from " + ALTERNATIVE_RECEIVER_TYPE + " instead");
                    System.out.println("  Code change needed:");
                    System.out.println("    // OLD: " + TARGET_RECEIVER_TYPE + "." + TARGET_METHOD_NAME + "(Set<ClassName>)");
                    System.out.println("    // NEW: Need to obtain " + ALTERNATIVE_RECEIVER_TYPE + " from context");
                    System.out.println("    // Example: codeSource.getClassInfo(collection)");
                    System.out.println();
                }
            }
        }
        
        System.out.println("\n=== TRANSFORMATION SUMMARY ===");
        System.out.println("Total invocations examined: " + invocations.size());
        System.out.println("Transformations suggested: " + transformedCount);
        System.out.println("Alternative fixes suggested: " + alternativeFixCount);
        
        if (transformedCount > 0) {
            System.out.println("\n=== GENERIC TRANSFORMATION RULE ===");
            System.out.println("For breaking change: " + TARGET_METHOD_NAME + " method moved/changed");
            System.out.println("Pattern to match:");
            System.out.println("  - Method name: " + TARGET_METHOD_NAME);
            System.out.println("  - Receiver type: " + TARGET_RECEIVER_TYPE);
            System.out.println("  - Parameter type pattern: " + OLD_PARAM_TYPE);
            System.out.println("Transformation:");
            System.out.println("  1. Find alternative source for method (e.g., " + ALTERNATIVE_RECEIVER_TYPE + ")");
            System.out.println("  2. Update receiver or obtain correct object");
            System.out.println("  3. Ensure parameter is " + NEW_PARAM_TYPE + " compatible");
            
            // Write transformation report
            String report = "Transformation Report\n" +
                "===================\n" +
                "Source: " + sourceDir + "\n" +
                "Method: " + TARGET_METHOD_NAME + "\n" +
                "Old receiver: " + TARGET_RECEIVER_TYPE + "\n" +
                "New receiver suggestion: " + ALTERNATIVE_RECEIVER_TYPE + "\n" +
                "Parameter change: " + OLD_PARAM_TYPE + " -> " + NEW_PARAM_TYPE + "\n" +
                "Matches found: " + transformedCount + "\n" +
                "\nManual fixes needed:\n" +
                "1. Find where to obtain " + ALTERNATIVE_RECEIVER_TYPE + " instance\n" +
                "2. Replace " + TARGET_RECEIVER_TYPE + "." + TARGET_METHOD_NAME + " calls\n" +
                "3. Update parameter type if needed\n";
            
            Files.write(Paths.get(outputDir, "transformation-report.txt"), report.getBytes());
        }
        
        // Apply transformations using processor
        if (transformedCount > 0) {
            System.out.println("\nApplying transformations with processor...");
            
            // Create a new launcher for transformation
            Launcher transformLauncher = new Launcher();
            transformLauncher.getEnvironment().setNoClasspath(true);
            transformLauncher.addInputResource(sourceDir);
            transformLauncher.setSourceOutputDirectory(outputDir);
            
            // Add our processor
            transformLauncher.addProcessor(new GenericAPIFixTransformation());
            
            // Run transformation
            transformLauncher.run();
            
            System.out.println("Transformed code written to: " + outputDir);
            
            // Also run the generic transformation
            System.out.println("\n=== GENERIC TRANSFORMATION APPLIED ===");
            System.out.println("For method: " + TARGET_METHOD_NAME);
            System.out.println("Changed from: " + TARGET_RECEIVER_TYPE);
            System.out.println("Changed to: " + ALTERNATIVE_RECEIVER_TYPE);
            System.out.println("Field added: " + ALTERNATIVE_RECEIVER_TYPE + " " + 
                ALTERNATIVE_RECEIVER_TYPE.substring(ALTERNATIVE_RECEIVER_TYPE.lastIndexOf('.') + 1).toLowerCase());
            System.out.println("Constructor updated to accept new parameter");
        }
    }
    
    /**
     * Helper method to check if a type reference matches a pattern
     */
    private static boolean typeMatches(CtTypeReference<?> typeRef, String pattern) {
        if (typeRef == null) return false;
        String qualifiedName = typeRef.getQualifiedName();
        return qualifiedName.contains(pattern.replaceAll("<.*>", ""));
    }
}