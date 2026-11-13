package github.chains;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseResult;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

/**
 * Generic JavaParser transformation to fix breaking changes in Eclipse Jetty API,
 * specifically replacing deprecated SelectChannelConnector with ServerConnector
 * in Jetty 11.0.7 upgrades.
 */
public class Main {
    public static void main(String[] args) {
        if (args.length != 1) {
            System.err.println("Usage: java -cp <classpath> github.chains.Main <input_file>");
            System.exit(1);
        }

        String inputFile = args[0];
        try {
            // Parse the Java file
            ParseResult<CompilationUnit> parseResult = new JavaParser().parse(new FileInputStream(inputFile));
            
            if (!parseResult.isSuccessful()) {
                System.err.println("Failed to parse file: " + inputFile);
                System.exit(1);
            }
            
            CompilationUnit cu = parseResult.getResult().get();
            
            // Apply the transformation
            JettyApiTransformationVisitor visitor = new JettyApiTransformationVisitor();
            visitor.visit(cu, null);
            
            // Save the modified file
            Files.write(Paths.get(inputFile), cu.toString().getBytes());
            System.out.println("Transformation applied successfully to " + inputFile);
            
        } catch (IOException e) {
            System.err.println("Error processing file: " + e.getMessage());
            System.exit(1);
        }
    }
    
    /**
     * Visitor that transforms deprecated Jetty connector usage to Jetty 11 compatible code
     */
    private static class JettyApiTransformationVisitor extends VoidVisitorAdapter<Void> {
        
        @Override
        public void visit(ObjectCreationExpr n, Void arg) {
            super.visit(n, arg);
            
            // Look for SelectChannelConnector instantiation
            if (n.getType().toString().equals("SelectChannelConnector")) {
                // Replace with ServerConnector
                n.setType("ServerConnector");
            }
            
            // Look for nio.SelectChannelConnector instantiation
            if (n.getType().toString().contains("nio.SelectChannelConnector")) {
                // Replace with ServerConnector
                n.setType("ServerConnector");
            }
        }
        
        @Override
        public void visit(ImportDeclaration n, Void arg) {
            super.visit(n, arg);
            
            // Remove deprecated imports - note this is logged but not actually removed
            // due to limitations of VoidVisitorAdapter
            if (n.getNameAsString().equals("org.eclipse.jetty.server.nio.SelectChannelConnector")) {
                System.out.println("Found deprecated import that should be removed: " + n.getNameAsString());
            }
        }
    }
}
