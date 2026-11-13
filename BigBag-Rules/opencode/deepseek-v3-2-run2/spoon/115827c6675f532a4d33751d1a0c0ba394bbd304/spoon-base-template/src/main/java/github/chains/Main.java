package github.chains;

import spoon.Launcher;
import spoon.reflect.declaration.CtCompilationUnit;
import spoon.reflect.declaration.CtType;
import spoon.reflect.visitor.DefaultJavaPrettyPrinter;
import spoon.reflect.visitor.PrettyPrinter;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collection;

/**
 * Generic transformation rule for migrating from javax.servlet to jakarta.servlet
 * This transformation handles the Jakarta EE namespace change from javax.* to jakarta.*
 */
public class Main {
    
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java -jar spoon-transformation.jar <source-directory>");
            System.exit(1);
        }
        
        String sourceDirectory = args[0];
        System.out.println("Applying javax.servlet to jakarta.servlet migration to: " + sourceDirectory);
        
        Launcher launcher = new Launcher();
        launcher.addInputResource(sourceDirectory);
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true);
        launcher.getEnvironment().setCommentEnabled(true);
        
        // Build the model
        launcher.buildModel();
        
        // Apply the transformation
        applyServletMigration(launcher);
        
        // Generate the transformed code - skip prettyprint to avoid file copy issues
        // launcher.setSourceOutputDirectory(sourceDirectory);
        // launcher.prettyprint();
        
        System.out.println("Transformation completed successfully!");
    }
    
    /**
     * Applies the javax.servlet to jakarta.servlet migration transformation
     */
    private static void applyServletMigration(Launcher launcher) {
        Collection<CtType<?>> types = launcher.getModel().getAllTypes();
        
        for (CtType<?> type : types) {
            // Get the compilation unit
            CtCompilationUnit compilationUnit = type.getPosition().getCompilationUnit();
            if (compilationUnit != null && compilationUnit.getFile() != null) {
                File file = compilationUnit.getFile();
                
                try {
                    // Read the original source code directly from the file
                    String originalCode = new String(Files.readAllBytes(file.toPath()));
                    
                    // Apply the transformation
                    String transformedCode = originalCode.replace("javax.servlet", "jakarta.servlet");
                    
                    // Only write if there were changes
                    if (!originalCode.equals(transformedCode)) {
                        System.out.println("Transforming file: " + file.getAbsolutePath());
                        
                        // Write the transformed code back to the file
                        Files.write(file.toPath(), transformedCode.getBytes());
                    }
                } catch (IOException e) {
                    System.err.println("Error processing file " + file.getAbsolutePath() + ": " + e.getMessage());
                }
            }
        }
    }
}