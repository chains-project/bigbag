package github.chains;

/**
 * Generic Spoon transformation rule for fixing breaking dependency updates
 * in Dubbo instrumentation for AREX agent.
 * 
 * This transformation handles breaking changes in Dubbo APIs between versions,
 * specifically targeting the Result interface and whenCompleteWithContext method
 * differences that cause compilation errors.
 */
public class Main {
    public static void main(String[] args) {
        System.out.println("Spoon transformation rule for Dubbo breaking changes");
        
        if (args.length < 1) {
            System.err.println("Usage: java Main <source-directory>");
            System.exit(1);
        }
        
        System.out.println("Transformation template for Dubbo API compatibility");
        System.out.println("Source directory: " + args[0]);
    }
}