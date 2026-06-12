package github.chains;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.FieldAccessExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
import com.github.javaparser.printer.DefaultPrettyPrinter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;

public class Main {
    public static void main(String[] args) throws IOException {
        if (args.length != 2) {
            System.err.println("Usage: java Main <source-directory> <output-directory>");
            System.exit(1);
        }

        String sourceDir = args[0];
        String outputDir = args[1];

        // Create output directory if it doesn't exist
        Files.createDirectories(Paths.get(outputDir));

        // Create a JavaParser instance
        JavaParser parser = new JavaParser();

        // Create a visitor that will transform the code
        VoidVisitorAdapter<Void> transformer = new VoidVisitorAdapter<Void>() {
            @Override
            public void visit(MethodCallExpr n, Void arg) {
                super.visit(n, arg);
                
                // Check if this is a call to JAXBToStringStrategy.getInstance()
                if (n.getName().asString().equals("getInstance") &&
                    n.getScope().isPresent() &&
                    n.getScope().get() instanceof NameExpr) {
                    
                    NameExpr scope = (NameExpr) n.getScope().get();
                    if (scope.getName().asString().equals("JAXBToStringStrategy")) {
                        // Replace the method call with field access
                        FieldAccessExpr fieldAccess = new FieldAccessExpr(
                            scope,
                            "INSTANCE"
                        );
                        n.getParentNode().ifPresent(parent -> parent.replace(n, fieldAccess));
                    }
                }
            }
        };

        // Process all Java files in the source directory
        processDirectory(new File(sourceDir), new File(outputDir), parser, transformer);
    }

    private static void processDirectory(File sourceDir, File outputDir, JavaParser parser, VoidVisitorAdapter<Void> transformer) throws IOException {
        if (!sourceDir.isDirectory()) {
            return;
        }

        // Create corresponding output directory
        File outputSubDir = new File(outputDir, sourceDir.getName());
        outputSubDir.mkdirs();

        File[] files = sourceDir.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isDirectory()) {
                    processDirectory(file, outputDir, parser, transformer);
                } else if (file.getName().endsWith(".java")) {
                    processJavaFile(file, outputDir, parser, transformer);
                }
            }
        }
    }

    private static void processJavaFile(File sourceFile, File outputDir, JavaParser parser, VoidVisitorAdapter<Void> transformer) throws IOException {
        try (FileInputStream in = new FileInputStream(sourceFile)) {
            // Parse the Java file
            com.github.javaparser.ParseResult<CompilationUnit> result = parser.parse(in);
            
            if (result.isSuccessful()) {
                CompilationUnit cu = result.getResult().get();
                
                // Apply transformation
                cu.accept(transformer, null);
                
                // Write the transformed file to the output directory
                File outputFile = new File(outputDir, sourceFile.getName());
                outputFile.getParentFile().mkdirs();
                
                try (FileWriter writer = new FileWriter(outputFile)) {
                    writer.write(new DefaultPrettyPrinter().print(cu));
                }
            }
        }
    }
}