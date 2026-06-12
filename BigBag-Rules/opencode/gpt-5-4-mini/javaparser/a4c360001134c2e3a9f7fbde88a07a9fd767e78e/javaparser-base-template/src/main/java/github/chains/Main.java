package github.chains;

import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.resolution.declarations.ResolvedMethodDeclaration;
import com.github.javaparser.resolution.types.ResolvedReferenceType;
import com.github.javaparser.resolution.types.ResolvedType;
import com.github.javaparser.symbolsolver.JavaSymbolSolver;
import com.github.javaparser.symbolsolver.javaparsermodel.JavaParserFacade;
import com.github.javaparser.symbolsolver.resolution.typesolvers.CombinedTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.JarTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.JavaParserTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.ReflectionTypeSolver;
import com.github.javaparser.resolution.TypeSolver;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class Main {
    private static final String FILTER_BUILDER = "org.reflections.util.FilterBuilder";
    private static final String REFLECTIONS_JAR = System.getProperty("user.home")
            + "/.m2/repository/org/reflections/reflections/0.10.2/reflections-0.10.2.jar";

    public static void main(String[] args) throws IOException {
        if (args.length == 0) {
            throw new IllegalArgumentException("Expected a source root path");
        }

        Path sourceRoot = Paths.get(args[0]).toAbsolutePath().normalize();
        CombinedTypeSolver typeSolver = new CombinedTypeSolver();
        typeSolver.add(new ReflectionTypeSolver());
        typeSolver.add(new JavaParserTypeSolver(sourceRoot));
        Path jarPath = Paths.get(REFLECTIONS_JAR);
        if (Files.exists(jarPath)) {
            typeSolver.add(new JarTypeSolver(jarPath));
        }

        StaticJavaParser.setConfiguration(new ParserConfiguration().setSymbolResolver(new JavaSymbolSolver(typeSolver)));

        Files.walk(sourceRoot)
                .filter(path -> path.toString().endsWith(".java"))
                .forEach(path -> transform(path, typeSolver));
    }

    private static void transform(Path path, TypeSolver typeSolver) {
        try {
            CompilationUnit compilationUnit = StaticJavaParser.parse(path);
            boolean changed = false;
            for (MethodCallExpr call : compilationUnit.findAll(MethodCallExpr.class)) {
                if (isFilterBuilderApply(call, typeSolver)) {
                    call.setName("test");
                    changed = true;
                }
            }
            if (changed) {
                Files.writeString(path, compilationUnit.toString(), StandardCharsets.UTF_8);
            }
        } catch (Exception ignored) {
            // Skip files that cannot be parsed or resolved.
        }
    }

    private static boolean isFilterBuilderApply(MethodCallExpr call, TypeSolver typeSolver) {
        if (!"apply".equals(call.getNameAsString()) || call.getArguments().size() != 1 || !call.getScope().isPresent()) {
            return false;
        }

        try {
            ResolvedMethodDeclaration resolvedMethod = call.resolve();
            if (!"apply".equals(resolvedMethod.getName())) {
                return false;
            }
            if (!FILTER_BUILDER.equals(resolvedMethod.declaringType().getQualifiedName())) {
                return false;
            }
            return true;
        } catch (Exception firstResolutionFailure) {
            try {
                ResolvedType scopeType = JavaParserFacade.get(typeSolver).getType(call.getScope().get());
                if (scopeType.isReferenceType()) {
                    ResolvedReferenceType referenceType = scopeType.asReferenceType();
                    return FILTER_BUILDER.equals(referenceType.getQualifiedName());
                }
            } catch (Exception ignored) {
                // fall through
            }
        }

        return false;
    }
}
