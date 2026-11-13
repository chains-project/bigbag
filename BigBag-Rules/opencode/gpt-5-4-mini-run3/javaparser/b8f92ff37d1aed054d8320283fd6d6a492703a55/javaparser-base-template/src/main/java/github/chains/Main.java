package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.expr.StringLiteralExpr;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Stream;

public class Main {
    private static final String TARGET_TYPE = "org.apache.maven.surefire.api.testset.TestListResolver";
    private static final String TARGET_METHOD = "getWildcard";
    private static final String REPLACEMENT_LITERAL = "*.class";

    public static void main(String[] args) {
        if (args.length != 1) {
            throw new IllegalArgumentException("Expected exactly one argument: the source root to transform");
        }

        Path sourceRoot = Paths.get(args[0]);
        if (!Files.isDirectory(sourceRoot)) {
            throw new IllegalArgumentException("Source root does not exist or is not a directory: " + sourceRoot);
        }

        try (Stream<Path> paths = Files.walk(sourceRoot)) {
            paths.filter(path -> path.toString().endsWith(".java"))
                    .forEach(Main::rewriteFile);
        } catch (IOException e) {
            throw new RuntimeException("Failed to traverse source root: " + sourceRoot, e);
        }
    }

    private static void rewriteFile(Path file) {
        try {
            CompilationUnit compilationUnit = StaticJavaParser.parse(file);
            List<MethodCallExpr> calls = compilationUnit.findAll(MethodCallExpr.class);

            boolean changed = false;
            for (MethodCallExpr call : calls) {
                if (isTargetWildcardCall(call, compilationUnit)) {
                    call.replace(new ObjectCreationExpr(
                            null,
                            StaticJavaParser.parseClassOrInterfaceType(TARGET_TYPE),
                            NodeList.nodeList(new StringLiteralExpr(REPLACEMENT_LITERAL))));
                    changed = true;
                }
            }

            if (changed) {
                Files.writeString(file, compilationUnit.toString(), StandardCharsets.UTF_8);
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to rewrite file: " + file, e);
        }
    }

    private static boolean isTargetWildcardCall(MethodCallExpr call, CompilationUnit compilationUnit) {
        if (!TARGET_METHOD.equals(call.getNameAsString()) || !call.getArguments().isEmpty()) {
            return false;
        }

        if (call.getScope().isPresent()) {
            String scope = call.getScope().get().toString();
            return TARGET_TYPE.equals(scope)
                    || "TestListResolver".equals(scope)
                    || scope.endsWith(".TestListResolver");
        }

        return hasRelevantImport(compilationUnit);
    }

    private static boolean hasRelevantImport(CompilationUnit compilationUnit) {
        for (ImportDeclaration importDeclaration : compilationUnit.getImports()) {
            if (importDeclaration.isStatic()) {
                String imported = importDeclaration.getNameAsString();
                if ((TARGET_TYPE + "." + TARGET_METHOD).equals(imported) || TARGET_TYPE.equals(imported)) {
                    return true;
                }
            } else if (TARGET_TYPE.equals(importDeclaration.getNameAsString())) {
                return true;
            }
        }
        return false;
    }

}
