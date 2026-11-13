package github.chains;

import spoon.Launcher;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.code.CtExpression;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.declaration.CtVariable;
import spoon.reflect.reference.CtTypeReference;
import java.util.List;

/**
 * Generic transformation rule for sonarlint-core API breaking change:
 * 
 * Breaking change: addEnabledLanguages(Set<Language>) changed to addEnabledLanguages(Language...)
 * 
 * The method was moved from AnalysisEngineConfiguration.Builder to 
 * AbstractGlobalConfiguration.AbstractBuilder (which StandaloneGlobalConfiguration.Builder extends).
 * 
 * This transformation:
 * 1. Converts addEnabledLanguages(Set<Language>) to addEnabledLanguages(Language...) 
 *    by adding .toArray(new Language[0])
 * 2. Changes AnalysisEngineConfiguration type references to StandaloneGlobalConfiguration
 * 3. Changes AnalysisEngineConfiguration.builder() to StandaloneGlobalConfiguration.builder()
 * 
 * Usage: java -jar spoon-transformation.jar <source-directory>
 */
public class Main {
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java -jar spoon-transformation.jar <source-directory>");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        
        Launcher launcher = new Launcher();
        launcher.addInputResource(sourceDir);
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true);
        launcher.getEnvironment().setCommentEnabled(true);
        
        // Transformation 1: Fix addEnabledLanguages signature
        launcher.addProcessor(new spoon.processing.AbstractProcessor<CtInvocation<?>>() {
            @Override
            public void process(CtInvocation<?> invocation) {
                // Check if this is a method call to addEnabledLanguages
                String methodName = invocation.getExecutable().getSimpleName();
                if (!"addEnabledLanguages".equals(methodName)) {
                    return;
                }
                
                // Check if there's exactly one argument (old signature: Set<Language>)
                List<CtExpression<?>> arguments = invocation.getArguments();
                if (arguments.size() != 1) {
                    return;
                }
                
                CtExpression<?> arg = arguments.get(0);
                
                // Transform: convert Set to varargs array
                // Create: arg.toArray(new Language[0])
                try {
                    String transformedSource = arg.toString() + ".toArray(new org.sonarsource.sonarlint.core.commons.Language[0])";
                    CtExpression<?> newArg = getFactory().Code().createCodeSnippetExpression(transformedSource);
                    invocation.getArguments().set(0, newArg);
                    
                    System.out.println("INFO: Transformed addEnabledLanguages call at: " + invocation.getPosition());
                } catch (Exception e) {
                    System.err.println("WARN: Failed to transform addEnabledLanguages call at " + invocation.getPosition() + ": " + e.getMessage());
                }
            }
        });
        
        // Transformation 2: Change AnalysisEngineConfiguration type references to StandaloneGlobalConfiguration
        launcher.addProcessor(new spoon.processing.AbstractProcessor<CtVariable<?>>() {
            @Override
            public void process(CtVariable<?> variable) {
                CtTypeReference<?> typeRef = variable.getType();
                if (typeRef != null && "org.sonarsource.sonarlint.core.analysis.api.AnalysisEngineConfiguration".equals(typeRef.getQualifiedName())) {
                    try {
                        CtTypeReference<?> newType = getFactory().Type().createReference("org.sonarsource.sonarlint.core.client.api.standalone.StandaloneGlobalConfiguration");
                        variable.setType(newType);
                        System.out.println("INFO: Changed variable type from AnalysisEngineConfiguration to StandaloneGlobalConfiguration: " + 
                                          variable.getSimpleName() + " at " + variable.getPosition());
                    } catch (Exception e) {
                        System.err.println("WARN: Failed to change variable type at " + variable.getPosition() + ": " + e.getMessage());
                    }
                }
            }
        });
        
        // Transformation 3: Change method return types
        launcher.addProcessor(new spoon.processing.AbstractProcessor<CtMethod<?>>() {
            @Override
            public void process(CtMethod<?> method) {
                CtTypeReference<?> returnType = method.getType();
                if (returnType != null && "org.sonarsource.sonarlint.core.analysis.api.AnalysisEngineConfiguration".equals(returnType.getQualifiedName())) {
                    try {
                        CtTypeReference<?> newType = getFactory().Type().createReference("org.sonarsource.sonarlint.core.client.api.standalone.StandaloneGlobalConfiguration");
                        method.setType(newType);
                        System.out.println("INFO: Changed method return type from AnalysisEngineConfiguration to StandaloneGlobalConfiguration: " + 
                                          method.getSimpleName() + " at " + method.getPosition());
                    } catch (Exception e) {
                        System.err.println("WARN: Failed to change method return type at " + method.getPosition() + ": " + e.getMessage());
                    }
                }
            }
        });
        
        // Transformation 4: Change AnalysisEngineConfiguration.builder() to StandaloneGlobalConfiguration.builder()
        launcher.addProcessor(new spoon.processing.AbstractProcessor<CtInvocation<?>>() {
            @Override
            public void process(CtInvocation<?> invocation) {
                // Check if this is a call to builder()
                String methodName = invocation.getExecutable().getSimpleName();
                if (!"builder".equals(methodName)) {
                    return;
                }
                
                // Check if the target is AnalysisEngineConfiguration
                CtExpression<?> target = invocation.getTarget();
                if (target == null) {
                    return;
                }
                
                String targetType = target.getType() != null ? target.getType().getQualifiedName() : target.toString();
                if ("org.sonarsource.sonarlint.core.analysis.api.AnalysisEngineConfiguration".equals(targetType) || 
                    targetType.contains("AnalysisEngineConfiguration")) {
                    try {
                        // Create type access for StandaloneGlobalConfiguration
                        spoon.reflect.code.CtTypeAccess<?> typeAccess = getFactory().createTypeAccess(
                            getFactory().Type().createReference("org.sonarsource.sonarlint.core.client.api.standalone.StandaloneGlobalConfiguration")
                        );
                        invocation.setTarget(typeAccess);
                        
                        System.out.println("INFO: Transformed AnalysisEngineConfiguration.builder() to StandaloneGlobalConfiguration.builder() at: " + invocation.getPosition());
                    } catch (Exception e) {
                        System.err.println("WARN: Failed to transform builder call at " + invocation.getPosition() + ": " + e.getMessage());
                    }
                }
            }
        });
        
        try {
            launcher.run();
            
            // Write the transformed code to output directory
            String outputDir = "/tmp/transformed-output";
            launcher.setSourceOutputDirectory(outputDir);
            launcher.prettyprint();
            
            System.out.println("SUCCESS: Transformation completed");
            System.out.println("Output written to: " + outputDir);
            System.out.println("\nNote: This transformation fixes the public API breaking change.");
            System.out.println("If your code uses internal APIs like AnalysisEngine constructor,");
            System.out.println("additional manual changes may be required.");
        } catch (Exception e) {
            System.err.println("ERROR: Transformation failed: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
}