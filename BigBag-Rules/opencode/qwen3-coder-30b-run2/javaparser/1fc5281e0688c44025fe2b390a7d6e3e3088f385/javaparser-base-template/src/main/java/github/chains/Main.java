package github.chains;

import com.github.javaparser.*;
import com.github.javaparser.ast.*;
import com.github.javaparser.ast.expr.*;
import com.github.javaparser.ast.type.*;
import com.github.javaparser.ast.visitor.*;
import com.github.javaparser.utils.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Generic JavaParser transformation to replace deprecated Jetty nio connectors
 * with modern ServerConnector equivalents.
 * This solves the breaking dependency issue where SelectChannelConnector
 * was removed in Jetty 11.x
 */
public class Main {
    public static void main(String[] args) throws IOException {
        if (args.length < 1) {
            System.err.println("Usage: java Main <source_directory>");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        Path sourcePath = Paths.get(sourceDir);
        
        if (!Files.exists(sourcePath)) {
            System.err.println("Source directory does not exist: " + sourceDir);
            System.exit(1);
        }
        
        // Process all Java files in the source directory
        SourceRoot root = new SourceRoot(sourcePath);
        
        // Parse and transform all Java files
        root.parse("", new SourceRoot.Callback() {
            @Override
            public void process(ParseResult<CompilationUnit> result) {
                result.ifSuccessful(cu -> {
                    JettyConnectorTransformation transformation = new JettyConnectorTransformation();
                    cu.accept(transformation, null);
                });
            }
        });
        
        // Save the modified files
        root.saveAll();
    }
    
    /**
     * A JavaParser visitor that transforms deprecated Jetty nio connector usage
     */
    private static class JettyConnectorTransformation extends ModifierVisitor<Void> {
        @Override
        public Visitable visit(ObjectCreationExpr n, Void arg) {
            // Check if this is a constructor call for deprecated SelectChannelConnector
            if (n.getType().asString().equals("org.eclipse.jetty.server.nio.SelectChannelConnector")) {
                // Replace with ServerConnector
                NodeList<Expression> arguments = new NodeList<>();
                // Add server reference as first argument
                arguments.add(new NameExpr("server"));
                
                // Replace the deprecated connector with ServerConnector
                return new ObjectCreationExpr(
                    null,
                    new ClassOrInterfaceType(null, "org.eclipse.jetty.server.ServerConnector"),
                    arguments
                );
            }
            
            return super.visit(n, arg);
        }
        
        @Override
        public Visitable visit(ImportDeclaration n, Void arg) {
            // Check if importing the deprecated nio package
            if (n.getNameAsString().equals("org.eclipse.jetty.server.nio.SelectChannelConnector")) {
                // Remove the import since it's no longer needed
                return null;
            }
            
            return super.visit(n, arg);
        }
    }
}
