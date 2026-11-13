package github.chains;

import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.code.CtLiteral;
import spoon.reflect.visitor.filter.TypeFilter;
import java.io.File;

/**
 * A generic Spoon transformation to fix the Jakarta Servlet API migration issue.
 * 
 * <p>This transformation replaces all occurrences of:
 * <ul>
 *   <li>"javax.servlet.error.exception" → "jakarta.servlet.error.exception"</li>
 *   <li>"javax.servlet.error.exception_type" → "jakarta.servlet.error.exception_type"</li>
 *   <li>"javax.servlet.error.message" → "jakarta.servlet.error.message"</li>
 *   <li>"javax.servlet.error.request_uri" → "jakarta.servlet.error.request_uri"</li>
 *   <li>"javax.servlet.error.status_code" → "jakarta.servlet.error.status_code"</li>
 *   <li>"javax.servlet.error.servlet_name" → "jakarta.servlet.error.servlet_name"</li>
 * </ul>
 * 
 * <p>This is a generic transformation that can be applied to any Java project
 * affected by the Jakarta EE namespace change from javax.* to jakarta.*.
 */
public class Main {
    
    /**
     * Main method to run the transformation.
     * 
     * @param args Command line arguments: 
     *             args[0] = source directory path
     *             args[1] = output directory path (optional, defaults to "spooned")
     */
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java -jar spoon-transformation.jar <source-directory> [output-directory]");
            System.err.println("Example: java -jar spoon-transformation.jar /path/to/project/src");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        String outputDir = args.length > 1 ? args[1] : "spooned";
        
        System.out.println("Starting Jakarta Servlet error attribute transformation...");
        System.out.println("Source directory: " + sourceDir);
        System.out.println("Output directory: " + outputDir);
        
        try {
            // Create Spoon launcher
            Launcher launcher = new Launcher();
            launcher.getEnvironment().setNoClasspath(true);
            launcher.getEnvironment().setAutoImports(true);
            launcher.getEnvironment().setCommentEnabled(true);
            
            // Add input resource
            launcher.addInputResource(sourceDir);
            
            // Build model
            System.out.println("Building model...");
            CtModel model = launcher.buildModel();
            
            // Find all string literals that need transformation
            java.util.List<CtLiteral<String>> literalsToTransform = new java.util.ArrayList<>();
            model.getElements(new TypeFilter<CtLiteral<String>>(CtLiteral.class) {
                @Override
                public boolean matches(CtLiteral<String> literal) {
                    if (literal.getValue() instanceof String) {
                        String value = (String) literal.getValue();
                        // Check if this is a javax.servlet.error.* string
                        if (value.startsWith("javax.servlet.error.")) {
                            return true;
                        }
                    }
                    return false;
                }
            }).forEach(literalsToTransform::add);
            
            int transformedCount = literalsToTransform.size();
            
            // Apply transformation
            literalsToTransform.forEach(literal -> {
                String oldValue = (String) literal.getValue();
                String newValue = oldValue.replace("javax.servlet.error.", "jakarta.servlet.error.");
                
                System.out.println("Transforming: " + oldValue + " → " + newValue);
                
                // Create new literal with transformed value
                CtLiteral<String> newLiteral = literal.getFactory().createLiteral(newValue);
                
                // Replace the old literal with the new one
                literal.replace(newLiteral);
            });
            
            System.out.println("\nFound and transformed " + transformedCount + " occurrences");
            
            if (transformedCount > 0) {
                // Set output directory and generate transformed code
                launcher.setSourceOutputDirectory(new File(outputDir));
                System.out.println("Writing transformed code to: " + outputDir);
                try {
                    launcher.prettyprint();
                    System.out.println("Transformation completed successfully!");
                } catch (Exception e) {
                    System.err.println("Warning: Error during pretty printing: " + e.getMessage());
                    System.err.println("Transformation applied in memory but file writing failed.");
                    System.err.println("You may need to manually write the transformed files.");
                }
                
                // Print summary of changes
                System.out.println("\nSummary of changes applied:");
                System.out.println("- javax.servlet.error.exception → jakarta.servlet.error.exception");
                System.out.println("- javax.servlet.error.exception_type → jakarta.servlet.error.exception_type");
                System.out.println("- javax.servlet.error.message → jakarta.servlet.error.message");
                System.out.println("- javax.servlet.error.request_uri → jakarta.servlet.error.request_uri");
                System.out.println("- javax.servlet.error.status_code → jakarta.servlet.error.status_code");
                System.out.println("- javax.servlet.error.servlet_name → jakarta.servlet.error.servlet_name");
            } else {
                System.out.println("No javax.servlet.error.* strings found to transform.");
            }
            
        } catch (Exception e) {
            System.err.println("Error during transformation: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
    
    
}