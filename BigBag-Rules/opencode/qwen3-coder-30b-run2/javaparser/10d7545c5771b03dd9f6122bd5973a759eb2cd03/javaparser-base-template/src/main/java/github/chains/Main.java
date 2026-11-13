package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.*;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;

import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class Main {
    public static void main(String[] args) throws IOException {
        if (args.length != 1) {
            System.err.println("Usage: java Main <source_directory>");
            System.exit(1);
        }

        String sourceDir = args[0];
        Path dir = Paths.get(sourceDir);
        
        if (!Files.exists(dir) || !Files.isDirectory(dir)) {
            System.err.println("Invalid directory: " + sourceDir);
            System.exit(1);
        }

        // Process all Java files in the directory recursively
        Files.walk(dir)
                .filter(path -> path.toString().endsWith(".java"))
                .forEach(Main::processFile);
    }

    private static void processFile(Path filePath) {
        try {
            CompilationUnit cu = StaticJavaParser.parse(filePath.toFile());
            
            // Apply transformation to fix DropwizardApacheConnector constructor calls
            boolean modified = applyTransformation(cu);
            
            if (modified) {
                // Write back to file
                try (FileWriter writer = new FileWriter(filePath.toFile())) {
                    writer.write(cu.toString());
                }
                System.out.println("Updated: " + filePath);
            }
        } catch (Exception e) {
            System.err.println("Error processing file " + filePath + ": " + e.getMessage());
        }
    }

    private static boolean applyTransformation(CompilationUnit cu) {
        final boolean[] modified = {false};
        
        // Find all constructor calls to DropwizardApacheConnector with 2 parameters
        // Pattern: new DropwizardApacheConnector(param1, param2)
        cu.accept(new VoidVisitorAdapter<Void>() {
            @Override
            public void visit(ObjectCreationExpr call, Void arg) {
                super.visit(call, arg);
                if (call.getType() instanceof ClassOrInterfaceType) {
                    ClassOrInterfaceType type = (ClassOrInterfaceType) call.getType();
                    if ("DropwizardApacheConnector".equals(type.getNameAsString())) {
                        if (call.getArguments().size() == 2) {
                            // Add a third argument: true (to enable connection pooling)
                            NodeList<Expression> newArgs = new NodeList<>();
                            newArgs.addAll(call.getArguments());
                            newArgs.add(new BooleanLiteralExpr(true)); // Add connection pooling enabled
                            
                            call.setArguments(newArgs);
                            modified[0] = true;
                        }
                    }
                }
            }
        }, null);

        return modified[0];
    }
}
