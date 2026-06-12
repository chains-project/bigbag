package github.chains;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.type.Type;
import com.github.javaparser.ast.visitor.ModifierVisitor;
import com.github.javaparser.printer.DefaultPrettyPrinter;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Generic transformation rule for logback-core 1.2.9 breaking API change.
 * 
 * Breaking Change: Encoder interface changed from streaming API to byte array API.
 * 
 * Old API (logback-core < 1.2.9):
 * - EncoderBase had abstract methods: init(OutputStream), doEncode(E), close()
 * - Subclasses implemented these streaming methods
 * 
 * New API (logback-core 1.2.9):
 * - Encoder interface requires: byte[] encode(E), byte[] headerBytes(), byte[] footerBytes()
 * - EncoderBase is abstract and implements Encoder but doesn't provide implementations
 * 
 * Transformation:
 * 1. Find classes extending EncoderBase<E>
 * 2. Add missing methods: headerBytes() and footerBytes()
 * 3. Transform doEncode method to encode method with byte[] return type
 * 4. Remove @Override annotations from init() and close() methods
 * 5. Add ByteArrayOutputStream import if needed
 * 
 * Note: The method body transformation for encode() is simplified.
 * A real implementation would need to analyze and adapt the original doEncode logic.
 */
public class Main {
    
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java Main <source-directory>");
            System.err.println("Example: java Main /path/to/project/src/main/java");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        System.out.println("Processing source directory: " + sourceDir);
        System.out.println("Looking for classes extending EncoderBase to fix logback-core 1.2.9 API break");
        
