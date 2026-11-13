package github.chains;

import spoon.Launcher;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.visitor.filter.TypeFilter;

import java.util.List;

/**
 * Generic transformation rule to fix breaking API changes in sonarlint-core
 * Specifically targeting the removal of addEnabledLanguages() method from AnalysisEngineConfiguration.Builder
 */
public class Main {
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java -cp <classpath> github.chains.Main <source-directory>");
            System.exit(1);
        }
        
        String sourceDirectory = args[0];
        
        // Create a Launcher instance with appropriate settings
        Launcher launcher = new Launcher();
        launcher.addInputResource(sourceDirectory);
        launcher.setSourceOutputDirectory(sourceDirectory + "-transformed");
        launcher.getEnvironment().setAutoImports(true);
        launcher.getEnvironment().setComplianceLevel(17);
        
        // Build the model
        launcher.buildModel();
        
        // Find all invocations of addEnabledLanguages method from AnalysisEngineConfiguration.Builder
        List<CtInvocation<?>> invocations = 
            launcher.getFactory().getModel().getRootPackage().getElements(new TypeFilter<>(CtInvocation.class) {
                @Override
                public boolean matches(CtInvocation<?> element) {
                    return element.getExecutable() != null && 
                           "addEnabledLanguages".equals(element.getExecutable().getSimpleName()) &&
                           element.getExecutable().getDeclaringType() != null &&
                           element.getExecutable().getDeclaringType().getQualifiedName().contains("AnalysisEngineConfiguration$Builder");
                }
            });
        
        // Modify the method calls to use the new method name
        for (CtInvocation<?> invocation : invocations) {
            // Change the method name to addEnabledLanguagesAndDefaultRules
            // This is the typical replacement for the removed method
            if (invocation.getExecutable() != null) {
                invocation.getExecutable().setSimpleName("addEnabledLanguagesAndDefaultRules");
            }
        }
        
        // Process and output the transformed code
        launcher.process();
        System.out.println("Transformation completed successfully. Transformed code is in: " + sourceDirectory + "-transformed");
    }
}
        
        String sourceDirectory = args[0];
        
        // Create a Launcher instance with appropriate settings
        Launcher launcher = new Launcher();
        launcher.addInputResource(sourceDirectory);
        launcher.setSourceOutputDirectory(sourceDirectory + "-transformed");
        launcher.getEnvironment().setAutoImports(true);
        launcher.getEnvironment().setComplianceLevel(17);
        
        // Build the model
        launcher.buildModel();
        
        // Find all invocations of addEnabledLanguages method from AnalysisEngineConfiguration.Builder
        List<CtInvocation<?>> invocations = 
            launcher.getFactory().getModel().getRootPackage().getElements(new TypeFilter<>(CtInvocation.class) {
                @Override
                public boolean matches(CtInvocation<?> element) {
                    return element.getExecutable() != null && 
                           "addEnabledLanguages".equals(element.getExecutable().getSimpleName()) &&
                           element.getExecutable().getDeclaringType() != null &&
                           element.getExecutable().getDeclaringType().getQualifiedName().contains("AnalysisEngineConfiguration$Builder");
                }
            });
        
        // Modify the method calls to use the new method name
        for (CtInvocation<?> invocation : invocations) {
            // Change the method name to addEnabledLanguagesAndDefaultRules
            // This is the typical replacement for the removed method
            if (invocation.getExecutable() != null) {
                invocation.getExecutable().setSimpleName("addEnabledLanguagesAndDefaultRules");
            }
        }
        
        // Process and output the transformed code
        launcher.process();
        System.out.println("Transformation completed successfully. Transformed code is in: " + sourceDirectory + "-transformed");
    }
}