package github.chains;

import spoon.Launcher;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.declaration.CtClass;
import spoon.reflect.visitor.filter.TypeFilter;
import spoon.support.visitor.SignaturePrinter;

public class Main {
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java -jar spoon-transformation.jar <source-directory>");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        
        Launcher launcher = new Launcher();
        launcher.addInputResource(sourceDir);
        launcher.getEnvironment().setNoClasspath(false);
        launcher.getEnvironment().setAutoImports(true);
        launcher.getEnvironment().setCommentEnabled(true);
        
        // Create the transformation
        launcher.addProcessor(new spoon.processing.AbstractProcessor<CtInvocation<?>>() {
            @Override
            public void process(CtInvocation<?> invocation) {
                // Check if this is a method call on AnalysisEngineConfiguration.Builder
                if (invocation.getTarget() != null) {
                    String targetType = invocation.getTarget().getType().getQualifiedName();
                    String methodName = invocation.getExecutable().getSimpleName();
                    
                    // Check if target is AnalysisEngineConfiguration.Builder and method is addEnabledLanguages
                    if (targetType.equals("org.sonarsource.sonarlint.core.analysis.api.AnalysisEngineConfiguration.Builder") &&
                        methodName.equals("addEnabledLanguages")) {
                        // Remove this method invocation
                        invocation.delete();
                    }
                }
            }
            
            @Override
            public boolean isToBeProcessed(CtInvocation<?> candidate) {
                if (candidate.getTarget() == null) return false;
                
                String targetType = candidate.getTarget().getType().getQualifiedName();
                String methodName = candidate.getExecutable().getSimpleName();
                
                // Match: AnalysisEngineConfiguration.Builder.addEnabledLanguages(...)
                return targetType.equals("org.sonarsource.sonarlint.core.analysis.api.AnalysisEngineConfiguration.Builder") &&
                       methodName.equals("addEnabledLanguages");
            }
        });
        
        try {
            launcher.run();
            System.out.println("Transformation completed successfully.");
        } catch (Exception e) {
            System.err.println("Error during transformation: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
}