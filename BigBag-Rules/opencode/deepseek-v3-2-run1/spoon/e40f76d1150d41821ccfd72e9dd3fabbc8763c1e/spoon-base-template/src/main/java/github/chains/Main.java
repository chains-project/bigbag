package github.chains;

/**
 * Main entry point for the generic API migration transformation.
 * 
 * This transformation fixes breaking changes when a dependency updates its API.
 * 
 * Current configuration fixes:
 * - com.gargoylesoftware.htmlunit.ScriptResult 
 * - to org.jenkinsci.test.acceptance.plugins.scriptler.ScriptResult
 * - Constructor change: ScriptResult(Object) -> ScriptResult(String)
 * 
 * To adapt for other breaking changes:
 * 1. Update constants in FinalGenericTransformation.java
 * 2. Adjust transformation logic if needed
 * 3. Recompile and run
 */
public class Main {
    public static void main(String[] args) {
        // Delegate to the final, generic transformation
        FinalGenericTransformation.main(args);
    }
}