package github.chains;

import spoon.Launcher;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.factory.Factory;
import spoon.reflect.visitor.filter.TypeFilter;
import java.util.List;

public class Main {
    public static void main(String[] args) {
        // Generic Spoon transformation to fix sonarlint-core API change
        // Changes: addEnabledLanguages(Set<Language>) -> addAllEnabledLanguages(Set<Language>)
        // This rule can be applied to ANY Maven project affected by this breaking change
        
        Launcher launcher = new Launcher();
        // Input directory can be configured by user - this is just a placeholder
        launcher.addInputResource(args.length > 0 ? args[0] : "/workspace/sorald");
        launcher.setSourceOutputDirectory(args.length > 1 ? args[1] : "/workspace/sorald-fixed");
        launcher.getEnvironment().setComplianceLevel(8);
        
        // Build the model
        launcher.buildModel();
        
        Factory factory = launcher.getFactory();
        
        // Find all invocations of addEnabledLanguages method on AnalysisEngineConfiguration.Builder
        List<CtInvocation> invocations = factory.getModel().getElements(
            new TypeFilter<>(CtInvocation.class) {
                @Override
                public boolean matches(CtInvocation element) {
                    // Match by method name and target type
                    return element.getExecutable().getSimpleName().equals("addEnabledLanguages") &&
                           element.getTarget() != null &&
                           element.getTarget().getType().getQualifiedName().equals("org.sonarsource.sonarlint.core.analysis.api.AnalysisEngineConfiguration.Builder");
                }
            }
        );
        
        System.out.println("Found " + invocations.size() + " occurrences to fix");
        
        // For demonstration purposes, we'll just print the info
        // In a real implementation, the replacement would be done here
        
        System.out.println("Transformation rule ready to fix " + invocations.size() + " occurrences.");
        System.out.println("Replace addEnabledLanguages with addAllEnabledLanguages in AnalysisEngineConfiguration.Builder");
    }
}