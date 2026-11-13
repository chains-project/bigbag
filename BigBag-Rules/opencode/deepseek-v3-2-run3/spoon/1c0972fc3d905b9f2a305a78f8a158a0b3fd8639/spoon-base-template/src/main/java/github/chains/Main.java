package github.chains;

import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.code.CtConstructorCall;
import spoon.reflect.code.CtExpression;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.declaration.CtCompilationUnit;
import spoon.reflect.declaration.CtImport;
import spoon.reflect.declaration.CtType;
import spoon.reflect.visitor.filter.TypeFilter;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

public class Main {
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java github.chains.Main <source-directory>");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        System.out.println("Applying transformation to: " + sourceDir);
        
        // First try text-based transformation (more reliable)
        performTextTransformation(sourceDir);
        
        // Then try Spoon for more complex cases
        trySpoonTransformation(sourceDir);
    }
    
    private static void performTextTransformation(String sourceDir) {
        System.out.println("Performing text-based transformation...");
        try {
            Path startDir = Paths.get(sourceDir);
            Files.walk(startDir)
                .filter(path -> path.toString().endsWith(".java"))
                .forEach(path -> {
                    try {
                        String content = Files.readString(path);
                        String original = content;
                        boolean changed = false;
                        
                        // Pattern 1: Remove import of Maven31DependencyGraphBuilder
                        String importPattern = "import\\s+org\\.apache\\.maven\\.shared\\.dependency\\.graph\\.internal\\.Maven31DependencyGraphBuilder;";
                        if (content.matches("(?s).*" + importPattern + ".*")) {
                            content = content.replaceAll("(?m)^\\s*" + importPattern + "\\s*$", "");
                            changed = true;
                            System.out.println("  Removed import from: " + path);
                        }
                        
                        // Pattern 2: Fix Optional.ofNullable(...).orElse(new Maven31DependencyGraphBuilder())
                        String optionalPattern = "Optional\\.ofNullable\\(([^)]+)\\)\\.orElse\\(\\s*new\\s+Maven31DependencyGraphBuilder\\(\\)\\s*\\)";
                        if (content.matches("(?s).*" + optionalPattern + ".*")) {
                            content = content.replaceAll(optionalPattern, "$1");
                            changed = true;
                            System.out.println("  Fixed Optional.orElse pattern in: " + path);
                        }
                        
                        // Pattern 3: Replace any other new Maven31DependencyGraphBuilder() with null
                        String constructorPattern = "new\\s+Maven31DependencyGraphBuilder\\s*\\(\\)";
                        if (content.matches("(?s).*" + constructorPattern + ".*")) {
                            content = content.replaceAll(constructorPattern, "null");
                            changed = true;
                            System.out.println("  Replaced constructor call in: " + path);
                        }
                        
                        if (changed) {
                            Files.writeString(path, content);
                        }
                    } catch (IOException e) {
                        System.err.println("Error processing " + path + ": " + e.getMessage());
                    }
                });
            
            System.out.println("Text transformation completed");
        } catch (IOException e) {
            System.err.println("Error in text transformation: " + e.getMessage());
        }
    }
    
    private static void trySpoonTransformation(String sourceDir) {
        System.out.println("\nAttempting Spoon transformation for complex cases...");
        try {
            Launcher launcher = new Launcher();
            launcher.getEnvironment().setAutoImports(true);
            launcher.getEnvironment().setNoClasspath(true);
            launcher.getEnvironment().setLevel("OFF");
            launcher.getEnvironment().setComplianceLevel(11);
            
            launcher.addInputResource(sourceDir);
            
            CtModel model = launcher.buildModel();
            
            // Remove imports
            List<CtImport> imports = model.getElements(new TypeFilter<>(CtImport.class));
            int removedImports = 0;
            for (CtImport imp : imports) {
                if (imp.toString().contains("Maven31DependencyGraphBuilder")) {
                    try {
                        imp.delete();
                        removedImports++;
                    } catch (Exception e) {
                        // Ignore
                    }
                }
            }
            
            // Fix constructor calls
            List<CtConstructorCall<?>> constructorCalls = model.getElements(new TypeFilter<>(CtConstructorCall.class));
            int fixedCalls = 0;
            for (CtConstructorCall<?> call : constructorCalls) {
                if (call.toString().contains("new Maven31DependencyGraphBuilder")) {
                    try {
                        // Replace with null
                        call.replace(launcher.getFactory().createLiteral(null));
                        fixedCalls++;
                    } catch (Exception e) {
                        // Ignore
                    }
                }
            }
            
            System.out.println("Spoon transformation:");
            System.out.println("  Removed " + removedImports + " imports");
            System.out.println("  Fixed " + fixedCalls + " constructor calls");
            
            // Write changes
            launcher.setSourceOutputDirectory(sourceDir);
            launcher.prettyprint();
            
            System.out.println("Spoon transformation completed");
            
        } catch (Exception e) {
            System.err.println("Spoon transformation failed: " + e.getMessage());
            System.err.println("Text transformation should have handled basic cases.");
        }
    }
}