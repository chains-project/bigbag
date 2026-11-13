package github.chains;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.FieldAccessExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.resolution.declarations.ResolvedReferenceTypeDeclaration;
import com.github.javaparser.resolution.types.ResolvedType;
import com.github.javaparser.symbolsolver.JavaSymbolSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.CombinedTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.JavaParserTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.ReflectionTypeSolver;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

public class Main {

    private static final Set<String> INSTANCE_BASED_TYPES = new HashSet<>(Arrays.asList(
            "org.jvnet.jaxb2_commons.lang.DefaultCopyStrategy",
            "org.jvnet.jaxb2_commons.lang.DefaultEqualsStrategy",
            "org.jvnet.jaxb2_commons.lang.DefaultHashCodeStrategy",
            "org.jvnet.jaxb2_commons.lang.DefaultMergeStrategy",
            "org.jvnet.jaxb2_commons.lang.DefaultToStringStrategy",
            "org.jvnet.jaxb2_commons.lang.JAXBCopyStrategy",
            "org.jvnet.jaxb2_commons.lang.JAXBEqualsStrategy",
            "org.jvnet.jaxb2_commons.lang.JAXBHashCodeStrategy",
            "org.jvnet.jaxb2_commons.lang.JAXBMergeCollectionsStrategy",
            "org.jvnet.jaxb2_commons.lang.JAXBMergeStrategy",
            "org.jvnet.jaxb2_commons.lang.JAXBToStringStrategy"
    ));

    public static void main(String[] args) throws IOException {
        if (args.length != 1) {
            throw new IllegalArgumentException("Expected exactly one argument: the source root to rewrite");
        }

        Path sourceRoot = Paths.get(args[0]).toAbsolutePath().normalize();
        if (!Files.isDirectory(sourceRoot)) {
            throw new IllegalArgumentException("Source root does not exist or is not a directory: " + sourceRoot);
        }

        CombinedTypeSolver typeSolver = new CombinedTypeSolver();
        typeSolver.add(new ReflectionTypeSolver(false));
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
            List<MethodCallExpr> calls = compilationUnit.findAll(MethodCallExpr.class);
            for (MethodCallExpr call : calls) {
                if (!"getInstance".equals(call.getNameAsString()) || !call.getArguments().isEmpty() || !call.getScope().isPresent()) {
                    continue;
                }

                Expression scope = call.getScope().get();
                if (!isKnownStrategyType(scope)) {
                    continue;
                }

                call.replace(new FieldAccessExpr(scope.clone(), "INSTANCE"));
                changed = true;
            }

            if (changed) {
                Files.write(path, compilationUnit.toString().getBytes(StandardCharsets.UTF_8));
            }
        } catch (Exception ex) {
            throw new RuntimeException("Failed to rewrite " + path + ": " + ex.getMessage(), ex);
        }
    }

    private static boolean isKnownStrategyType(Expression scope) {
        if (isKnownStrategyName(scope.toString())) {
            return true;
        }

        try {
            ResolvedType resolvedType = scope.calculateResolvedType();
            if (resolvedType.isReferenceType()) {
                ResolvedReferenceTypeDeclaration declaration = resolvedType.asReferenceType().getTypeDeclaration().orElse(null);
                return declaration != null && INSTANCE_BASED_TYPES.contains(declaration.getQualifiedName());
            }
        } catch (Exception ignored) {
            // Fall back to string-based checks when resolution is unavailable.
        }

        String text = scope.toString();
        return isKnownStrategyName(text);
    }

    private static boolean isKnownStrategyName(String text) {
        return INSTANCE_BASED_TYPES.stream()
                .map(type -> type.substring(type.lastIndexOf('.') + 1))
                .anyMatch(text::equals);
    }
}
