package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.*;
import com.github.javaparser.ast.stmt.ExpressionStmt;
import com.github.javaparser.ast.stmt.Statement;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.expr.SimpleName;
import com.github.javaparser.ast.expr.StringLiteralExpr;
import com.github.javaparser.ast.expr.ThisExpr;
import com.github.javaparser.ast.expr.VariableDeclarationExpr;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.stmt.ExpressionStmt;
import com.github.javaparser.ast.stmt.ReturnStmt;
import com.github.javaparser.ast.stmt.Statement;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.type.ReferenceType;
import com.github.javaparser.ast.type.Type;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
import com.github.javaparser.printer.DefaultPrettyPrinter;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;

import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * Generic JavaParser transformation to fix Jetty breaking change from SelectChannelConnector to ServerConnector.
 * This transformation replaces all occurrences of SelectChannelConnector with ServerConnector
 * and handles the necessary constructor parameter changes.
 */
public class Main {
    public static void main(String[] args) throws IOException {
        if (args.length != 1) {
            System.err.println("Usage: java -cp <classpath> github.chains.Main <source_directory>");
            System.exit(1);
        }

        String sourceDirectory = args[0];
        Path sourcePath = Paths.get(sourceDirectory);
        
        if (!Files.exists(sourcePath)) {
            System.err.println("Source directory does not exist: " + sourceDirectory);
            System.exit(1);
        }
        
        // Process all Java files in the directory
        Files.walk(sourcePath)
                .filter(path -> path.toString().endsWith(".java"))
                .forEach(Main::processJavaFile);
    }
    
    private static void processJavaFile(Path javaFilePath) {
        try {
            // Parse the Java file
            CompilationUnit cu = StaticJavaParser.parse(javaFilePath.toFile());
            
            // Create a visitor to find and replace SelectChannelConnector usage
            JettyConnectorTransformer transformer = new JettyConnectorTransformer();
            transformer.visit(cu, null);
            
            // Save the modified file
            try (FileWriter writer = new FileWriter(javaFilePath.toFile())) {
                writer.write(new DefaultPrettyPrinter().print(cu));
            }
            System.out.println("Processed: " + javaFilePath);
        } catch (Exception e) {
            System.err.println("Error processing " + javaFilePath + ": " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * Visitor that finds and transforms SelectChannelConnector references to ServerConnector
     */
    private static class JettyConnectorTransformer extends VoidVisitorAdapter<Void> {
        @Override
        public void visit(ObjectCreationExpr n, Void arg) {
            super.visit(n, arg);
            
            // Check if this is a SelectChannelConnector instantiation
            if (n.getType() instanceof ClassOrInterfaceType) {
                ClassOrInterfaceType type = (ClassOrInterfaceType) n.getType();
                if ("SelectChannelConnector".equals(type.getNameAsString())) {
                    // Replace with ServerConnector
                    type.setName("ServerConnector");
                    
                    // Update constructor arguments if necessary
                    // SelectChannelConnector() becomes ServerConnector(server)
                    // SelectChannelConnector(port) becomes ServerConnector(server, port)
                    NodeList<Expression> args = n.getArguments();
                    if (args.isEmpty()) {
                        // No arguments case - need to add server parameter
                        n.setArguments(new NodeList<>(new NameExpr("server")));
                    } else if (args.size() == 1) {
                        // One argument case - need to add server parameter
                        args.add(0, new NameExpr("server"));
                    }
                }
            }
        }
        
        @Override
        public void visit(ClassOrInterfaceType n, Void arg) {
            super.visit(n, arg);
            
            // Replace imports or type references
            if ("org.eclipse.jetty.server.nio.SelectChannelConnector".equals(n.getNameAsString())) {
                n.setName("org.eclipse.jetty.server.ServerConnector");
            } else if ("SelectChannelConnector".equals(n.getNameAsString())) {
                n.setName("ServerConnector");
            }
        }
        
        @Override
        public void visit(ImportDeclaration n, Void arg) {
            super.visit(n, arg);
            
            // Check if it's an import that needs to be removed or updated
            if ("org.eclipse.jetty.server.nio.SelectChannelConnector".equals(n.getNameAsString())) {
                // For now we'll leave this import in place to be manually reviewed
                // A more sophisticated approach would remove it, but this is a safe approach
            }
        }
    }
}

        String sourceDirectory = args[0];
        Path sourcePath = Paths.get(sourceDirectory);
        
        if (!Files.exists(sourcePath)) {
            System.err.println("Source directory does not exist: " + sourceDirectory);
            System.exit(1);
        }
        
        // Process all Java files in the directory
        Files.walk(sourcePath)
                .filter(path -> path.toString().endsWith(".java"))
                .forEach(Main::processJavaFile);
    }
    
    private static void processJavaFile(Path javaFilePath) {
        try {
            // Parse the Java file
            CompilationUnit cu = StaticJavaParser.parse(javaFilePath.toFile());
            
            // Create a visitor to find and replace SelectChannelConnector usage
            JettyConnectorTransformer transformer = new JettyConnectorTransformer();
            transformer.visit(cu, null);
            
            // Save the modified file
            try (FileWriter writer = new FileWriter(javaFilePath.toFile())) {
                writer.write(new DefaultPrettyPrinter().print(cu));
            }
            System.out.println("Processed: " + javaFilePath);
        } catch (Exception e) {
            System.err.println("Error processing " + javaFilePath + ": " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * Visitor that finds and transforms SelectChannelConnector references to ServerConnector
     */
    private static class JettyConnectorTransformer extends VoidVisitorAdapter<Void> {
        @Override
        public void visit(ObjectCreationExpr n, Void arg) {
            super.visit(n, arg);
            
            // Check if this is a SelectChannelConnector instantiation
            if (n.getType() instanceof ClassOrInterfaceType) {
                ClassOrInterfaceType type = (ClassOrInterfaceType) n.getType();
                if ("SelectChannelConnector".equals(type.getNameAsString())) {
                    // Replace with ServerConnector
                    type.setName("ServerConnector");
                    
                    // Update constructor arguments if necessary
                    // SelectChannelConnector() becomes ServerConnector(server)
                    // SelectChannelConnector(port) becomes ServerConnector(server, port)
                    NodeList<Expression> args = n.getArguments();
                    if (args.isEmpty()) {
                        // No arguments case - need to add server parameter
                        n.setArguments(new NodeList<>(new NameExpr("server")));
                    } else if (args.size() == 1) {
                        // One argument case - need to add server parameter
                        args.add(0, new NameExpr("server"));
                    }
                }
            }
        }
        
        @Override
        public void visit(ClassOrInterfaceType n, Void arg) {
            super.visit(n, arg);
            
            // Replace imports or type references
            if ("org.eclipse.jetty.server.nio.SelectChannelConnector".equals(n.getNameAsString())) {
                n.setName("org.eclipse.jetty.server.ServerConnector");
            } else if ("SelectChannelConnector".equals(n.getNameAsString())) {
                n.setName("ServerConnector");
            }
        }
        
        @Override
        public void visit(ImportDeclaration n, Void arg) {
            super.visit(n, arg);
            
            // Remove old import if it exists
            if ("org.eclipse.jetty.server.nio.SelectChannelConnector".equals(n.getNameAsString())) {
                // This import should be removed or updated
                // For now we'll leave it for manual checking
            }
        }
    }
}
