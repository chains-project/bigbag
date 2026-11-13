package github.chains;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.ConstructorDeclaration;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.stmt.ExplicitConstructorInvocationStmt;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
import java.io.File;
import java.io.FileInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;

/**
 * A generic JavaParser transformation for fixing breaking API changes.
 * 
 * This tool detects and fixes constructor/method signature changes when
 * a dependency updates its API. It's parameterized to handle specific
 * breaking changes by matching:
 * - Target class name (fully qualified)
 * - Old method/constructor signature (parameter count)
 * - New method/constructor signature (parameter count to fix to)
 * 
 * Example: For geoip2 3.0.0 breaking change:
 * - Target class: com.maxmind.geoip2.record.Location
 * - Old constructor: 8 parameters (including localTime)
 * - New constructor: 7 parameters (localTime removed)
 * - Action: Remove last parameter from super() calls in subclasses
 */
public class Main {
    
    // Configuration: Define the breaking change to fix
    private static final String TARGET_CLASS = "com.maxmind.geoip2.record.Location";
    private static final String TARGET_CLASS_SIMPLE_NAME = "Location";
    private static final int OLD_PARAM_COUNT = 8;
    private static final int NEW_PARAM_COUNT = 7;
    private static final boolean REMOVE_LAST_PARAM = true;
    
    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            printUsage();
            System.exit(1);
        }
        
        String sourceDir = args[0];
        System.out.println("Processing source directory: " + sourceDir);
        System.out.println("Target class: " + TARGET_CLASS);
        System.out.println("Fixing constructor calls from " + OLD_PARAM_COUNT + 
                         " to " + NEW_PARAM_COUNT + " parameters");
        
        List<File> javaFiles = Files.walk(Paths.get(sourceDir))
            .filter(Files::isRegularFile)
            .filter(p -> p.toString().endsWith(".java"))
            .map(Path::toFile)
            .collect(Collectors.toList());
        
        System.out.println("Found " + javaFiles.size() + " Java files");
        
        JavaParser javaParser = new JavaParser();
        int modifiedFiles = 0;
        
        for (File javaFile : javaFiles) {
            try (FileInputStream in = new FileInputStream(javaFile)) {
                CompilationUnit cu = javaParser.parse(in).getResult().orElse(null);
                if (cu == null) {
                    continue;
                }
                
                APIFixer fixer = new APIFixer();
                fixer.visit(cu, null);
                
                if (fixer.isModified()) {
                    Files.write(javaFile.toPath(), cu.toString().getBytes());
                    modifiedFiles++;
                    System.out.println("Modified: " + javaFile.getPath());
                }
            } catch (Exception e) {
                System.err.println("Error processing " + javaFile + ": " + e.getMessage());
            }
        }
        
        System.out.println("Total files modified: " + modifiedFiles);
        System.out.println("\nNote: This transformation can be reused for other breaking changes");
        System.out.println("by modifying the configuration constants at the top of Main.java");
    }
    
    private static void printUsage() {
        System.err.println("Usage: java -jar javaparser.jar <source-directory>");
        System.err.println();
        System.err.println("A generic transformation for fixing breaking API changes.");
        System.err.println();
        System.err.println("Current configuration:");
        System.err.println("  Target class: " + TARGET_CLASS);
        System.err.println("  Old parameter count: " + OLD_PARAM_COUNT);
        System.err.println("  New parameter count: " + NEW_PARAM_COUNT);
        System.err.println("  Action: " + (REMOVE_LAST_PARAM ? "Remove last parameter" : "Unknown"));
        System.err.println();
        System.err.println("To adapt for other breaking changes:");
        System.err.println("  1. Update the configuration constants in Main.java");
        System.err.println("  2. Recompile the transformation");
        System.err.println("  3. Run on your source code");
    }
    
    private static class APIFixer extends VoidVisitorAdapter<Void> {
        private boolean modified = false;
        
        public boolean isModified() {
            return modified;
        }
        
        @Override
        public void visit(ClassOrInterfaceDeclaration cls, Void arg) {
            super.visit(cls, arg);
            
            // Check if this class extends the target class
            if (cls.getExtendedTypes().isEmpty()) {
                return;
            }
            
            boolean extendsTargetClass = cls.getExtendedTypes().stream()
                .anyMatch(ext -> matchesTargetClass(ext));
            
            if (!extendsTargetClass) {
                return;
            }
            
            System.out.println("Found class extending " + TARGET_CLASS_SIMPLE_NAME + 
                             ": " + cls.getNameAsString());
            
            // Fix constructors in this class
            for (ConstructorDeclaration constructor : cls.getConstructors()) {
                constructor.accept(new ConstructorFixer(), null);
            }
            
            // Also check for method calls to the target class
            cls.accept(new MethodCallFixer(), null);
        }
        
        private boolean matchesTargetClass(ClassOrInterfaceType type) {
            String name = type.getNameAsString();
            // Match simple name or fully qualified name
            return name.equals(TARGET_CLASS_SIMPLE_NAME) || 
                   name.equals(TARGET_CLASS) ||
                   name.endsWith("." + TARGET_CLASS_SIMPLE_NAME);
        }
        
        private class ConstructorFixer extends VoidVisitorAdapter<Void> {
            @Override
            public void visit(ExplicitConstructorInvocationStmt stmt, Void arg) {
                if (!stmt.isThis()) {  // It's a super() call
                    NodeList<Expression> args = stmt.getArguments();
                    
                    if (args.size() == OLD_PARAM_COUNT) {
                        System.out.println("  Fixing super() constructor call: " + 
                                         OLD_PARAM_COUNT + " -> " + NEW_PARAM_COUNT + " parameters");
                        
                        if (REMOVE_LAST_PARAM && OLD_PARAM_COUNT > NEW_PARAM_COUNT) {
                            // Remove last parameter(s)
                            for (int i = 0; i < OLD_PARAM_COUNT - NEW_PARAM_COUNT; i++) {
                                args.remove(args.size() - 1);
                            }
                            modified = true;
                        } else {
                            System.out.println("  Warning: No transformation rule defined for this parameter change");
                        }
                    } else if (args.size() != NEW_PARAM_COUNT) {
                        System.out.println("  Note: super() call has " + args.size() + 
                                         " parameters (expected " + NEW_PARAM_COUNT + " for new API)");
                    }
                }
            }
        }
        
        private class MethodCallFixer extends VoidVisitorAdapter<Void> {
            @Override
            public void visit(MethodCallExpr methodCall, Void arg) {
                // This could be extended to fix method signature changes
                // For now, we only handle constructor changes
                super.visit(methodCall, arg);
            }
        }
    }
}