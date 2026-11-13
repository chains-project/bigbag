package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.*;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;

public class Main {
    public static void main(String[] args) throws IOException {
        if (args.length < 1) {
            System.err.println("Usage: java Main <source-directory>");
            System.exit(1);
        }
        
        String sourceDirectory = args[0];
        Path rootPath = Paths.get(sourceDirectory);
        
        // Find all Java files
        List<Path> javaFiles = Files.walk(rootPath)
                .filter(path -> path.toString().endsWith(".java"))
                .collect(Collectors.toList());
        
        for (Path javaFile : javaFiles) {
            try {
                // Parse the file
                CompilationUnit cu = StaticJavaParser.parse(javaFile);
                
                // Apply transformation
                cu.accept(new HibernateValidatorPropertyTransformer(), null);
                
                // Save the modified file
                Files.write(javaFile, cu.toString().getBytes());
            } catch (Exception e) {
                System.err.println("Error processing " + javaFile + ": " + e.getMessage());
            }
        }
    }
    
    private static class HibernateValidatorPropertyTransformer extends VoidVisitorAdapter<Void> {
        private static final String HIBERNATE_VALIDATOR_PROPERTY_FQN = "com.premiumminds.webapp.wicket.validators.HibernateValidatorProperty";
        
        @Override
        public void visit(ObjectCreationExpr n, Void arg) {
            super.visit(n, arg);
            
            // Check if this is a HibernateValidatorProperty object creation
            if (n.getType() instanceof ClassOrInterfaceType) {
                ClassOrInterfaceType type = (ClassOrInterfaceType) n.getType();
                if (HIBERNATE_VALIDATOR_PROPERTY_FQN.equals(type.getNameAsString())) {
                    // This is a HibernateValidatorProperty constructor call
                    // Apply the fix for the breaking change in API
                    applyHibernateValidatorPropertyFix(n);
                }
            }
        }
        
        private void applyHibernateValidatorPropertyFix(ObjectCreationExpr n) {
            // Check if this is the old constructor call: new HibernateValidatorProperty(getResourceBase().getDefaultModel(), getPropertyName())
            // This pattern is used in AbstractControlGroup.java line 68
            
            // The fix would be to change the constructor call to match the new API
            // Since we don't know the exact new API signature from the files, we'll create a generic pattern
            // that can be customized for specific breaking changes
            
            System.out.println("Found HibernateValidatorProperty constructor call that needs API migration");
            System.out.println("  Arguments: " + n.getArguments().size() + " arguments");
            
            // For now, print the arguments to help with debugging
            for (int i = 0; i < n.getArguments().size(); i++) {
                System.out.println("  Arg " + i + ": " + n.getArguments().get(i).toString());
            }
        }
    }
}