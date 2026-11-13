package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.stmt.Statement;
import com.github.javaparser.ast.visitor.ModifierVisitor;
import com.github.javaparser.ast.visitor.Visitable;
import com.github.javaparser.printer.configuration.DefaultPrinterConfiguration;
import com.github.javaparser.printer.configuration.PrettyPrinterConfiguration;
import com.github.javaparser.printer.configuration.PrinterConfiguration;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public class Main {
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java Main <source-directory>");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        System.out.println("Applying generic Mockito 3.x to 4.x transformations to: " + sourceDir);
        
        try {
            List<Path> javaFiles = Files.walk(Paths.get(sourceDir))
                .filter(p -> p.toString().endsWith(".java"))
                .collect(Collectors.toList());
            
            int transformedFiles = 0;
            for (Path javaFile : javaFiles) {
                if (transformFileWithPatterns(javaFile)) {
                    transformedFiles++;
                }
            }
            
            System.out.println("Successfully transformed " + transformedFiles + " files");
        } catch (Exception e) {
            System.err.println("Error during transformation: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
    
    private static boolean transformFileWithPatterns(Path javaFile) throws IOException {
        CompilationUnit cu;
        try {
            cu = StaticJavaParser.parse(javaFile);
        } catch (Exception e) {
            System.err.println("Failed to parse " + javaFile + ": " + e.getMessage());
            return false;
        }
        
        if (cu == null) {
            System.err.println("Failed to parse " + javaFile);
            return false;
        }
        
        boolean[] modified = new boolean[]{false};
        
        // Visitor to apply transformations
        cu.accept(new ModifierVisitor<Void>() {
            @Override
            public Node visit(ImportDeclaration importDecl, Void arg) {
                String importName = importDecl.getNameAsString();
                
                // Pattern 1: MockitoJUnitRunner package change
                if (importName.equals("org.mockito.runners.MockitoJUnitRunner")) {
                    importDecl.setName("org.mockito.junit.MockitoJUnitRunner");
                    modified[0] = true;
                    System.out.println("  Updated import: " + importName + " -> org.mockito.junit.MockitoJUnitRunner");
                }
                
                // Pattern 2: MockitoJUnitRunner.Silent deprecated in 4.x
                if (importName.equals("org.mockito.junit.runners.MockitoJUnitRunner.Silent")) {
                    System.out.println("  Warning: " + importName + " is deprecated in Mockito 4.x");
                    System.out.println("    Consider using @ExtendWith(MockitoExtension.class) with @MockitoSettings");
                }
                
                // Pattern 3: verifyStatic() package change (if present)
                if (importName.equals("org.mockito.Mockito.verifyStatic")) {
                    System.out.println("  Note: verifyStatic() requires try-with-resources in Mockito 4.x");
                }
                return super.visit(importDecl, arg);
            }
            
            @Override
            public Visitable visit(MethodCallExpr methodCall, Void arg) {
                // Check for static method mocking patterns
                if (methodCall.getNameAsString().equals("when") && methodCall.getScope().isPresent()) {
                    // This might be a static method mocking: when(ClassName.method())
                    Optional<Node> parent = methodCall.getParentNode();
                    boolean isInWhenStatement = false;
                    
                    // Check if this is part of a when().thenReturn() chain
                    while (parent.isPresent()) {
                        if (parent.get() instanceof MethodCallExpr) {
                            MethodCallExpr parentCall = (MethodCallExpr) parent.get();
                            if (parentCall.getNameAsString().equals("thenReturn") || 
                                parentCall.getNameAsString().equals("thenThrow") ||
                                parentCall.getNameAsString().equals("thenAnswer")) {
                                isInWhenStatement = true;
                                break;
                            }
                        }
                        parent = parent.get().getParentNode();
                    }
                    
                    if (isInWhenStatement) {
                        System.out.println("  Warning: Potential static method mocking detected");
                        System.out.println("    Mockito 4.x requires mockStatic() for static method mocking");
                        System.out.println("    Example transformation needed:");
                        System.out.println("    Old: when(ClassName.staticMethod(args)).thenReturn(value)");
                        System.out.println("    New: try (MockedStatic<ClassName> mocked = mockStatic(ClassName.class)) {");
                        System.out.println("      mocked.when(() -> ClassName.staticMethod(args)).thenReturn(value);");
                        System.out.println("      // test code here");
                        System.out.println("    }");
                    }
                }
                
                // Check for verifyStatic() calls
                if (methodCall.getNameAsString().equals("verifyStatic")) {
                    System.out.println("  Warning: verifyStatic() found - requires transformation for Mockito 4.x");
                    System.out.println("    Old: verifyStatic(ClassName.class); ClassName.staticMethod(args);");
                    System.out.println("    New: try (MockedStatic<ClassName> mocked = mockStatic(ClassName.class)) {");
                    System.out.println("      mocked.verify(() -> ClassName.staticMethod(args));");
                    System.out.println("    }");
                }
                
                return super.visit(methodCall, arg);
            }
        }, null);
        
        // Check if file contains static method mocking patterns
        boolean hasStaticMocking = cu.toString().contains("when(") && 
                                   cu.toString().contains(".") &&
                                   !cu.toString().contains("mockStatic");
        
        if (hasStaticMocking) {
            // Check if MockedStatic import is already present
            boolean hasMockedStaticImport = cu.getImports().stream()
                .anyMatch(imp -> imp.getNameAsString().equals("org.mockito.MockedStatic"));
            
            if (!hasMockedStaticImport) {
                // Add MockedStatic import
                cu.addImport("org.mockito.MockedStatic");
                modified[0] = true;
                System.out.println("  Added MockedStatic import for static method mocking");
            }
        }
        
        if (modified[0]) {
            PrinterConfiguration config = new DefaultPrinterConfiguration();
            String transformedCode = cu.toString(config);
            
            try {
                Files.writeString(javaFile, transformedCode);
                System.out.println("  Updated file: " + javaFile);
                return true;
            } catch (Exception e) {
                System.err.println("  Failed to write " + javaFile + ": " + e.getMessage());
                return false;
            }
        }
        
        return false;
    }
    
    private static void printTransformationRules() {
        System.out.println("\n=== Generic Mockito 3.x to 4.x Transformation Rules ===");
        System.out.println("Rule 1: Import package change");
        System.out.println("  Old: import org.mockito.runners.MockitoJUnitRunner;");
        System.out.println("  New: import org.mockito.junit.MockitoJUnitRunner;");
        System.out.println();
        System.out.println("Rule 2: Static method mocking");
        System.out.println("  Old: when(ClassName.staticMethod(args)).thenReturn(value);");
        System.out.println("  New: try (MockedStatic<ClassName> mocked = mockStatic(ClassName.class)) {");
        System.out.println("    mocked.when(() -> ClassName.staticMethod(args)).thenReturn(value);");
        System.out.println("    // Test code using mocked static method");
        System.out.println("  }");
        System.out.println();
        System.out.println("Rule 3: Verification of static methods");
        System.out.println("  Old: verifyStatic(ClassName.class);");
        System.out.println("       ClassName.staticMethod(args);");
        System.out.println("  New: try (MockedStatic<ClassName> mocked = mockStatic(ClassName.class)) {");
        System.out.println("    mocked.verify(() -> ClassName.staticMethod(args));");
        System.out.println("  }");
        System.out.println("\nNote: Static method transformation requires manual code restructuring");
    }
}