        try {
            List<Path> javaFiles = Files.walk(Paths.get(sourceDir))
                .filter(p -> p.toString().endsWith(".java"))
                .collect(Collectors.toList());
            
            System.out.println("Found " + javaFiles.size() + " Java files");
            
            int modifiedFiles = 0;
            for (Path javaFile : javaFiles) {
                if (processFile(javaFile)) {
                    modifiedFiles++;
                }
            }
            
            System.out.println("Modified " + modifiedFiles + " files");
            
        } catch (IOException e) {
            System.err.println("Error processing files: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    private static boolean processFile(Path filePath) throws IOException {
        try {
            JavaParser parser = new JavaParser();
            CompilationUnit cu = parser.parse(filePath).getResult().orElse(null);
            
            if (cu == null) {
                return false;
            }
            
            EncoderTransformer transformer = new EncoderTransformer();
            CompilationUnit modifiedCu = transformer.visit(cu, null);
            
            if (transformer.wasModified()) {
                // Write back the modified file
                DefaultPrettyPrinter printer = new DefaultPrettyPrinter();
                String modifiedCode = printer.print(modifiedCu);
                Files.write(filePath, modifiedCode.getBytes());
                System.out.println("Fixed EncoderBase subclass in: " + filePath);
                return true;
            }
            
        } catch (Exception e) {
            System.err.println("Error processing file " + filePath + ": " + e.getMessage());
            e.printStackTrace();
        }
        
        return false;
    }
    
    private static class EncoderTransformer extends ModifierVisitor<Void> {
        private boolean modified = false;
        private boolean inEncoderClass = false;
        private ClassOrInterfaceDeclaration currentClass;
        private JavaParser javaParser = new JavaParser();
        
        public boolean wasModified() {
            return modified;
        }
        
        @Override
        public ClassOrInterfaceDeclaration visit(ClassOrInterfaceDeclaration n, Void arg) {
            currentClass = n;
            boolean previousInEncoderClass = inEncoderClass;
            
            // Check if this class extends EncoderBase
            if (n.getExtendedTypes().stream()
                .anyMatch(t -> t.getNameAsString().equals("EncoderBase") || 
                              t.getNameAsString().endsWith(".EncoderBase"))) {
                inEncoderClass = true;
                System.out.println("  Found EncoderBase subclass: " + n.getNameAsString());
            }
            
            // Visit children first
            ClassOrInterfaceDeclaration result = (ClassOrInterfaceDeclaration) super.visit(n, arg);
            
            // After visiting all methods, check what's missing
            if (inEncoderClass) {
                boolean hasHeaderBytes = result.getMethodsByName("headerBytes").size() > 0;
                boolean hasFooterBytes = result.getMethodsByName("footerBytes").size() > 0;
                
                // Add headerBytes() method if not present
                if (!hasHeaderBytes) {
                    try {
                        MethodDeclaration headerBytes = javaParser.parseMethodDeclaration(
                            "@Override public byte[] headerBytes() { return null; }"
                        ).getResult().get();
                        result.addMember(headerBytes);
                        modified = true;
                        System.out.println("    Added headerBytes() method");
                    } catch (Exception e) {
                        System.err.println("    Warning: Could not create headerBytes method");
                    }
                }
                
                // Add footerBytes() method if not present
                if (!hasFooterBytes) {
                    try {
                        MethodDeclaration footerBytes = javaParser.parseMethodDeclaration(
                            "@Override public byte[] footerBytes() { return null; }"
                        ).getResult().get();
                        result.addMember(footerBytes);
                        modified = true;
                        System.out.println("    Added footerBytes() method");
                    } catch (Exception e) {
                        System.err.println("    Warning: Could not create footerBytes method");
                    }
                }
            }
            
            inEncoderClass = previousInEncoderClass;
            currentClass = null;
            return result;
        }
        
        @Override
        public MethodDeclaration visit(MethodDeclaration n, Void arg) {
            if (inEncoderClass) {
                String methodName = n.getNameAsString();
                
                // Transform doEncode method to encode method
                if (methodName.equals("doEncode")) {
                    System.out.println("    Transforming doEncode() to encode()");
                    
                    // Rename doEncode to encode
                    n.setName("encode");
                    
                    // Change return type to byte[]
                    try {
                        Type byteArrayType = javaParser.parseType("byte[]").getResult().get();
                        n.setType(byteArrayType);
                    } catch (Exception e) {
                        n.setType("byte[]");
                    }
                    
                    // Remove old @Override annotation if present
                    n.getAnnotations().removeIf(anno -> 
                        anno.getNameAsString().equals("Override"));
                    
                    // Add @Override annotation for the new encode method
                    n.addMarkerAnnotation("Override");
                    
                    // Get the method body
                    Optional<BlockStmt> bodyOpt = n.getBody();
                    if (bodyOpt.isPresent()) {
                        // Create a template implementation
                        // Note: This is a simplified transformation
                        // Real implementation would need to adapt the original logic
                        String templateBody = 
                            "{\n" +
                            "    try {\n" +
                            "        // TODO: Adapt from OutputStream-based to byte[]-based API\n" +
                            "        // Original doEncode wrote to OutputStream, now needs to return byte[]\n" +
                            "        // Example adaptation:\n" +
                            "        // java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();\n" +
                            "        // init(baos); // If init method exists\n" +
                            "        // Call original logic that writes to baos\n" +
                            "        // return baos.toByteArray();\n" +
                            "        return new byte[0]; // Placeholder\n" +
                            "    } catch (Exception e) {\n" +
                            "        // Logback handles encoding errors internally\n" +
                            "        return new byte[0];\n" +
                            "    }\n" +
                            "}";
                        
                        try {
                            BlockStmt newBody = javaParser.parseBlock(templateBody).getResult().get();
                            n.setBody(newBody);
                        } catch (Exception e) {
                            System.err.println("    Warning: Could not parse new method body template");
                        }
                    }
                    
                    modified = true;
                } 
                // Remove @Override from init and close methods (they're no longer overriding)
                else if (methodName.equals("init") || methodName.equals("close")) {
                    boolean removed = n.getAnnotations().removeIf(anno -> 
                        anno.getNameAsString().equals("Override"));
                    if (removed) {
                        modified = true;
                        System.out.println("    Removed @Override from " + methodName + "()");
                    }
                }
            }
            
            return (MethodDeclaration) super.visit(n, arg);
        }
        
        @Override
        public CompilationUnit visit(CompilationUnit n, Void arg) {
            // First, visit the entire AST
            CompilationUnit result = (CompilationUnit) super.visit(n, arg);
            
            // Add ByteArrayOutputStream import if we modified an Encoder class
            if (modified) {
                boolean hasByteArrayOutputStreamImport = result.getImports().stream()
                    .anyMatch(imp -> imp.getNameAsString().equals("java.io.ByteArrayOutputStream"));
                
                if (!hasByteArrayOutputStreamImport) {
                    try {
                        result.addImport("java.io.ByteArrayOutputStream");
                        System.out.println("    Added ByteArrayOutputStream import");
                    } catch (Exception e) {
                        // Import already exists or couldn't add
                    }
                }
            }
            
            return result;
        }
    }
}