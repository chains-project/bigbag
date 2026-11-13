package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.*;
import com.github.javaparser.ast.nodeTypes.NodeWithType;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Generic JavaParser transformation to fix Hamcrest StringContains and StringStartsWith constructor calls.
 * 
 * This transformation fixes breaking changes in Hamcrest where StringContains and StringStartsWith constructors
 * were changed from (boolean, String) to just (String).
 * 
 * Usage: java -cp target/classes:$(mvn dependency:build-classpath -Dmdep.outputFile=/dev/stdout -q) github.chains.Main /path/to/source/directory
 */
public class Main {
    public static void main(String[] args) throws IOException {
        if (args.length != 1) {
            System.err.println("Usage: java -cp ... github.chains.Main <source_directory>");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        Path dir = Paths.get(sourceDir);
        
        try (Stream<Path> paths = Files.walk(dir)) {
            List<Path> javaFiles = paths
                .filter(Files::isRegularFile)
                .filter(path -> path.toString().endsWith(".java"))
                .collect(Collectors.toList());
            
            for (Path javaFile : javaFiles) {
                fixStringContainsConstructors(javaFile);
            }
        }
    }
    
    private static void fixStringContainsConstructors(Path javaFile) throws IOException {
        String content = new String(Files.readAllBytes(javaFile));
        CompilationUnit cu = StaticJavaParser.parse(content);
        
        // Visit all expressions to find StringContains/StartsWith constructor calls
        cu.accept(new VoidVisitorAdapter<Void>() {
            @Override
            public void visit(ObjectCreationExpr n, Void arg) {
                super.visit(n, arg);
                
                // Check if this is a StringContains or StringStartsWith constructor call
                String typeName = n.getType().getNameAsString();
                if ("StringContains".equals(typeName) || "StringStartsWith".equals(typeName)) {
                    // Check if it has two arguments (boolean, String)
                    if (n.getArguments().size() == 2) {
                        // Get the second argument which should be the string
                        Expression secondArg = n.getArguments().get(1);
                        
                        // Check that first argument is a boolean literal
                        Expression firstArg = n.getArguments().get(0);
                        if (firstArg instanceof BooleanLiteralExpr) {
                            // Replace with single-argument constructor
                            n.setArguments(com.github.javaparser.ast.NodeList.nodeList(secondArg));
                        }
                    }
                }
            }
        }, null);
        
        // Write back the fixed content
        Files.write(javaFile, cu.toString().getBytes());
    }
}