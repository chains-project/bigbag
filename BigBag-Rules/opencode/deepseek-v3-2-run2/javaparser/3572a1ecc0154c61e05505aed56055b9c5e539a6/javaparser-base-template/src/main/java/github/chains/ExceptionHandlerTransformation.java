package github.chains;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.stmt.CatchClause;
import com.github.javaparser.ast.stmt.TryStmt;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.type.ReferenceType;
import com.github.javaparser.printer.lexicalpreservation.LexicalPreservingPrinter;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Generic transformation to handle TTransportException thrown by TFastFramedTransport constructors
 * in libthrift 0.16.0
 */
public class ExceptionHandlerTransformation {
    
    // Configuration parameters - can be changed for different projects
    private static final String TFAST_FRAMED_TRANSPORT_CLASS = "org.apache.thrift.transport.layered.TFastFramedTransport";
    private static final String TTRANSPORT_EXCEPTION_CLASS = "org.apache.thrift.transport.TTransportException";
    
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java -jar exception-handler.jar <source-directory>");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        System.out.println("Processing source directory: " + sourceDir);
        
        try {
            List<Path> javaFiles = Files.walk(Paths.get(sourceDir))
                    .filter(path -> path.toString().endsWith(".java"))
                    .collect(Collectors.toList());
            
            System.out.println("Found " + javaFiles.size() + " Java files");
            
            int modifiedFiles = 0;
            for (Path javaFile : javaFiles) {
                if (processFile(javaFile.toFile())) {
                    modifiedFiles++;
                }
            }
            
            System.out.println("Successfully modified " + modifiedFiles + " files");
            
        } catch (IOException e) {
            System.err.println("Error processing files: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
    
    private static boolean processFile(File file) {
        try (FileInputStream input = new FileInputStream(file)) {
            JavaParser parser = new JavaParser();
            CompilationUnit cu = parser.parse(input).getResult().orElse(null);
            
            if (cu == null) {
                return false;
            }
            
            LexicalPreservingPrinter.setup(cu);
            boolean modified = false;
            
            // Find all ObjectCreationExpr nodes that create TFastFramedTransport
            List<ObjectCreationExpr> objectCreations = cu.findAll(ObjectCreationExpr.class);
            for (ObjectCreationExpr objectCreation : objectCreations) {
                if (objectCreation.getType().getNameAsString().equals("TFastFramedTransport")) {
                    // Check if this is inside a method that doesn't declare TTransportException
                    Node parent = objectCreation.getParentNode().orElse(null);
                    while (parent != null && !(parent instanceof MethodDeclaration)) {
                        parent = parent.getParentNode().orElse(null);
                    }
                    
                    if (parent instanceof MethodDeclaration) {
                        MethodDeclaration method = (MethodDeclaration) parent;
                        
                        // Check if method already declares TTransportException
                        boolean declaresException = method.getThrownExceptions().stream()
                                .anyMatch(exception -> {
                                    if (exception instanceof ReferenceType) {
                                        ReferenceType refType = (ReferenceType) exception;
                                        return refType.getElementType().getNameAsString().equals("TTransportException");
                                    }
                                    return false;
                                });
                        
                        if (!declaresException) {
                            // Add throws declaration to method
                            method.addThrownException(new ClassOrInterfaceType(null, "TTransportException"));
                            modified = true;
                            System.out.println("Added TTransportException throws declaration in " + file.getName());
                        }
                    }
                }
            }
            
            if (modified) {
                try (FileOutputStream output = new FileOutputStream(file)) {
                    output.write(LexicalPreservingPrinter.print(cu).getBytes());
                }
                return true;
            }
            
        } catch (IOException e) {
            System.err.println("Error processing file " + file.getName() + ": " + e.getMessage());
        }
        
        return false;
    }
}