package github.chains.core.prompt;

/**
 * Exception thrown when a Spoon transformation fails (compilation or execution error).
 * This allows the repair loop to handle transformation failures gracefully without stopping
 * the entire process.
 */
public class TransformationFailureException extends Exception {
    private final String errorType; // "COMPILATION_ERROR" or "EXECUTION_ERROR"
    private final String errorOutput;
    
    public TransformationFailureException(String errorType, String errorOutput, Throwable cause) {
        super("Spoon transformation failed: " + errorType, cause);
        this.errorType = errorType;
        this.errorOutput = errorOutput;
    }
    
    public TransformationFailureException(String errorType, String errorOutput) {
        super("Spoon transformation failed: " + errorType);
        this.errorType = errorType;
        this.errorOutput = errorOutput;
    }
    
    public String getErrorType() {
        return errorType;
    }
    
    public String getErrorOutput() {
        return errorOutput;
    }
}

