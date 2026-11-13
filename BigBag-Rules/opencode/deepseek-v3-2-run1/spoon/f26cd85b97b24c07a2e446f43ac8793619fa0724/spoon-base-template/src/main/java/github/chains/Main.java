package github.chains;

/**
 * Main entry point for the Jetty 11 migration transformation.
 * This is a wrapper around the main transformation logic in Jetty11MigrationTransformation.
 */
public class Main {
    public static void main(String[] args) {
        // Delegate to the main transformation class
        Jetty11MigrationTransformation.main(args);
    }
}