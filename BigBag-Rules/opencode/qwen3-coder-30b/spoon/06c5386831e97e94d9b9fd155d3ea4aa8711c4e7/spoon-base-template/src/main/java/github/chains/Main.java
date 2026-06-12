package github.chains;

import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.declaration.CtImport;
import spoon.reflect.visitor.filter.TypeFilter;

import java.util.List;

/**
 * Generic transformation rule to fix ScriptResult API issues in Jenkins acceptance test harness
 * This handles the case where ScriptResult class was moved/removed from com.gargoylesoftware.htmlunit
 */
public class Main {
    public static void main(String[] args) {
        System.out.println("Starting ScriptResult API transformation...");
        
        // Create a Spoon launcher
        Launcher launcher = new Launcher();
        
        // Set the source directory to process
        launcher.addInputResource("/workspace/code-coverage-api-plugin");
        
        // Set the output directory  
        launcher.setSourceOutputDirectory("/workspace/code-coverage-api-plugin");
        
        // Build the model
        CtModel model = launcher.buildModel();
        
        // Find all imports of ScriptResult and remove them
        List<CtImport> imports = model.getElements(new TypeFilter<>(CtImport.class));
        int importCount = 0;
        for (CtImport imp : imports) {
            String importPath = imp.getReference().toString();
            if (importPath.contains("com.gargoylesoftware.htmlunit.ScriptResult")) {
                imp.delete();
                importCount++;
            }
        }
        
        System.out.println("Removed " + importCount + " ScriptResult imports");
        
        System.out.println("ScriptResult API transformation complete");
    }
}