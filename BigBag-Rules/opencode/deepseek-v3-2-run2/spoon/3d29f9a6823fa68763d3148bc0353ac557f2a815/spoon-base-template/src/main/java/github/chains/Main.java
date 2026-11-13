package github.chains;

import spoon.Launcher;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.visitor.Filter;
import spoon.reflect.visitor.Query;
import spoon.reflect.code.CtStatement;
import spoon.reflect.code.CtExpression;
import java.util.List;

public class Main {
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java github.chains.Main <source-directory>");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        
        Launcher launcher = new Launcher();
        launcher.addInputResource(sourceDir);
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true);
        
        // Build the model
        launcher.buildModel();
        
        // Find all method calls to addEnabledLanguages on AnalysisEngineConfiguration.Builder
        List<CtInvocation<?>> invocations = Query.getElements(
            launcher.getFactory(),
            new Filter<CtInvocation<?>>() {
                @Override
                public boolean matches(CtInvocation<?> invocation) {
                    // Check if method name is addEnabledLanguages
                    if (!invocation.getExecutable().getSimpleName().equals("addEnabledLanguages")) {
                        return false;
                    }
                    
                    // Check if the target expression is a builder() call or a Builder instance
                    CtExpression<?> target = invocation.getTarget();
                    if (target == null) {
                        return false;
                    }
                    
                    // Get the type of the target - might be null
                    if (target.getType() != null) {
                        String targetType = target.getType().toString();
                        
                        // Check if target type is AnalysisEngineConfiguration.Builder
                        // We need to handle both:
                        // 1. AnalysisEngineConfiguration.builder().addEnabledLanguages(...)
                        // 2. Some variable of type Builder .addEnabledLanguages(...)
                        if (targetType.contains("AnalysisEngineConfiguration.Builder") || 
                            targetType.contains("AnalysisEngineConfiguration$Builder")) {
                            return true;
                        }
                    }
                    
                    // Also check if target is a method call to builder()
                    if (target instanceof CtInvocation) {
                        CtInvocation<?> targetInvocation = (CtInvocation<?>) target;
                        if (targetInvocation.getExecutable().getSimpleName().equals("builder")) {
                            if (targetInvocation.getExecutable().getDeclaringType() != null) {
                                String declaringType = targetInvocation.getExecutable().getDeclaringType().toString();
                                if (declaringType.contains("AnalysisEngineConfiguration")) {
                                    return true;
                                }
                            }
                        }
                    }
                    
                    return false;
                }
            }
        );
        
        System.out.println("Found " + invocations.size() + " addEnabledLanguages calls to fix");
        
        // Remove each invocation from fluent chains
        for (CtInvocation<?> invocation : invocations) {
            // In a fluent API chain like builder().addEnabledLanguages(...).setClientPid(...)
            // We need to replace the invocation with its target
            // So builder().addEnabledLanguages(...).setClientPid(...) becomes builder().setClientPid(...)
            CtExpression<?> target = invocation.getTarget();
            if (target != null) {
                invocation.replace(target);
                System.out.println("Removed addEnabledLanguages call at line " + invocation.getPosition().getLine());
            }
        }
        
        // Output transformed code
        launcher.setSourceOutputDirectory(sourceDir + "-transformed");
        launcher.prettyprint();
        
        System.out.println("Transformation complete. Output written to: " + sourceDir + "-transformed");
    }
}