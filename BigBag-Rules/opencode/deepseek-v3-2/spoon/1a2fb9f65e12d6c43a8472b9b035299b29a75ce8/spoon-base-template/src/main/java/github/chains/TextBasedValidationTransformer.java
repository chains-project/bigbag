package github.chains;

import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.declaration.CtCompilationUnit;
import spoon.reflect.visitor.filter.TypeFilter;
import java.util.List;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * A simpler text-based transformer for javax.validation to jakarta.validation migration.
 * This approach directly replaces text in source files, which is more reliable
 * for simple namespace migrations.
 */
public class TextBasedValidationTransformer {
    
    public static void main(String[] args) throws IOException {
        if (args.length < 1) {
            System.err.println("Usage: java github.chains.TextBasedValidationTransformer <source-directory>");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        System.out.println("Transforming javax.validation to jakarta.validation in: " + sourceDir);
        
        // First, use Spoon to find all Java files
        Launcher launcher = new Launcher();
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(false);
        launcher.addInputResource(sourceDir);
        
        CtModel model = launcher.buildModel();
        List<CtCompilationUnit> compilationUnits = model.getElements(new TypeFilter<>(CtCompilationUnit.class));
        
        int transformedFiles = 0;
        int totalReplacements = 0;
        
        for (CtCompilationUnit cu : compilationUnits) {
            Path filePath = Paths.get(cu.getFile().getAbsolutePath());
            String content = new String(Files.readAllBytes(filePath));
            
            // Check if file contains javax.validation
            if (content.contains("javax.validation")) {
                // Perform replacements
                String newContent = content
                    .replace("import javax.validation.", "import jakarta.validation.")
                    .replace("javax.validation.Validator", "jakarta.validation.Validator")
                    .replace("javax.validation.constraints.", "jakarta.validation.constraints.")
                    .replace("javax.validation.metadata.", "jakarta.validation.metadata.")
                    .replace("javax.validation.ValidatorFactory", "jakarta.validation.ValidatorFactory");
                
                // Count replacements
                int replacements = countReplacements(content, newContent);
                if (replacements > 0) {
                    Files.write(filePath, newContent.getBytes());
                    transformedFiles++;
                    totalReplacements += replacements;
                    System.out.println("Transformed " + replacements + " occurrences in: " + filePath);
                }
            }
        }
        
        System.out.println("\nTransformation completed!");
        System.out.println("Files transformed: " + transformedFiles);
        System.out.println("Total replacements: " + totalReplacements);
    }
    
    private static int countReplacements(String oldContent, String newContent) {
        // Simple heuristic to count replacements
        int count = 0;
        String[] oldLines = oldContent.split("\n");
        String[] newLines = newContent.split("\n");
        
        for (int i = 0; i < Math.min(oldLines.length, newLines.length); i++) {
            if (!oldLines[i].equals(newLines[i]) && 
                (oldLines[i].contains("javax.validation") || newLines[i].contains("jakarta.validation"))) {
                count++;
            }
        }
        return count;
    }
}