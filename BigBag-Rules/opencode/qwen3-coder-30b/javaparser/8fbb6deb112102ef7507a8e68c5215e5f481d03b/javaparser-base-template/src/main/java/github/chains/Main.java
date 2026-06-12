package github.chains;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

public class Main {
    public static void main(String[] args) throws IOException {
        if (args.length != 1) {
            System.err.println("Usage: java -jar transformation.jar <source-directory>");
            System.exit(1);
        }

        // For now, just process one file to verify basic functionality
        String sourceDir = args[0];
        String javaFilePath = sourceDir + "/test.java";
        java.nio.file.Path javaFile = Paths.get(javaFilePath);
        if (javaFile.toFile().exists()) {
            processJavaFile(javaFile);
        }
    }

    private static void processJavaFile(java.nio.file.Path javaFile) throws IOException {
        String content = Files.readString(javaFile);
        CompilationUnit cu = JavaParser.parse(content);
        
        // Visitor to find and replace SelectChannelConnector with ServerConnector
        cu.accept(new VoidVisitorAdapter<Void>() {
            @Override
            public void visit(ObjectCreationExpr n, Void arg) {
                super.visit(n, arg);
                
                // Check if this is an object creation of SelectChannelConnector
                if (n.getType() != null && 
                    n.getType().asString().equals("SelectChannelConnector")) {
                    
                    // Replace the class name from SelectChannelConnector to ServerConnector
                    n.setType("ServerConnector");
                }
            }
        }, null);
        
        // Write the modified file back
        Files.write(javaFile, cu.toString().getBytes());
    }
}