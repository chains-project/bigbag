package github.chains;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.visitor.ModifierVisitor;
import com.github.javaparser.ast.visitor.Visitable;
import java.io.File;
import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.List;

public class Main {

    /**
     * Generic transformation rule for API breaking changes.
     * Configurable method renaming transformation.
     * Usage: java github.chains.Main <source-directory> [oldMethod=newMethod]
     * Example: java github.chains.Main /path/to/src nnQuery=query1NN
     */
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java github.chains.Main <source-directory> [oldMethod=newMethod]");
            System.err.println("Example: java github.chains.Main /path/to/src nnQuery=query1NN");
            System.exit(1);
        }
        String sourceDir = args[0];
        String transformationRule = args.length > 1 ? args[1] : "nnQuery=query1NN";
        System.out.println("Transforming source directory: " + sourceDir);
        System.out.println("Transformation rule: " + transformationRule);
        String[] parts = transformationRule.split("=");
        if (parts.length != 2) {
            System.err.println("Invalid transformation rule format. Use: oldMethod=newMethod");
            System.exit(1);
        }
        String oldMethod = parts[0];
        String newMethod = parts[1];
        try {
            transformProject(sourceDir, oldMethod, newMethod);
            System.out.println("Transformation completed successfully.");
        } catch (Exception e) {
            System.err.println("Error during transformation: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }

    private static void transformProject(String sourceDir, String oldMethod, String newMethod) throws FileNotFoundException {
        JavaParser parser = new JavaParser();
        List<File> javaFiles = findAllJavaFiles(new File(sourceDir));
        for (File file : javaFiles) {
            System.out.println("Processing: " + file.getPath());
            CompilationUnit cu = parser.parse(file).getResult().orElseThrow(() -> new RuntimeException("Failed to parse " + file.getPath()));
            // Apply transformation
            cu.accept(new MethodRenamingTransformer(oldMethod, newMethod), null);
            // Write back the transformed file
            try {
                java.nio.file.Files.write(file.toPath(), cu.toString().getBytes());
            } catch (Exception e) {
                throw new RuntimeException("Failed to save " + file.getPath(), e);
            }
        }
    }

    private static List<File> findAllJavaFiles(File dir) {
        List<File> javaFiles = new ArrayList<>();
        findJavaFilesRecursive(dir, javaFiles);
        return javaFiles;
    }

    private static void findJavaFilesRecursive(File dir, List<File> javaFiles) {
        if (!dir.exists() || !dir.isDirectory()) {
            return;
        }
        File[] files = dir.listFiles();
        if (files == null)
            return;
        for (File file : files) {
            if (file.isDirectory()) {
                findJavaFilesRecursive(file, javaFiles);
            } else if (file.getName().endsWith(".java")) {
                javaFiles.add(file);
            }
        }
    }

    /**
     * Visitor that transforms method calls by renaming them.
     * This is a generic transformation that can be applied to any project
     * affected by method renaming breaking changes.
     */
    private static class MethodRenamingTransformer extends ModifierVisitor<Void> {

        private final String oldMethod;

        private final String newMethod;

        public MethodRenamingTransformer(String oldMethod, String newMethod) {
            this.oldMethod = oldMethod;
            this.newMethod = newMethod;
        }

        @Override
        public Visitable visit(MethodCallExpr n, Void arg) {
            // Check if this is a call to the old method
            if (oldMethod.equals(n.getNameAsString())) {
                System.out.println("  Transforming " + oldMethod + "() -> " + newMethod + "() at line " + n.getRange().map(r -> r.begin.line).orElse(-1));
                // Rename the method call
                n.setName(newMethod);
            }
            return super.visit(n, arg);
        }
    }
}
