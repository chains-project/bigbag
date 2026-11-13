package github.chains;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.type.ReferenceType;
import com.github.javaparser.ast.type.Type;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Generic transformation rule for handling breaking changes where methods add new checked exceptions
 * to their throws clause. This transformation uses pattern matching rather than symbol resolution,
 * making it more robust when dependencies are not available.
 * 
 * Breaking Change Pattern (Jackson 2.13+):
 * - Old: writeValue(File, Object) throws IOException
 * - New: writeValue(File, Object) throws StreamWriteException, DatabindException, IOException
 * 
 * Transformation Strategy:
 * 1. Find method calls to writeValue() on ObjectWriter objects
 * 2. Check if the enclosing method declares throws IOException (or a superclass)
 * 3. If not, add throws IOException to the method signature
 */
public class Main {
    
    // Configuration parameters - can be changed for different breaking changes
    // For Jackson 2.13+ breaking change:
    // - writeValue() methods now throw StreamWriteException and DatabindException
    // - Both extend IOException, so adding throws IOException fixes compilation
    private static final String TARGET_METHOD_NAME = "writeValue";
    private static final String TARGET_RECEIVER_TYPE = "ObjectWriter";
    private static final String REQUIRED_EXCEPTION = "IOException";
    private static final String REQUIRED_EXCEPTION_FQN = "java.io.IOException";
    
    // Alternative configuration examples:
    // For a different breaking change where method() throws NewException extends ExistingException:
    // private static final String TARGET_METHOD_NAME = "method";
    // private static final String TARGET_RECEIVER_TYPE = "TargetClass";
    // private static final String REQUIRED_EXCEPTION = "ExistingException";
    // private static final String REQUIRED_EXCEPTION_FQN = "com.example.ExistingException";
    
    // Track which files were modified
    private static int modifiedFiles = 0;
    
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java Main <source-directory>");
            System.err.println("Example: java Main /path/to/src/main/java");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        System.out.println("Applying transformation to: " + sourceDir);
        System.out.println("Target: Methods calling " + TARGET_RECEIVER_TYPE + "." + TARGET_METHOD_NAME + "()");
        System.out.println("Will add: throws " + REQUIRED_EXCEPTION + " to enclosing methods if not already declared");
        
