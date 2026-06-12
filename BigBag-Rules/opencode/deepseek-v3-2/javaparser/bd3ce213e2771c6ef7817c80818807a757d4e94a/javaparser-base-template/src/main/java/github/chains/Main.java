package github.chains;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.visitor.ModifierVisitor;
import com.github.javaparser.ast.visitor.Visitable;
import com.github.javaparser.printer.DefaultPrettyPrinter;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

public class Main {
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java Main <source-directory>");
            System.err.println("Example: java Main /path/to/src");
            System.err.println("\nThis transformation fixes Jackson method calls affected by breaking changes in Jackson 2.13+.");
            System.err.println("Specifically, writeValue() and readValue() methods now throw:");
            System.err.println("  - StreamWriteException / StreamReadException (extends JsonProcessingException)");
            System.err.println("  - DatabindException (extends JsonProcessingException)");
            System.err.println("  - IOException");
            System.err.println("\nSince JsonProcessingException extends IOException, adding 'throws IOException'");
            System.err.println("to the method signature is sufficient to handle all these exceptions.");
            System.err.println("\nNOTE: This transformation assumes jackson-core version is compatible with");
            System.err.println("jackson-databind (both should be 2.13.x or higher). If you see compilation");
            System.err.println("errors about missing StreamWriteException/StreamReadException classes,");
            System.err.println("update your jackson-core dependency to match jackson-databind version.");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        System.out.println("Transforming source directory: " + sourceDir);
        System.out.println("Fixing Jackson method calls affected by Jackson 2.13+ breaking changes");
        System.out.println("========================================================================================");
        
        try {
            JavaParser javaParser = new JavaParser();
            
            // Walk through all Java files
            long fileCount = Files.walk(Paths.get(sourceDir))
                .filter(path -> path.toString().endsWith(".java"))
                .peek(path -> transformFile(path, javaParser))
                .count();
                
            System.out.println("========================================================================================");
            System.out.println("Transformation complete! Processed " + fileCount + " Java files.");
            System.out.println("\nImportant: If you still have compilation errors about missing");
            System.out.println("StreamWriteException or StreamReadException classes, ensure your");
            System.out.println("jackson-core dependency version matches jackson-databind version.");
            System.out.println("Example: both should be 2.13.4 or higher.");
            
        } catch (Exception e) {
            System.err.println("Error during transformation: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    private static void transformFile(Path path, JavaParser javaParser) {
        try {
            CompilationUnit cu = javaParser.parse(path).getResult().orElseThrow();
            AtomicBoolean modified = new AtomicBoolean(false);
            
            // Visitor to find and fix Jackson method calls
            cu.accept(new ModifierVisitor<Void>() {
                @Override
                public Visitable visit(MethodCallExpr n, Void arg) {
                    Visitable result = super.visit(n, arg);
                    
                    // Check if this is a Jackson method that now throws new exceptions
                    String methodName = n.getNameAsString();
                    if ("writeValue".equals(methodName) || "readValue".equals(methodName)) {
                        // Get the surrounding method
                        Optional<MethodDeclaration> enclosingMethod = n.findAncestor(MethodDeclaration.class);
                        if (enclosingMethod.isPresent()) {
                            MethodDeclaration method = enclosingMethod.get();
                            
                            // Check if method declares IOException or a supertype
                            boolean throwsCompatibleException = method.getThrownExceptions().stream()
                                .anyMatch(expr -> {
                                    String typeStr = expr.toString();
                                    // Check for IOException or supertypes
                                    return typeStr.contains("IOException") || 
                                           typeStr.contains("java.io.IOException") ||
                                           typeStr.contains("Exception") ||
                                           typeStr.contains("java.lang.Exception") ||
                                           typeStr.contains("Throwable") ||
                                           typeStr.contains("java.lang.Throwable");
                                });
                                
                            if (!throwsCompatibleException) {
                                System.out.println("• Fixing: " + method.getNameAsString() + "() in " + path.getFileName());
                                System.out.println("  Line: " + n.getRange().map(r -> r.begin.line).orElse(-1));
                                System.out.println("  Adding 'throws IOException' to method signature");
                                
                                // Add throws IOException to the method
                                ClassOrInterfaceType ioExceptionType = new ClassOrInterfaceType();
                                ioExceptionType.setName("IOException");
                                method.addThrownException(ioExceptionType);
                                modified.set(true);
                            }
                        }
                    }
                    return result;
                }
            }, null);
            
            // Write back if modified
            if (modified.get()) {
                DefaultPrettyPrinter printer = new DefaultPrettyPrinter();
                String transformedCode = printer.print(cu);
                Files.write(path, transformedCode.getBytes());
                System.out.println("  ✓ File updated successfully\n");
            }
            
        } catch (FileNotFoundException e) {
            System.err.println("File not found: " + path);
        } catch (IOException e) {
            System.err.println("Error writing file " + path + ": " + e.getMessage());
        } catch (Exception e) {
            System.err.println("Error processing file " + path + ": " + e.getMessage());
        }
    }
}