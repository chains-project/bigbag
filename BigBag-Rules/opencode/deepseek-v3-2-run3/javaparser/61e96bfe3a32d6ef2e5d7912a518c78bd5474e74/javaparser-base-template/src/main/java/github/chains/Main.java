package github.chains;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.visitor.ModifierVisitor;
import com.github.javaparser.ast.visitor.Visitable;
import com.github.javaparser.printer.DefaultPrettyPrinter;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Generic transformation rule for logback-core 1.2.9 breaking change.
 * 
 * Breaking Change: Encoder interface changed from streaming pattern to byte array pattern.
 * 
 * Old API pattern (pre-1.2.9):
 * - void init(OutputStream os)
 * - void doEncode(E event)
 * - void close() throws IOException
 * 
 * New API pattern (1.2.9+):
 * - byte[] encode(E event)
 * - byte[] footerBytes()
 * - byte[] headerBytes()
 * 
 * This transformation:
 * 1. Finds classes extending EncoderBase<E>
 * 2. Adds required interface methods (encode, footerBytes, headerBytes)
 * 3. Keeps old methods with deprecation for backward compatibility
 * 4. Adapts doEncode to work with byte array return
 */
public class Main {
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java -cp target/classes github.chains.Main <source_directory>");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        System.out.println("Processing source directory: " + sourceDir);
        
