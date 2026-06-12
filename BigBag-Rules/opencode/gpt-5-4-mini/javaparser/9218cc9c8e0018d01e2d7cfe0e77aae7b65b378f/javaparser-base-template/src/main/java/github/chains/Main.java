package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.type.Type;
import com.github.javaparser.printer.lexicalpreservation.LexicalPreservingPrinter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

public class Main {
    private static final String OLD_PREFIX = "jakarta.validation";
    private static final String NEW_PREFIX = "javax.validation";

    public static void main(String[] args) {
        if (args.length != 1) {
            throw new IllegalArgumentException("Expected exactly one argument: source root directory");
        }

        Path sourceRoot = Paths.get(args[0]);
        if (!Files.isDirectory(sourceRoot)) {
            throw new IllegalArgumentException("Not a directory: " + sourceRoot);
        }

        try (Stream<Path> paths = Files.walk(sourceRoot)) {
            paths.filter(Files::isRegularFile)
                    .forEach(Main::rewriteFile);
        } catch (IOException e) {
            throw new RuntimeException("Failed to walk source tree", e);
        }
    }

    private static void rewriteFile(Path file) {
        try {
            if (file.getFileName().toString().equals("pom.xml")) {
                rewritePom(file);
                return;
            }

            if (!file.toString().endsWith(".java")) {
                return;
            }

            CompilationUnit compilationUnit = StaticJavaParser.parse(file, StandardCharsets.UTF_8);
            LexicalPreservingPrinter.setup(compilationUnit);

            boolean changed = false;
            changed |= rewriteImports(compilationUnit);
            changed |= rewriteTypedNodes(compilationUnit);
            changed |= rewriteAnnotationNodes(compilationUnit);
            if (changed) {
                Files.write(file, LexicalPreservingPrinter.print(compilationUnit).getBytes(StandardCharsets.UTF_8));
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to rewrite " + file, e);
        }
    }

    private static void rewritePom(Path file) throws IOException {
        String pom = Files.readString(file, StandardCharsets.UTF_8);
        String jakartaDependency = "\n\t\t<dependency>\n\t\t\t<groupId>jakarta.validation</groupId>\n\t\t\t<artifactId>jakarta.validation-api</artifactId>\n\t\t\t<version>3.0.2</version>\n\t\t\t<scope>provided</scope>\n\t\t</dependency>";
        String javaxDependency = "\n\t\t<dependency>\n\t\t\t<groupId>javax.validation</groupId>\n\t\t\t<artifactId>validation-api</artifactId>\n\t\t\t<version>2.0.1.Final</version>\n\t\t\t<scope>provided</scope>\n\t\t</dependency>";
        if (pom.contains(jakartaDependency)) {
            pom = pom.replace(jakartaDependency, javaxDependency);
            Files.writeString(file, pom, StandardCharsets.UTF_8);
        }
    }

    private static boolean rewriteImports(CompilationUnit compilationUnit) {
        boolean changed = false;
        for (int i = 0; i < compilationUnit.getImports().size(); i++) {
            var importDeclaration = compilationUnit.getImports().get(i);
            String name = importDeclaration.getNameAsString();
            if (name.startsWith(OLD_PREFIX)) {
                importDeclaration.setName(StaticJavaParser.parseName(name.replaceFirst("^javax\\.validation", NEW_PREFIX)));
                changed = true;
            }
        }
        return changed;
    }

    private static boolean rewriteTypedNodes(CompilationUnit compilationUnit) {
        boolean changed = false;
        List<Type> types = new ArrayList<>(compilationUnit.findAll(Type.class));
        for (Type type : types) {
            String rendered = type.toString();
            if (rendered.startsWith(OLD_PREFIX)) {
                type.replace(StaticJavaParser.parseType(rendered.replaceFirst("^javax\\.validation", NEW_PREFIX)));
                changed = true;
            }
        }

        List<Expression> expressions = new ArrayList<>(compilationUnit.findAll(Expression.class));
        for (Expression expression : expressions) {
            String rendered = expression.toString();
            if (rendered.startsWith(OLD_PREFIX)) {
                expression.replace(StaticJavaParser.parseExpression(rendered.replaceFirst("^javax\\.validation", NEW_PREFIX)));
                changed = true;
            }
        }

        return changed;
    }

    private static boolean rewriteAnnotationNodes(CompilationUnit compilationUnit) {
        boolean changed = false;
        List<AnnotationExpr> annotations = new ArrayList<>(compilationUnit.findAll(AnnotationExpr.class));
        for (AnnotationExpr annotation : annotations) {
            String rendered = annotation.getNameAsString();
            if (rendered.startsWith(OLD_PREFIX)) {
                annotation.setName(StaticJavaParser.parseName(rendered.replaceFirst("^javax\\.validation", NEW_PREFIX)));
                changed = true;
            }
        }
        return changed;
    }

}
