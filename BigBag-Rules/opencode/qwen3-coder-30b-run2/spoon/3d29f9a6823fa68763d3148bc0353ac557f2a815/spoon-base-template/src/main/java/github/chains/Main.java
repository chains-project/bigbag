package github.chains;

import spoon.Launcher;
import spoon.processing.AbstractProcessor;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.factory.Factory;

public class Main {
    public static void main(String[] args) {
        // Process the sorald project to fix sonarlint-core API breaking change
        String sourceFolder = args.length > 0 ? args[0] : "/workspace/sorald/sorald/src/main/java";
        
        Launcher launcher = new Launcher();
        launcher.addInputResource(sourceFolder);
        launcher.setSourceOutputDirectory(sourceFolder); // Write back to source directory
        launcher.getEnvironment().setAutoImports(true);
        launcher.getEnvironment().setComplianceLevel(11);
        launcher.getEnvironment().setShouldCompile(true);
        
        // Process the code with our transformation
        launcher.addProcessor(new SonarLintFixProcessor());
        launcher.run();
        
        System.out.println("Transformation completed.");
    }
    
    public static class SonarLintFixProcessor extends AbstractProcessor<CtInvocation<?>> {
        @Override
        public void process(CtInvocation<?> invocation) {
            // Debug: Print when we find the target method
            if (invocation.getExecutable() != null) {
                String methodName = invocation.getExecutable().getSimpleName();
                if ("addEnabledLanguages".equals(methodName)) {
                    System.out.println("Found invocation: " + invocation.toString());
                    // Change method name from addEnabledLanguages to setEnabledLanguages
                    invocation.getExecutable().setSimpleName("setEnabledLanguages");
                    System.out.println("Changed to: " + invocation.toString());
                }
            }
        }
    }
}