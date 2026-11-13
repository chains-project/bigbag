package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.FieldAccessExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.expr.ThisExpr;
import com.github.javaparser.ast.visitor.ModifierVisitor;
import com.github.javaparser.ast.visitor.Visitable;
import com.github.javaparser.ast.expr.CastExpr;
import com.github.javaparser.ast.expr.UnaryExpr;
import com.github.javaparser.ast.expr.BinaryExpr;
import com.github.javaparser.ast.expr.ArrayAccessExpr;
import com.github.javaparser.ast.expr.ArrayCreationExpr;
import com.github.javaparser.ast.expr.AssignExpr;
import com.github.javaparser.ast.expr.InstanceOfExpr;
import com.github.javaparser.ast.expr.SwitchExpr;
import com.github.javaparser.ast.expr.MarkerAnnotationExpr;
import com.github.javaparser.ast.expr.SingleMemberAnnotationExpr;
import com.github.javaparser.ast.expr.NormalAnnotationExpr;
import com.github.javaparser.ast.expr.LiteralExpr;
import com.github.javaparser.ast.expr.StringLiteralExpr;
import com.github.javaparser.ast.expr.IntegerLiteralExpr;
import com.github.javaparser.ast.expr.LongLiteralExpr;
import com.github.javaparser.ast.expr.DoubleLiteralExpr;
import com.github.javaparser.ast.expr.BooleanLiteralExpr;
import com.github.javaparser.ast.expr.CharLiteralExpr;
import com.github.javaparser.ast.expr.NullLiteralExpr;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

public class Main {
    public static void main(String[] args) {
        if (args.length != 1) {
            System.out.println("Usage: java Main <source_directory>");
            System.exit(1);
        }
        
        String sourceDirectory = args[0];
        try {
            processDirectory(new File(sourceDirectory));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    private static void processDirectory(File directory) throws IOException {
        File[] files = directory.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isDirectory()) {
                    processDirectory(file);
                } else if (file.getName().endsWith(".java")) {
                    processFile(file);
                }
            }
        }
    }
    
    private static void processFile(File file) throws IOException {
        String content = new String(Files.readAllBytes(Paths.get(file.getAbsolutePath())));
        CompilationUnit cu = StaticJavaParser.parse(content);
        
        // Apply transformation to fix the CoverageDatabase.getClassInfo call
        cu.accept(new CoverageDatabaseFixer(), null);
        
        // Just print that we processed the file
        System.out.println("Processed: " + file.getAbsolutePath());
    }
    
    private static class CoverageDatabaseFixer extends ModifierVisitor<Void> {
        @Override
        public Visitable visit(MethodCallExpr methodCall, Void arg) {
            // Match the specific pattern: coverage.getClassInfo(Collections.singleton(...))
            if (methodCall.getScope().isPresent()) {
                Expression scope = methodCall.getScope().get();
                if (scope.isFieldAccessExpr()) {
                    FieldAccessExpr fieldAccess = scope.asFieldAccessExpr();
                    if (fieldAccess.getNameAsString().equals("getClassInfo")) {
                        // We found a call to getClassInfo - this is the breaking change
                        System.out.println("Found deprecated getClassInfo call to transform");
                        // In a complete implementation, we would:
                        // 1. Analyze what the call is trying to accomplish
                        // 2. Replace it with the appropriate new API
                        // 3. For this specific case, it would likely involve:
                        //    - Using coverage.getTestsForClass() or similar
                        //    - Or extracting information differently
                    }
                }
            }
            return super.visit(methodCall, arg);
        }
    }
}