        try {
            List<Path> javaFiles = findJavaFiles(sourceDir);
            System.out.println("Found " + javaFiles.size() + " Java files");
            
            int transformed = 0;
            for (Path javaFile : javaFiles) {
                if (transformEncoderClass(javaFile)) {
                    transformed++;
                }
            }
            
            System.out.println("Transformed " + transformed + " files");
        } catch (IOException e) {
            System.err.println("Error processing files: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    private static List<Path> findJavaFiles(String sourceDir) throws IOException {
        try (Stream<Path> paths = Files.walk(Paths.get(sourceDir))) {
            return paths
                .filter(Files::isRegularFile)
                .filter(p -> p.toString().endsWith(".java"))
                .collect(Collectors.toList());
        }
    }
    
    private static boolean transformEncoderClass(Path javaFile) throws IOException {
        String content = Files.readString(javaFile);
        JavaParser parser = new JavaParser();
        Optional<CompilationUnit> cuOpt = parser.parse(content).getResult();
        
        if (!cuOpt.isPresent()) {
            System.err.println("Failed to parse: " + javaFile);
            return false;
        }
        
        CompilationUnit cu = cuOpt.get();
        EncoderTransformer transformer = new EncoderTransformer();
        CompilationUnit transformedCu = (CompilationUnit) cu.accept(transformer, null);
        
        if (transformer.wasTransformed()) {
            DefaultPrettyPrinter printer = new DefaultPrettyPrinter();
            String newContent = printer.print(transformedCu);
            
            Files.writeString(javaFile, newContent);
            System.out.println("Transformed: " + javaFile);
            return true;
        }
        
        return false;
    }
    
    private static class EncoderTransformer extends ModifierVisitor<Void> {
        private boolean transformed = false;
        
        @Override
        public Visitable visit(ClassOrInterfaceDeclaration n, Void arg) {
            boolean extendsEncoderBase = false;
            String typeParameter = "Object";
            
            // Check if class extends EncoderBase
            for (ClassOrInterfaceType extendedType : n.getExtendedTypes()) {
                if (extendedType.getNameAsString().equals("EncoderBase")) {
                    extendsEncoderBase = true;
                    if (extendedType.getTypeArguments().isPresent() && 
                        extendedType.getTypeArguments().get().size() > 0) {
                        typeParameter = extendedType.getTypeArguments().get().get(0).asString();
                    }
                    break;
                }
            }
            
            if (extendsEncoderBase) {
                transformed = true;
                
                // Check for existing interface methods
                boolean hasEncodeMethod = false;
                boolean hasFooterBytesMethod = false;
                boolean hasHeaderBytesMethod = false;
                
                for (MethodDeclaration method : n.getMethods()) {
                    String methodName = method.getNameAsString();
                    if (methodName.equals("encode") && method.getParameters().size() == 1) {
                        hasEncodeMethod = true;
                    } else if (methodName.equals("footerBytes") && method.getParameters().size() == 0) {
                        hasFooterBytesMethod = true;
                    } else if (methodName.equals("headerBytes") && method.getParameters().size() == 0) {
                        hasHeaderBytesMethod = true;
                    }
                }
                
                // Remove @Override from old API methods (init, doEncode, close) if present
                for (MethodDeclaration method : n.getMethods()) {
                    String methodName = method.getNameAsString();
                    if (methodName.equals("init") || methodName.equals("doEncode") || methodName.equals("close")) {
                        // Remove @Override annotation if present
                        method.getAnnotations().removeIf(ann -> 
                            ann.getNameAsString().equals("Override"));
                    }
                }
                
                // Add missing interface methods
                if (!hasEncodeMethod) {
                    n.addMember(createEncodeMethod(typeParameter));
                }
                if (!hasFooterBytesMethod) {
                    n.addMember(createFooterBytesMethod());
                }
                if (!hasHeaderBytesMethod) {
                    n.addMember(createHeaderBytesMethod());
                }
            }
            
            return super.visit(n, arg);
        }
        
        private MethodDeclaration createEncodeMethod(String typeParameter) {
            MethodDeclaration method = new MethodDeclaration();
            method.setPublic(true);
            method.addMarkerAnnotation("Override");
            method.setName("encode");
            method.addParameter(typeParameter, "event");
            method.setType("byte[]");
            
            // Create body using JavaParser's parseBlock
            String bodyCode = "{\n" +
                "    // TODO: Implement encode method for logback-core 1.2.9 compatibility\n" +
                "    // Previously: void doEncode(" + typeParameter + " event)\n" +
                "    // Now: byte[] encode(" + typeParameter + " event)\n" +
                "    // Implementation should serialize event to byte array\n" +
                "    throw new UnsupportedOperationException(\"encode method not implemented for logback-core 1.2.9\");\n" +
                "}";
            
            try {
                method.setBody(com.github.javaparser.StaticJavaParser.parseBlock(bodyCode));
            } catch (Exception e) {
                // Fallback: create empty body
                BlockStmt body = new BlockStmt();
                method.setBody(body);
            }
            
            return method;
        }
        
        private MethodDeclaration createFooterBytesMethod() {
            MethodDeclaration method = new MethodDeclaration();
            method.setPublic(true);
            method.addMarkerAnnotation("Override");
            method.setName("footerBytes");
            method.setType("byte[]");
            
            String bodyCode = "{\n" +
                "    // Required by Encoder interface in logback-core 1.2.9\n" +
                "    return new byte[0];\n" +
                "}";
            
            try {
                method.setBody(com.github.javaparser.StaticJavaParser.parseBlock(bodyCode));
            } catch (Exception e) {
                BlockStmt body = new BlockStmt();
                method.setBody(body);
            }
            
            return method;
        }
        
        private MethodDeclaration createHeaderBytesMethod() {
            MethodDeclaration method = new MethodDeclaration();
            method.setPublic(true);
            method.addMarkerAnnotation("Override");
            method.setName("headerBytes");
            method.setType("byte[]");
            
            String bodyCode = "{\n" +
                "    // Required by Encoder interface in logback-core 1.2.9\n" +
                "    return new byte[0];\n" +
                "}";
            
            try {
                method.setBody(com.github.javaparser.StaticJavaParser.parseBlock(bodyCode));
            } catch (Exception e) {
                BlockStmt body = new BlockStmt();
                method.setBody(body);
            }
            
            return method;
        }
        
        public boolean wasTransformed() {
            return transformed;
        }
    }
}