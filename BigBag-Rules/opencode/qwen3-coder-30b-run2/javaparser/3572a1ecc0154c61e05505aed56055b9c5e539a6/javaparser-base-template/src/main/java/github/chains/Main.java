package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.expr.*;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.type.Type;

import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Generic JavaParser transformation for fixing libthrift constructor signature changes.
 * This rule specifically addresses breaking changes in libthrift protocol constructors
 * that require additional parameters.
 */
public class Main {
    public static void main(String[] args) throws IOException {
        if (args.length < 1) {
            System.err.println("Usage: java Main <source_directory>");
            System.exit(1);
        }

        String sourceDirectory = args[0];
        Path sourcePath = Paths.get(sourceDirectory);

        if (!Files.exists(sourcePath)) {
            System.err.println("Source directory does not exist: " + sourceDirectory);
            System.exit(1);
        }

        Files.walk(sourcePath)
                .filter(path -> path.toString().endsWith(".java"))
                .forEach(Main::processFile);
    }

    private static void processFile(Path filePath) {
        try {
            CompilationUnit cu = StaticJavaParser.parse(filePath.toFile());

            // Find all constructor calls to TBinaryProtocol and related protocol classes
            // that match old signature (just TTransport) to add new parameters
            cu.findAll(ObjectCreationExpr.class).stream()
                    .filter(expr -> {
                        // Match constructors for TBinaryProtocol, TCompactProtocol, etc.
                        String className = expr.getType().asString();
                        return className.equals("TBinaryProtocol") || 
                               className.equals("TCompactProtocol") ||
                               className.equals("TJSONProtocol");
                    })
                    .filter(expr -> {
                        // Only match when there's exactly 1 argument (the old form)
                        return expr.getArguments().size() == 1;
                    })
                    .forEach(Main::fixProtocolConstructorCall);

            // Save the modified file
            try (FileWriter writer = new FileWriter(filePath.toFile())) {
                writer.write(cu.toString());
            }
        } catch (IOException e) {
            System.err.println("Error processing file " + filePath + ": " + e.getMessage());
        }
    }

    private static void fixProtocolConstructorCall(ObjectCreationExpr expr) {
        // Get the type being constructed
        Type type = expr.getType();
        
        // Get the single argument (should be TTransport)
        Expression transportArg = expr.getArguments().get(0);
        
        if (type.asClassOrInterfaceType().getNameAsString().equals("TBinaryProtocol")) {
            // For TBinaryProtocol, change to factory approach
            ObjectCreationExpr factoryExpr = new ObjectCreationExpr();
            factoryExpr.setType(new ClassOrInterfaceType(null, "TBinaryProtocol$Factory"));
            
            MethodCallExpr factoryCall = new MethodCallExpr();
            factoryCall.setScope(factoryExpr);
            factoryCall.setName("getProtocol");
            factoryCall.setArguments(new NodeList<>(transportArg));
            
            // Replace the original expression with the factory approach
            expr.replace(factoryCall);
        } else if (type.asClassOrInterfaceType().getNameAsString().equals("TCompactProtocol")) {
            // For TCompactProtocol, change to factory approach
            ObjectCreationExpr factoryExpr = new ObjectCreationExpr();
            factoryExpr.setType(new ClassOrInterfaceType(null, "TCompactProtocol$Factory"));
            
            MethodCallExpr factoryCall = new MethodCallExpr();
            factoryCall.setScope(factoryExpr);
            factoryCall.setName("getProtocol");
            factoryCall.setArguments(new NodeList<>(transportArg));
            
            expr.replace(factoryCall);
        } else if (type.asClassOrInterfaceType().getNameAsString().equals("TJSONProtocol")) {
            // For TJSONProtocol, change to factory approach
            ObjectCreationExpr factoryExpr = new ObjectCreationExpr();
            factoryExpr.setType(new ClassOrInterfaceType(null, "TJSONProtocol$Factory"));
            
            MethodCallExpr factoryCall = new MethodCallExpr();
            factoryCall.setScope(factoryExpr);
            factoryCall.setName("getProtocol");
            factoryCall.setArguments(new NodeList<>(transportArg));
            
            expr.replace(factoryCall);
        }
    }
}
