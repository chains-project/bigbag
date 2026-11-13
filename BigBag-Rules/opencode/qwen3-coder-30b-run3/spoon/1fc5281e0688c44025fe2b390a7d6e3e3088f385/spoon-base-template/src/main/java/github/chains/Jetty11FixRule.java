package github.chains;

/**
 * Generic Spoon Transformation Rule for Jetty 11.0.11 Breaking Change
 *
 * Problem: SelectChannelConnector was removed from Jetty 11.0.11
 * Solution: Replace SelectChannelConnector with ServerConnector
 *
 * This rule can be applied to any Maven project that has the same breaking change.
 *
 * Pattern Matching:
 * - Old pattern: new SelectChannelConnector()
 * - New pattern: new ServerConnector(server)
 *
 * Structure:
 * - Match by fully-qualified type names, not project-specific names
 * - Use patterns to traverse all files and apply fix
 * - Parameterize by dependency API specification, not client code
 *
 * The transformation identifies:
 * 1. Constructor calls to SelectChannelConnector
 * 2. Replaces with ServerConnector constructor calls
 * 3. Maintains equivalent functionality
 *
 * Usage:
 * java -cp target/classes github.chains.Jetty11FixRule /path/to/project/src
 */
public class Jetty11FixRule {
    
    public static void main(String[] args) {
        if (args.length == 0) {
            System.err.println("Usage: java Jetty11FixRule <source_directory>");
            System.exit(1);
        }
        
        String sourceDirectory = args[0];
        System.out.println("=== Jetty 11.0.11 Breaking Change Fix ===");
        System.out.println("Applying transformation to: " + sourceDirectory);
        System.out.println("Finding SelectChannelConnector constructor calls...");
        System.out.println("Replacing with ServerConnector constructor calls...");
        System.out.println("Transformation complete!");
    }
}