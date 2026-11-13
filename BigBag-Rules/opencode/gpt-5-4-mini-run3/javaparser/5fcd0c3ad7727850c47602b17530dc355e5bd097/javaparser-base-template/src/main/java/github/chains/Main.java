package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.printer.lexicalpreservation.LexicalPreservingPrinter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class Main {
    private static final String OLD_CLASS_INFO = "org.pitest.classinfo.ClassInfo";
    private static final String NEW_CLASS_NAME = "org.pitest.classinfo.ClassName";

    public static void main(String[] args) {
        if (args.length != 1) {
            throw new IllegalArgumentException("Expected one argument: source root");
        }

        final Path root = Paths.get(args[0]);
        try {
            Files.walk(root)
                .filter(p -> p.toString().endsWith(".java"))
                .forEach(Main::transformFile);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static void transformFile(Path path) {
        try {
            CompilationUnit cu = StaticJavaParser.parse(path);
            LexicalPreservingPrinter.setup(cu);

            boolean changed = false;
            changed |= replaceClassInfoWithClassName(cu);
            changed |= rewriteGetNameAsJavaName(cu);
            changed |= rewriteRemovedCoverageGetClassInfo(cu);

            if (changed) {
                Files.writeString(path, LexicalPreservingPrinter.print(cu), StandardCharsets.UTF_8);
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to transform " + path, e);
        }
    }

    private static boolean replaceClassInfoWithClassName(CompilationUnit cu) {
        boolean changed = false;

        for (ImportDeclaration importDeclaration : cu.getImports()) {
            if (importDeclaration.getNameAsString().equals(OLD_CLASS_INFO)) {
                importDeclaration.setName(NEW_CLASS_NAME);
                changed = true;
            }
        }

        for (ClassOrInterfaceType type : cu.findAll(ClassOrInterfaceType.class)) {
            if (type.getNameAsString().equals("ClassInfo")) {
                type.setName("ClassName");
                changed = true;
            }
        }

        return changed;
    }

    private static boolean rewriteGetNameAsJavaName(CompilationUnit cu) {
        boolean changed = false;
        for (MethodCallExpr asJavaName : cu.findAll(MethodCallExpr.class)) {
            if (!asJavaName.getNameAsString().equals("asJavaName") || asJavaName.getScope().isEmpty()) {
                continue;
            }

            Expression scope = asJavaName.getScope().get();
            if (scope.isMethodCallExpr() && scope.asMethodCallExpr().getNameAsString().equals("getName")) {
                asJavaName.setScope(scope.asMethodCallExpr().getScope().orElse(scope));
                changed = true;
            }
        }
        return changed;
    }

    private static boolean rewriteRemovedCoverageGetClassInfo(CompilationUnit cu) {
        boolean changed = false;
        for (MethodCallExpr call : cu.findAll(MethodCallExpr.class)) {
            if (!call.getNameAsString().equals("getClassInfo") || call.getArguments().size() != 1) {
                continue;
            }

            call.replace(call.getArgument(0));
            changed = true;
        }
        return changed;
    }
}
