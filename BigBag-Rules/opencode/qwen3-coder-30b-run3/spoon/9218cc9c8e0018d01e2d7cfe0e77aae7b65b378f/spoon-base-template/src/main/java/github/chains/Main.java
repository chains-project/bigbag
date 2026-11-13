package github.chains;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.regex.Pattern;

public class Main {
    public static void main(String[] args) throws Exception {
        String inputDir = args.length > 0 ? args[0] : "/workspace/wicket-crudifier";
        System.out.println("Processing directory: " + inputDir);
        
        // Process Java files
        Files.walk(Paths.get(inputDir))
            .filter(path -> path.toString().endsWith(".java"))
            .forEach(Main::processJavaFile);
            
        System.out.println("Migration complete!");
    }
    
    private static void processJavaFile(java.nio.file.Path filePath) {
        try {
            String content = new String(Files.readAllBytes(filePath));
            boolean modified = false;
            
            // Fix imports
            content = content.replaceAll("import javax\\.validation\\.", "import jakarta.validation.");
            content = content.replaceAll("import javax\\.validation\\.constraints\\.", "import jakarta.validation.constraints.");
            content = content.replaceAll("import javax\\.validation\\.metadata\\.", "import jakarta.validation.metadata.");
            
            // Fix class references
            content = content.replaceAll("javax\\.validation\\.Validator", "jakarta.validation.Validator");
            content = content.replaceAll("javax\\.validation\\.ValidatorFactory", "jakarta.validation.ValidatorFactory");
            content = content.replaceAll("javax\\.validation\\.BeanDescriptor", "jakarta.validation.BeanDescriptor");
            content = content.replaceAll("javax\\.validation\\.ElementDescriptor", "jakarta.validation.ElementDescriptor");
            content = content.replaceAll("javax\\.validation\\.ConstraintDescriptor", "jakarta.validation.ConstraintDescriptor");
            content = content.replaceAll("javax\\.validation\\.NotNull", "jakarta.validation.constraints.NotNull");
            content = content.replaceAll("javax\\.validation\\.NotEmpty", "jakarta.validation.constraints.NotEmpty");
            content = content.replaceAll("javax\\.validation\\.NotBlank", "jakarta.validation.constraints.NotBlank");
            
            // Fix the specific validator factory issue in ListControlGroups.java
            if (filePath.toString().contains("ListControlGroups.java")) {
                // Replace the validator creation with the new pattern
                content = content.replaceAll(
                    "Validator validator = HibernateValidatorProperty\\.validatorFactory\\.getValidator\\(\\);",
                    "Validator validator = Validation.buildDefaultValidatorFactory().getValidator();"
                );
                
                // Ensure the Validation import is present
                if (!content.contains("import jakarta.validation.Validation;")) {
                    content = content.replaceAll(
                        "(import jakarta\\.validation\\.metadata\\.ElementDescriptor;)",
                        "$1\nimport jakarta.validation.Validation;"
                    );
                }
            }
            
            if (!content.equals(new String(Files.readAllBytes(filePath)))) {
                Files.write(filePath, content.getBytes());
                System.out.println("Modified: " + filePath);
            }
            
        } catch (Exception e) {
            System.err.println("Error processing file " + filePath + ": " + e.getMessage());
        }
    }
}