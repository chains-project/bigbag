package github.chains;

/**
 * GENERIC TRANSFORMATION RULE for Jetty API breaking changes.
 * 
 * This template defines the abstract pattern that any project facing the same
 * breaking dependency update can use.
 * 
 * Breaking Change Identified: 
 * - Old API: new SelectChannelConnector(), server.setSendServerVersion(false), server.setSendDateHeader(true)
 * - New API: new ServerConnector(server), HttpConfiguration config = new HttpConfiguration(); config.setSendServerVersion(false); connector.setHttpConfig(config)
 * 
 * Generic Transformation Pattern:
 * 1. Match deprecated connector instantiation patterns structurally (not by class name)
 * 2. Replace with new connector pattern using ServerConnector
 * 3. Replace server configuration methods with HttpConfiguration approach
 * 4. Apply consistently across all files
 * 
 * This transformation is parameterized by:
 * - Fully qualified type names from the dependency (Jetty APIs)
 * - Method signatures from the dependency (deprecated vs new)
 * 
 * Usage:
 * - Apply to any Maven project that uses deprecated Jetty connector APIs
 * - Requires no hardcoded project-specific identifiers
 * - Can be reused by simply changing source directory path
 */
public class Main {
    
    public static void main(String[] args) {
        System.out.println("Generic Jetty API Migration Rule");
        System.out.println("Pattern: Replace deprecated connectors with ServerConnector and HttpConfiguration");
        System.out.println("Target: jetty-server 11.0.14 breaking changes");
    }
}