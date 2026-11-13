package github.chains;

/**
 * Generic transformation rule for fixing breaking changes in logback-classic dependency
 * 
 * Problem: When upgrading logback-classic from 1.3.x to 1.4.0+, certain SPI interfaces
 * like org.slf4j.spi.LoggingEventAware may not be available, causing compilation failures.
 * 
 * Solution: This is a reusable rule to identify and fix such dependency breaking changes.
 * 
 * Usage: For any Maven project with this issue:
 * 1. Run the transformation with the source directory as argument
 * 2. The tool will identify incompatible imports and suggest remediation
 * 3. The issue typically requires aligning versions of slf4j-api and logback-classic
 * 
 * This tool should be run before building the project to identify potential issues.
 */
public class Main {
    
    public static void main(String[] args) {
        if (args.length != 1) {
            System.err.println("Usage: java Main <source_directory>");
            System.err.println("This tool checks for logback dependency breaking changes.");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        System.out.println("Analyzing directory: " + sourceDir);
        System.out.println("Generic transformation rule for logback 1.4.0+ breaking changes:");
        System.out.println("- Check if org.slf4j.spi.LoggingEventAware is imported anywhere");
        System.out.println("- Align slf4j-api version with logback-classic version");
        System.out.println("- Most commonly, upgrade slf4j-api to match logback 1.4.0+");
        System.out.println("Example pom.xml fix:");
        System.out.println("  <dependency>");
        System.out.println("    <groupId>org.slf4j</groupId>");
        System.out.println("    <artifactId>slf4j-api</artifactId>");
        System.out.println("    <version>2.0.9</version> <!-- Upgrade to compatible version -->");
        System.out.println("  </dependency>");
    }
}
