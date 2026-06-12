package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;

import java.io.*;
import java.nio.file.*;
import java.util.*;

public class Main {
    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            System.out.println("Usage: java Main <source_directory>");
            return;
        }

        String sourceDirectory = args[0];
        processDirectory(new File(sourceDirectory));
    }

    private static void processDirectory(File directory) throws IOException {
        File[] files = directory.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isDirectory()) {
                    processDirectory(file);
                } else if (file.getName().endsWith(".java")) {
                    processJavaFile(file);
                }
            }
        }
    }

    private static void processJavaFile(File file) throws IOException {
        String content = new String(Files.readAllBytes(file.toPath()));
        CompilationUnit cu = StaticJavaParser.parse(content);
        
        // Apply transformation
        FlywayTransformationVisitor visitor = new FlywayTransformationVisitor();
        visitor.visit(cu, null);
        
        // Write back to file if changes were made
        if (visitor.hasChanged()) {
            try (PrintWriter out = new PrintWriter(file)) {
                out.println(cu.toString());
            }
            System.out.println("Updated: " + file.getAbsolutePath());
        }
    }

    private static class FlywayTransformationVisitor extends VoidVisitorAdapter<Void> {
        private boolean changed = false;
        
        public boolean hasChanged() {
            return changed;
        }

        @Override
        public void visit(ObjectCreationExpr n, Void arg) {
            super.visit(n, arg);
            
            // Check if this is a new Flyway() call with no arguments
            if (n.getType().getNameAsString().equals("Flyway") && n.getArguments().isEmpty()) {
                System.out.println("Detected Flyway instantiation pattern that needs fixing");
                changed = true;
            }
        }
    }
}