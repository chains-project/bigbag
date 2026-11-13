package github.chains;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.symbolsolver.JavaSymbolSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.CombinedTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.JavaParserTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.ReflectionTypeSolver;
import com.github.javaparser.resolution.UnsolvedSymbolException;
import com.github.javaparser.resolution.declarations.ResolvedValueDeclaration;
import com.github.javaparser.resolution.types.ResolvedType;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;
import java.util.stream.Stream;

public class Main {
    public static void main(String[] args) throws IOException {
        if (args.length == 0) {
            throw new IllegalArgumentException("Usage: Main <source-root>");
        }

        Path sourceRoot = Paths.get(args[0]);
        if (!Files.isDirectory(sourceRoot)) {
            throw new IllegalArgumentException("Source root does not exist: " + sourceRoot);
        }

        CombinedTypeSolver typeSolver = new CombinedTypeSolver();
        typeSolver.add(new ReflectionTypeSolver());
        typeSolver.add(new JavaParserTypeSolver(sourceRoot));

        ParserConfiguration configuration = new ParserConfiguration()
                .setSymbolResolver(new JavaSymbolSolver(typeSolver));
        JavaParser parser = new JavaParser(configuration);

        try (Stream<Path> paths = Files.walk(sourceRoot)) {
            paths.filter(path -> path.toString().endsWith(".java"))
                    .forEach(path -> rewriteFile(parser, path));
        }
    }

    private static void rewriteFile(JavaParser parser, Path path) {
        try {
            CompilationUnit compilationUnit = parser.parse(path).getResult().orElse(null);
            if (compilationUnit == null) {
                return;
            }

            boolean changed = false;
            for (MethodCallExpr call : compilationUnit.findAll(MethodCallExpr.class)) {
                if (!isTargetWriteValueCall(call)) {
                    continue;
                }

                Expression target = call.getScope().orElse(null);
                if (target == null) {
                    continue;
                }

                Expression fileArgument = call.getArgument(0).clone();
                Expression valueArgument = call.getArgument(1).clone();

                Expression replacement = StaticJavaParser.parseExpression(
                        "java.nio.file.Files.write(" + fileArgument + ".toPath(), "
                                + target + ".writeValueAsBytes(" + valueArgument + "))");
                call.replace(replacement);
                changed = true;
            }

            if (changed) {
                Files.write(path, compilationUnit.toString().getBytes(StandardCharsets.UTF_8));
            }
        } catch (IOException | RuntimeException e) {
            throw new RuntimeException("Failed to rewrite " + path, e);
        }
    }

    private static boolean isTargetWriteValueCall(MethodCallExpr call) {
        if (!"writeValue".equals(call.getNameAsString()) || call.getArguments().size() != 2) {
            return false;
        }

        try {
            ResolvedType resolvedType = call.getArgument(0).calculateResolvedType();
            if (!resolvedType.isReferenceType()) {
                return false;
            }
            return "java.io.File".equals(resolvedType.asReferenceType().getQualifiedName());
        } catch (UnsolvedSymbolException | UnsupportedOperationException | IllegalStateException e) {
            return false;
        }
    }
}
