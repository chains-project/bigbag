package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.FieldAccessExpr;
import com.github.javaparser.ast.expr.Name;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.visitor.ModifierVisitor;
import com.github.javaparser.ast.visitor.Visitable;
import com.github.javaparser.printer.PrettyPrinter;
import com.github.javaparser.printer.configuration.PrettyPrinterConfiguration;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Stream;

public class Main {
    private static final String OLD_PACKAGE = "com.squareup.okio";
    private static final String NEW_PACKAGE = "okio";

    public static void main(String[] args) throws IOException {
        if (args.length != 1) {
            throw new IllegalArgumentException("Expected a single source root path argument");
        }

        Path sourceRoot = Paths.get(args[0]);
        try (Stream<Path> paths = Files.walk(sourceRoot)) {
            paths.filter(path -> Files.isRegularFile(path)).forEach(Main::rewriteFile);
        }
    }

    private static void rewriteFile(Path path) {
        try {
            if (path.getFileName().toString().equals("pom.xml")) {
                rewritePom(path);
                return;
            }
            if (!path.toString().endsWith(".java")) {
                return;
            }
            CompilationUnit compilationUnit = StaticJavaParser.parse(path);
            compilationUnit.accept(new OkioPackageRenamer(), null);
            PrettyPrinterConfiguration configuration = new PrettyPrinterConfiguration();
            configuration.setPrintComments(true);
            PrettyPrinter prettyPrinter = new PrettyPrinter(configuration);
            Files.writeString(path, prettyPrinter.print(compilationUnit));
        } catch (RuntimeException ex) {
            // Skip files that use syntax outside this parser version.
        } catch (IOException ex) {
            throw new RuntimeException("Failed to rewrite " + path, ex);
        }
    }

    private static void rewritePom(Path path) throws IOException {
        String content = Files.readString(path);
        if (content.contains("<groupId>com.squareup.okio</groupId>")
            && content.contains("<artifactId>okio</artifactId>")) {
            content = content.replaceAll(
                "<kotlin\\.version>[^<]+</kotlin\\.version>",
                "<kotlin.version>1.8.22</kotlin.version>"
            );
            content = content.replaceAll(
                "<version>1\\.6\\.0</version>",
                "<version>1.8.22</version>"
            );
        }
        Files.writeString(path, content);
    }

    private static final class OkioPackageRenamer extends ModifierVisitor<Void> {
        @Override
        public ImportDeclaration visit(ImportDeclaration n, Void arg) {
            String name = n.getNameAsString();
            if (name.startsWith(OLD_PACKAGE)) {
                String rewritten = NEW_PACKAGE + name.substring(OLD_PACKAGE.length());
                n.setName(new Name(rewritten));
            }
            return (ImportDeclaration) super.visit(n, arg);
        }

        @Override
        public Visitable visit(ClassOrInterfaceType n, Void arg) {
            if (n.toString().contains(OLD_PACKAGE)) {
                return StaticJavaParser.parseClassOrInterfaceType(n.toString().replace(OLD_PACKAGE, NEW_PACKAGE));
            }
            return super.visit(n, arg);
        }

        @Override
        public Visitable visit(ObjectCreationExpr n, Void arg) {
            if (n.toString().contains(OLD_PACKAGE)) {
                return StaticJavaParser.parseExpression(n.toString().replace(OLD_PACKAGE, NEW_PACKAGE));
            }
            return super.visit(n, arg);
        }

        @Override
        public Visitable visit(MethodCallExpr n, Void arg) {
            if (n.toString().contains(OLD_PACKAGE)) {
                return StaticJavaParser.parseExpression(n.toString().replace(OLD_PACKAGE, NEW_PACKAGE));
            }
            return super.visit(n, arg);
        }

        @Override
        public Visitable visit(FieldAccessExpr n, Void arg) {
            if (n.toString().contains(OLD_PACKAGE)) {
                return StaticJavaParser.parseExpression(n.toString().replace(OLD_PACKAGE, NEW_PACKAGE));
            }
            return super.visit(n, arg);
        }

        @Override
        public Visitable visit(NameExpr n, Void arg) {
            if (n.toString().contains(OLD_PACKAGE)) {
                return StaticJavaParser.parseExpression(n.toString().replace(OLD_PACKAGE, NEW_PACKAGE));
            }
            return super.visit(n, arg);
        }
    }
}
