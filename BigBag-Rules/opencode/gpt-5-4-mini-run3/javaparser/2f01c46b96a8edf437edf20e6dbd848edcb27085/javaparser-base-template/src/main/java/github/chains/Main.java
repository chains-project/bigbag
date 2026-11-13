package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.CastExpr;
import com.github.javaparser.ast.expr.MethodReferenceExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.TypeExpr;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.printer.lexicalpreservation.LexicalPreservingPrinter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.stream.Stream;

public class Main {
    private static final String OLD_TYPE = "org.codehaus.plexus.util.xml.Xpp3Dom";
    private static final String NEW_TYPE = "org.codehaus.plexus.configuration.PlexusConfiguration";
    private static final String OLD_SIMPLE_NAME = simpleName(OLD_TYPE);
    private static final String NEW_SIMPLE_NAME = simpleName(NEW_TYPE);

    public static void main(String[] args) {
        StaticJavaParser.getParserConfiguration().setLanguageLevel(ParserConfiguration.LanguageLevel.JAVA_17);
        Path sourceRoot = Paths.get(args.length > 0 ? args[0] : ".");
        try (Stream<Path> paths = Files.walk(sourceRoot)) {
            paths.filter(path -> Files.isRegularFile(path) && path.toString().endsWith(".java"))
                    .sorted(Comparator.naturalOrder())
                    .forEach(Main::rewriteFile);
        } catch (IOException exception) {
            throw new RuntimeException("Failed to transform sources under " + sourceRoot, exception);
        }
    }

    private static void rewriteFile(Path path) {
        try {
            CompilationUnit compilationUnit = StaticJavaParser.parse(path);
            LexicalPreservingPrinter.setup(compilationUnit);

            replaceImports(compilationUnit);
            replaceTypes(compilationUnit);
            replaceMethodReferences(compilationUnit);

            Files.writeString(path, LexicalPreservingPrinter.print(compilationUnit));
        } catch (IOException exception) {
            throw new RuntimeException("Failed to rewrite " + path, exception);
        }
    }

    private static void replaceImports(CompilationUnit compilationUnit) {
        compilationUnit.getImports().stream()
                .filter(importDeclaration -> importDeclaration.getNameAsString().equals(OLD_TYPE))
                .forEach(importDeclaration -> importDeclaration.setName(NEW_TYPE));
    }

    private static void replaceTypes(CompilationUnit compilationUnit) {
        compilationUnit.findAll(ClassOrInterfaceType.class).forEach(type -> {
            if (matchesOldType(type)) {
                type.replace(StaticJavaParser.parseClassOrInterfaceType(NEW_TYPE));
            }
        });

        compilationUnit.findAll(CastExpr.class).forEach(castExpr -> {
            if (matchesOldType(castExpr.getType())) {
                castExpr.setType(StaticJavaParser.parseType(NEW_TYPE));
            }
        });
    }

    private static void replaceMethodReferences(CompilationUnit compilationUnit) {
        compilationUnit.findAll(MethodReferenceExpr.class).forEach(methodReferenceExpr -> {
            if (methodReferenceExpr.getScope() instanceof NameExpr) {
                NameExpr scope = (NameExpr) methodReferenceExpr.getScope();
                if (scope.getNameAsString().equals(OLD_SIMPLE_NAME)) {
                    methodReferenceExpr.setScope(new NameExpr(NEW_SIMPLE_NAME));
                }
            } else if (methodReferenceExpr.getScope() instanceof TypeExpr) {
                TypeExpr scope = (TypeExpr) methodReferenceExpr.getScope();
                if (matchesOldType(scope.getType())) {
                    scope.setType(StaticJavaParser.parseType(NEW_TYPE));
                }
            }
        });
    }

    private static boolean matchesOldType(ClassOrInterfaceType type) {
        return type.asString().equals(OLD_TYPE) || type.getNameAsString().equals(OLD_SIMPLE_NAME);
    }

    private static boolean matchesOldType(com.github.javaparser.ast.type.Type type) {
        return type.asString().equals(OLD_TYPE) || type.asString().equals(OLD_SIMPLE_NAME);
    }

    private static String simpleName(String fullyQualifiedName) {
        int lastDot = fullyQualifiedName.lastIndexOf('.');
        return lastDot == -1 ? fullyQualifiedName : fullyQualifiedName.substring(lastDot + 1);
    }
}
