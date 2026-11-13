package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.type.Type;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

public class Main {
    private static final String OLD_COVERAGE_API = "org.pitest.coverage.CoverageDatabase#getClassInfo(java.util.Collection<org.pitest.classinfo.ClassName>)";
    private static final String NEW_COVERAGE_API = "org.pitest.coverage.CoverageDatabase#getTestsForClass(org.pitest.classinfo.ClassName)";

    public static void main(String[] args) {
        if (args.length != 1) {
            throw new IllegalArgumentException("Usage: Main <source-root>");
        }

        final Path sourceRoot = Paths.get(args[0]);
        try {
            Files.walk(sourceRoot)
                .filter(path -> Files.isRegularFile(path) && path.toString().endsWith(".java"))
                .forEach(Main::rewriteFile);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static void rewriteFile(Path file) {
        try {
            final CompilationUnit cu = StaticJavaParser.parse(file);
            boolean changed = false;
            changed |= rewriteDeprecatedCoverageLookup(cu);
            if (changed) {
                Files.writeString(file, cu.toString(), StandardCharsets.UTF_8);
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to rewrite " + file, e);
        }
    }

    private static boolean rewriteDeprecatedCoverageLookup(CompilationUnit cu) {
        final List<MethodCallExpr> calls = cu.findAll(MethodCallExpr.class);
        boolean changed = false;
        for (MethodCallExpr call : calls) {
            if (!"getClassInfo".equals(call.getNameAsString()) || call.getArguments().size() != 1) {
                continue;
            }

            final MethodCallExpr collectionCall = call.getArgument(0).asMethodCallExpr();
            if ("singleton".equals(collectionCall.getNameAsString()) && collectionCall.getArguments().size() == 1) {
                call.setName("getTestsForClass");
                call.setArgument(0, collectionCall.getArgument(0).clone());
                changed = true;
            }
        }
        return changed;
    }
}
