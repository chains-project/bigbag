package github.chains;

import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.declaration.CtImport;
import spoon.reflect.visitor.filter.TypeFilter;

/**
 * Generic Spoon transformation rule to fix ScriptResult usage in acceptance test harness.
 * 
 * This transformation addresses a breaking change where com.gargoylesoftware.htmlunit.ScriptResult
 * is no longer available in newer versions of the dependency.
 * 
 * Old pattern:
 *   import com.gargoylesoftware.htmlunit.ScriptResult;
 *   ...
 *   ScriptResult scriptResult = new ScriptResult(result);
 *   Object value = scriptResult.getJavaScriptResult();
 * 
 * New pattern:
 *   // Remove import
 *   ...
 *   Object value = result;
 */
public class Main {
    public static void main(String[] args) {
        // Check if input directory is provided
        if (args.length < 1) {
            System.err.println("Usage: java -cp spoon-base-template.jar github.chains.Main <source-directory>");
            System.exit(1);
        }
        
        String sourceDirectory = args[0];
        System.out.println("Processing source directory: " + sourceDirectory);
        
        // Create Spoon launcher
        Launcher launcher = new Launcher();
        launcher.addInputResource(sourceDirectory);
        launcher.setSourceOutputDirectory(sourceDirectory + "/processed");
        launcher.getEnvironment().setAutoImports(true);
        
        // Process the code
        CtModel model = launcher.buildModel();
        
        // Find all imports containing ScriptResult and remove them
        model.getElements(new TypeFilter<>(CtImport.class))
                .stream()
                .filter(importStmt -> {
                    String importStr = importStmt.toString();
                    return importStr.contains("ScriptResult");
                })
                .forEach(importStmt -> {
                    System.out.println("Removing import: " + importStmt.toString());
                    importStmt.delete();
                });
        
        // Write the modified code back to the source directory
        launcher.process();
        System.out.println("Transformation completed!");
    }
}