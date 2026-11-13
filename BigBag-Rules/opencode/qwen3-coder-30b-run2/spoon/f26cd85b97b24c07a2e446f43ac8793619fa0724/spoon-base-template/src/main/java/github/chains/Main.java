package github.chains;

/**
 * Generic Spoon transformation for migrating Jetty connector APIs from versions 8/9 to 11.
 * 
 * This transformation addresses the breaking change where SelectChannelConnector was removed
 * in Jetty 11 and replaced with ServerConnector.
 * 
 * Usage:
 *   1. Copy this transformation to your project
 *   2. Configure Spoon to run against your source code
 *   3. The transformation will automatically convert all SelectChannelConnector usage
 * 
 * See Jetty11Migration.java for the implementation details.
 */
public class Main {
    
    public static void main(String[] args) {
        System.out.println("Jetty 11 Connector Migration Tool");
        System.out.println("===================================");
        System.out.println("This tool fixes breaking changes in Jetty 11 migration:");
        System.out.println("- Replaces SelectChannelConnector with ServerConnector");
        System.out.println("- Updates constructor calls appropriately");
        System.out.println("");
        System.out.println("To use:");
        System.out.println("1. Run with Spoon: spoon -i <source_dir> -o <output_dir>");
        System.out.println("2. Apply transformation rules from Jetty11Migration.java");
        System.out.println("3. Test the compiled result");
    }
}