        try {
            applyTransformation(new File(sourceDir));
            System.out.println("\nTransformation complete. Modified " + modifiedFiles + " files.");
        } catch (IOException e) {
            System.err.println("Error applying transformation: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
    
    private static void applyTransformation(File sourceDir) throws IOException {
        List<File> javaFiles = Files.walk(sourceDir.toPath())
            .filter(path -> path.toString().endsWith(".java"))
            .map(Path::toFile)
            .collect(Collectors.toList());
        
        System.out.println("Found " + javaFiles.size() + " Java files");
        
        for (File javaFile : javaFiles) {
            boolean modified = processFile(javaFile);
            if (modified) {
                modifiedFiles++;
            }
        }
    }
    
    private static boolean processFile(File javaFile) throws IOException {
        JavaParser parser = new JavaParser();
        CompilationUnit cu = parser.parse(javaFile).getResult().orElse(null);
        if (cu == null) {
            return false;
        }
        
        MethodCallVisitor visitor = new MethodCallVisitor();
        cu.accept(visitor, null);
        
        if (visitor.isModified()) {
            // Write the modified file back
            try (FileWriter writer = new FileWriter(javaFile)) {
                writer.write(cu.toString());
                System.out.println("Modified: " + javaFile);
                return true;
            }
        }
        
        return false;
    }
    
    /**
     * Visitor that finds method calls to writeValue() and adds throws IOException
     * to the enclosing method if not already declared.
     */
    private static class MethodCallVisitor extends VoidVisitorAdapter<Void> {
        private boolean modified = false;
        private Set<MethodDeclaration> methodsToUpdate = new HashSet<>();
        
        @Override
        public void visit(MethodCallExpr n, Void arg) {
            super.visit(n, arg);
            
            // Check if this is a call to writeValue()
            if (!n.getNameAsString().equals(TARGET_METHOD_NAME)) {
                return;
            }
            
            // Try to check if the receiver is an ObjectWriter
            // This is a heuristic - we look for common patterns
            boolean isLikelyObjectWriterCall = false;
            
            if (n.getScope().isPresent()) {
                String scopeString = n.getScope().get().toString();
                
                // Check for common patterns in Jackson usage:
                // 1. writer.writeValue(...)
                // 2. mapper.writer().writeValue(...)
                // 3. objectMapper.writer().writeValue(...)
                // 4. Any expression ending with .writer()
                
                // More specific checks to reduce false positives:
                if (scopeString.endsWith(".writer()")) {
                    // mapper.writer() or objectMapper.writer()
                    isLikelyObjectWriterCall = true;
                } else if (scopeString.equals("writer")) {
                    // Simple writer variable
                    isLikelyObjectWriterCall = true;
                } else if (scopeString.matches("\\w+[Ww]riter")) {
                    // Variable named somethingWriter or somethingwriter
                    isLikelyObjectWriterCall = true;
                }
            }
            
            if (!isLikelyObjectWriterCall) {
                return;
            }
            
            // Find the enclosing method declaration
            MethodDeclaration enclosingMethod = n.findAncestor(MethodDeclaration.class).orElse(null);
            if (enclosingMethod == null) {
                return;
            }
            
            // Check if the method already declares IOException or a superclass
            // OR has a try-catch that handles it
            if (!declaresRequiredException(enclosingMethod) && !hasTryCatchForException(enclosingMethod)) {
                methodsToUpdate.add(enclosingMethod);
                modified = true;
            }
        }
        
        @Override
        public void visit(MethodDeclaration n, Void arg) {
            super.visit(n, arg);
            
            // Update method signatures after all method calls have been visited
            if (methodsToUpdate.contains(n)) {
                ClassOrInterfaceType exceptionType = new ClassOrInterfaceType(null, REQUIRED_EXCEPTION);
                n.addThrownException(exceptionType);
                
                // Also add import if not already present
                CompilationUnit cu = n.findCompilationUnit().orElse(null);
                if (cu != null) {
                    addImportIfNeeded(cu);
                }
            }
        }
        
        private boolean declaresRequiredException(MethodDeclaration method) {
            for (ReferenceType thrownException : method.getThrownExceptions()) {
                Type type = thrownException.getElementType();
                if (type instanceof ClassOrInterfaceType) {
                    ClassOrInterfaceType classType = (ClassOrInterfaceType) type;
                    String name = classType.getNameAsString();
                    
                    // Check for IOException (simple or fully qualified)
                    if (name.equals(REQUIRED_EXCEPTION) || name.equals(REQUIRED_EXCEPTION_FQN)) {
                        return true;
                    }
                    
                    // Check for superclasses like Exception, Throwable
                    if (name.equals("Exception") || name.equals("Throwable")) {
                        return true;
                    }
                }
            }
            return false;
        }
        
        private boolean hasTryCatchForException(MethodDeclaration method) {
            // Check if the method body has a try-catch that catches IOException or superclass
            // This is a simplified check - in a real implementation, you'd want to analyze
            // the catch blocks more thoroughly
            return method.getBody().map(body -> {
                String bodyString = body.toString();
                // Check for catch blocks that catch Exception or IOException
                return bodyString.contains("catch (Exception") || 
                       bodyString.contains("catch (IOException") ||
                       bodyString.contains("catch (java.io.IOException") ||
                       bodyString.contains("catch (Throwable");
            }).orElse(false);
        }
        
        private void addImportIfNeeded(CompilationUnit cu) {
            boolean hasImport = cu.getImports().stream()
                .anyMatch(importDecl -> {
                    String importName = importDecl.getNameAsString();
                    return importName.equals(REQUIRED_EXCEPTION_FQN) || 
                           (importName.equals("java.io.*") && REQUIRED_EXCEPTION_FQN.startsWith("java.io."));
                });
            
            if (!hasImport) {
                cu.addImport(REQUIRED_EXCEPTION_FQN);
            }
        }
        
        public boolean isModified() {
            return modified;
        }
    }
}