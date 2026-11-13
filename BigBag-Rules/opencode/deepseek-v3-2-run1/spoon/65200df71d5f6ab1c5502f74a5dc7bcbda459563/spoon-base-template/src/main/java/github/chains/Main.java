package github.chains;

import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.declaration.CtClass;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.declaration.CtType;
import spoon.reflect.visitor.Query;
import spoon.reflect.visitor.filter.TypeFilter;
import java.io.File;
import java.util.List;

public class Main {
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java -jar spoon-transformation.jar <source-directory>");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        System.out.println("Processing source directory: " + sourceDir);
        
        // Create Spoon launcher
        Launcher launcher = new Launcher();
        launcher.getEnvironment().setAutoImports(true);
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setCommentEnabled(true);
        
        // Add input directory
        launcher.addInputResource(sourceDir);
        
        // Build model
        CtModel model = launcher.buildModel();
        
        // Find all method invocations
        List<CtInvocation<?>> invocations = Query.getElements(model.getRootPackage(), new TypeFilter<CtInvocation<?>>(CtInvocation.class) {
            @Override
            public boolean matches(CtInvocation<?> invocation) {
                // Check if this is a call to addEnabledLanguages on AnalysisEngineConfiguration.Builder
                String methodName = invocation.getExecutable().getSimpleName();
                
                if ("addEnabledLanguages".equals(methodName)) {
                    // Check if target type is AnalysisEngineConfiguration.Builder
                    // or if it's part of a chain from AnalysisEngineConfiguration.builder()
                    try {
                        // Get the target expression type
                        if (invocation.getTarget() != null) {
                            // Try to get the type of the target
                            spoon.reflect.reference.CtTypeReference<?> targetTypeRef = invocation.getTarget().getType();
                            if (targetTypeRef != null) {
                                String typeName = targetTypeRef.getQualifiedName();
                                // Check if it's AnalysisEngineConfiguration.Builder or a builder type
                                if (typeName != null && (
                                    typeName.equals("org.sonarsource.sonarlint.core.analysis.api.AnalysisEngineConfiguration.Builder") ||
                                    typeName.contains("AnalysisEngineConfiguration$Builder")
                                )) {
                                    return true;
                                }
                            }
                            
                            // Also check the source code pattern: AnalysisEngineConfiguration.builder()
                            String targetCode = invocation.getTarget().toString();
                            if (targetCode.contains("AnalysisEngineConfiguration.builder()")) {
                                return true;
                            }
                        }
                    } catch (Exception e) {
                        // If we can't determine type, check based on surrounding context
                        // Look for pattern: AnalysisEngineConfiguration.builder().addEnabledLanguages(...)
                        String invocationStr = invocation.toString();
                        if (invocationStr.contains("AnalysisEngineConfiguration.builder().addEnabledLanguages")) {
                            return true;
                        }
                    }
                }
                return false;
            }
        });
        
        System.out.println("Found " + invocations.size() + " occurrences of addEnabledLanguages on AnalysisEngineConfiguration.Builder");
        
        // Remove each invocation
        for (CtInvocation<?> invocation : invocations) {
            System.out.println("Removing invocation at position: " + invocation.getPosition());
            
            // We need to remove this method call from the chain
            // If it's part of a builder chain like: builder().addEnabledLanguages(...).setClientPid(...)
            // We need to remove just the addEnabledLanguages call
            
            // Get the parent invocation or statement
            if (invocation.getParent() instanceof CtInvocation) {
                // This is part of a chain: foo().addEnabledLanguages().bar()
                // We need to replace foo().addEnabledLanguages().bar() with foo().bar()
                CtInvocation<?> parentInvocation = (CtInvocation<?>) invocation.getParent();
                
                // In a fluent API chain, the parent should have this invocation as its target
                // Actually, in Spoon, for a chain like a.b().c(), the structure is:
                // CtInvocation of c() with target = CtInvocation of b() with target = CtReference of a
                // So we need to replace the target of parent with the target of this invocation
                if (parentInvocation.getTarget() == invocation) {
                    // Replace parent's target with invocation's target
                    parentInvocation.setTarget(invocation.getTarget());
                    System.out.println("  Removed from chain");
                }
            } else {
                // This is a standalone call or end of chain
                // Just delete it
                invocation.delete();
                System.out.println("  Deleted standalone call");
            }
        }
        
        // Output the transformed code
        String outputDir = sourceDir + "-transformed";
        launcher.setSourceOutputDirectory(outputDir);
        launcher.prettyprint();
        
        System.out.println("Transformation complete. Output written to: " + outputDir);
        
        // Print summary
        if (invocations.size() > 0) {
            System.out.println("\nSummary: Removed " + invocations.size() + " calls to addEnabledLanguages on AnalysisEngineConfiguration.Builder");
            System.out.println("This fixes the breaking change in sonarlint-core 8.19.0.72745 where");
            System.out.println("AnalysisEngineConfiguration.Builder.addEnabledLanguages(Set<Language>) was removed.");
        } else {
            System.out.println("\nNo occurrences found. The code may already be fixed.");
        }
    }
}