package github.chains;

/**
 * Main entry point for the Dubbo API migration tool.
 * This is a wrapper that calls the actual transformation logic in DubboApiFix.
 */
public class Main {
    public static void main(String[] args) {
        // Delegate to DubboApiFix which contains the complete implementation
        DubboApiFix.main(args);
    }
}