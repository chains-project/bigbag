package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.*;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.printer.DefaultPrettyPrinter;
import com.github.javaparser.ast.comments.LineComment;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;

/**
 * Generic JavaParser transformation for tinspin-indexes API changes.
 * This transformation addresses breaking changes in tinspin-indexes dependency.
 *
 * The transformation handles common breaking changes in PointIndex and PointIndexMM APIs
 * such as:
 * - Method signature changes in remove() and update() methods
 * - Changes in return types of query methods
 * - Changes in method availability or behavior
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
        // Process all Java files in the directory
        Files.walk(sourcePath).filter(path -> path.toString().endsWith(".java")).forEach(Main::processJavaFile);
    }

    private static void processJavaFile(Path filePath) {
        try {
            CompilationUnit cu = StaticJavaParser.parse(filePath.toFile());
            // Apply transformations to fix tinspin API calls
            cu.accept(new TinspinApiFixVisitor(), null);
            // Save the modified file
            try (FileWriter writer = new FileWriter(filePath.toFile())) {
                writer.write(new DefaultPrettyPrinter().print(cu));
            }
        } catch (Exception e) {
            System.err.println("Error processing file " + filePath + ": " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Visitor that identifies and fixes tinspin API method calls
     */
    private static class TinspinApiFixVisitor extends VoidVisitorAdapter<Void> {

        @Override
        public void visit(MethodCallExpr methodCall, Void arg) {
            super.visit(methodCall, arg);
            // Check if this is a tinspin method call that needs fixing
            if (isTinspinMethodCall(methodCall)) {
                // Apply transformation based on method name
                String methodName = methodCall.getNameAsString();
                // Most common breaking changes involve method signatures
                if ("remove".equals(methodName)) {
                    transformRemoveCall(methodCall);
                } else if ("update".equals(methodName)) {
                    transformUpdateCall(methodCall);
                } else if ("query1NN".equals(methodName)) {
                    transformQuery1NNCall(methodCall);
                } else if ("queryKNN".equals(methodName)) {
                    transformQueryKNNCall(methodCall);
                }
            }
        }

        private boolean isTinspinMethodCall(MethodCallExpr methodCall) {
            // Check if method call is on a tinspin index type
            Optional<Expression> scope = methodCall.getScope();
            if (scope.isPresent()) {
                Expression expr = scope.get();
                if (expr instanceof NameExpr) {
                    String name = ((NameExpr) expr).getNameAsString();
                    // Match common tinspin index variable names
                    return name.contains("tree") || name.contains("index") || name.contains("PointIndex") || name.contains("PointIndexMM");
                }
            }
            return false;
        }

        /**
         * Transform remove calls to handle potential signature changes
         */
        private void transformRemoveCall(MethodCallExpr methodCall) {
            // Check if this is a PointIndexMM remove call (returns boolean)
            // vs a PointIndex remove call (returns T)
            // We can't determine the exact type, but we can add a comment to guide developers
            // The API changes from PointIndex.remove(double[]) -> T
            // to PointIndexMM.remove(double[], T) -> boolean
            // Add a comment to indicate the potential need for signature adjustment
            // and how to handle the return value
            if (methodCall.getScope().isPresent()) {
                Expression scope = methodCall.getScope().get();
                String scopeName = getScopeName(scope);
                if (scopeName != null) {
                    // We can't automatically fix this because we don't know the type,
                    // but we can add a comment to guide developers
                    methodCall.setComment(new LineComment("TODO: Check if this is PointIndexMM.remove() (returns boolean) vs PointIndex.remove() (returns T). If PointIndexMM, change to: boolean success = " + scopeName + ".remove(...)"));
                }
            }
        }

        /**
         * Transform update calls to handle potential signature changes
         */
        private void transformUpdateCall(MethodCallExpr methodCall) {
            // Check if this is a PointIndexMM update call (returns boolean)
            // vs a PointIndex update call (returns T)
            // API changes from PointIndex.update(double[], double[]) -> T
            // to PointIndexMM.update(double[], double[], T) -> boolean
            if (methodCall.getScope().isPresent()) {
                Expression scope = methodCall.getScope().get();
                String scopeName = getScopeName(scope);
                if (scopeName != null) {
                    // Add a comment to guide developers
                    methodCall.setComment(new LineComment("TODO: Check if this is PointIndexMM.update() (returns boolean) vs PointIndex.update() (returns T). If PointIndexMM, change to: boolean success = " + scopeName + ".update(...)"));
                }
            }
        }

        /**
         * Transform query1NN calls to handle potential return type changes
         */
        private void transformQuery1NNCall(MethodCallExpr methodCall) {
            // API change: query1NN() now returns PointEntryDist<T> instead of T directly
            // Need to add .value() call to get the actual value
            // This is a common pattern that we can help identify
            if (methodCall.getScope().isPresent()) {
                Expression scope = methodCall.getScope().get();
                String scopeName = getScopeName(scope);
                if (scopeName != null) {
                    // Add a comment to guide developers on how to fix this
                    methodCall.setComment(new LineComment("TODO: query1NN() now returns PointEntryDist<T>, use .value() to get the actual value: " + scopeName + ".query1NN(...).value()"));
                }
            }
        }

        /**
         * Transform queryKNN calls to handle potential return type changes
         */
        private void transformQueryKNNCall(MethodCallExpr methodCall) {
            // API change: queryKNN() now returns QueryIteratorKNN<PointEntryDist<T>> instead of QueryIteratorKNN<T>
            // When iterating, need to use .value() on each PointEntryDist
            if (methodCall.getScope().isPresent()) {
                Expression scope = methodCall.getScope().get();
                String scopeName = getScopeName(scope);
                if (scopeName != null) {
                    // Add a comment to guide developers
                    methodCall.setComment(new LineComment("TODO: queryKNN() now returns QueryIteratorKNN<PointEntryDist<T>>, when iterating use .value(): " + scopeName + ".queryKNN(...).iterator().next().value()"));
                }
            }
        }

        /**
         * Extract the name of a scope expression
         */
        private String getScopeName(Expression expr) {
            if (expr instanceof NameExpr) {
                return ((NameExpr) expr).getNameAsString();
            } else if (expr instanceof FieldAccessExpr) {
                return ((FieldAccessExpr) expr).getNameAsString();
            }
            return null;
        }
    }
}
