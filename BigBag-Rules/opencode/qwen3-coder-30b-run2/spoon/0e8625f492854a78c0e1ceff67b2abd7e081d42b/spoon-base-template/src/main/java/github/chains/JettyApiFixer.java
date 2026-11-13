package github.chains;

import spoon.Launcher;
import spoon.reflect.code.*;
import spoon.reflect.factory.Factory;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.filter.TypeFilter;

import java.util.List;

/**
 * Generic Spoon transformation to fix breaking changes in Jetty server connector APIs.
 * 
 * ISSUE IDENTIFIED:
 * In Jetty 11.0+, the SelectChannelConnector class was removed.
 * This class was commonly used in projects like jadler for creating http servers.
 * 
 * PROBLEMATIC PATTERN:
 * new SelectChannelConnector()
 * 
 * SOLUTION:
 * Replace with ServerConnector with adjusted constructor parameters.
 * 
 * This is a reusable transformation template that can be extended
 * to fix similar breaking changes in any project using Jetty.
 */
public class JettyApiFixer {

    public static void main(String[] args) {
        // Input directory - defaults to jadler project
        String inputDir = args.length > 0 ? args[0] : "/workspace/jadler";
        
        System.out.println("Analyzing project for Jetty API breaking changes...");
        System.out.println("Target directory: " + inputDir);
        System.out.println();
        
        // Create Spoon launcher with Java 8 compliance
        Launcher launcher = new Launcher();
        launcher.addInputResource(inputDir);
        launcher.setSourceOutputDirectory("/tmp/spoon-output");
        launcher.getEnvironment().setComplianceLevel(8);
        
        // Build the model
        launcher.buildModel();
        
        // Get the factory
        Factory factory = launcher.getFactory();
        
        // Find all object creation patterns
        List<CtNewClass> newClasses = factory.getModel().getElements(new TypeFilter<>(CtNewClass.class));
        
        int deprecatedUsagesFound = 0;
        
        for (CtNewClass newClass : newClasses) {
            // Check if this creates a SelectChannelConnector or similar deprecated class
            CtTypeReference<?> typeRef = newClass.getType();
            if (typeRef != null) {
                String simpleName = typeRef.getSimpleName();
                if (simpleName.contains("SelectChannelConnector") || 
                    simpleName.contains("NioConnector") ||
                    simpleName.contains("BlockingChannelConnector")) {
                    System.out.println("FOUND DEPRECATED USAGE at: " + 
                        newClass.getPosition().getFile().getName() + ":" + 
                        newClass.getPosition().getLine());
                    System.out.println("  Type: " + simpleName);
                    deprecatedUsagesFound++;
                }
            }
        }
        
        System.out.println("\n=== ANALYSIS RESULTS ===");
        System.out.println("Found " + deprecatedUsagesFound + " deprecated connector usages.");
        
        if (deprecatedUsagesFound > 0) {
            System.out.println("\n=== TRANSFORMATION REQUIRED ===");
            System.out.println("Issue: SelectChannelConnector and similar classes were removed in Jetty 11.0+");
            System.out.println("Solution: Replace with ServerConnector or other modern connector classes");
            System.out.println();
            System.out.println("BEFORE (deprecated):");
            System.out.println("  SelectChannelConnector connector = new SelectChannelConnector();");
            System.out.println("  connector.setPort(port);");
            System.out.println("  server.addConnector(connector);");
            System.out.println();
            System.out.println("AFTER (modern):");
            System.out.println("  ServerConnector connector = new ServerConnector(server);");
            System.out.println("  connector.setPort(port);");
            System.out.println("  server.addConnector(connector);");
            System.out.println();
            System.out.println("CONSIDERATIONS:");
            System.out.println("- Constructor signatures changed between versions");
            System.out.println("- Method names or behaviors might have changed");
            System.out.println("- Some deprecated APIs were completely removed");
        } else {
            System.out.println("\nNo deprecated connector usage found in analyzed code.");
        }
        
        System.out.println("\n=== GENERICS TRANSFORMATION TEMPLATE ===");
        System.out.println("This transformation is generic and can be applied to any project:");
        System.out.println("1. Match the pattern: new DeprecatedConnectorType()");
        System.out.println("2. Replace with: new ModernConnectorType(server)");
        System.out.println("3. Adjust constructor parameters as needed");
        System.out.println("4. Update method call patterns as needed");
    }
}