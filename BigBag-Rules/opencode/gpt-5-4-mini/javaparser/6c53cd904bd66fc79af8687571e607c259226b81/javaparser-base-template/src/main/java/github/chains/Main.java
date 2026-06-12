package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.expr.ClassExpr;
import com.github.javaparser.ast.visitor.ModifierVisitor;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

public class Main {
    private static final String OLD_FQCN = "org.apache.struts2.dispatcher.ng.filter.StrutsPrepareAndExecuteFilter";
    private static final String NEW_FQCN = "org.apache.struts2.dispatcher.filter.StrutsPrepareAndExecuteFilter";

    public static void main(String[] args) throws IOException {
        if (args.length < 1) {
            throw new IllegalArgumentException("Expected a source directory path");
        }

        Path sourceRoot = Path.of(args[0]);
        if (!Files.isDirectory(sourceRoot)) {
            throw new IllegalArgumentException("Not a directory: " + sourceRoot);
        }

        try (Stream<Path> paths = Files.walk(sourceRoot)) {
            paths.filter(path -> path.toString().endsWith(".java")).forEach(Main::rewrite);
        }
    }

    private static void rewrite(Path sourceFile) {
        try {
            CompilationUnit compilationUnit = StaticJavaParser.parse(sourceFile);
            Rewriter rewriter = new Rewriter();
            rewriter.visit(compilationUnit, null);

            if (rewriter.changed) {
                Files.writeString(sourceFile, compilationUnit.toString());
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to rewrite " + sourceFile, e);
        }
    }

    private static final class Rewriter extends ModifierVisitor<Void> {
        private boolean changed;

        @Override
        public Node visit(ImportDeclaration n, Void arg) {
            if (n.getNameAsString().equals(OLD_FQCN)) {
                changed = true;
                n.setName(NEW_FQCN);
            }
            return n;
        }

        @Override
        public Node visit(ClassOrInterfaceType n, Void arg) {
            if (n.asString().equals(OLD_FQCN)) {
                changed = true;
                n.replace(StaticJavaParser.parseType(NEW_FQCN));
            }
            return n;
        }

        @Override
        public Node visit(ClassExpr n, Void arg) {
            if (n.getType() instanceof ClassOrInterfaceType && n.getType().asString().equals(OLD_FQCN)) {
                changed = true;
                n.setType(StaticJavaParser.parseType(NEW_FQCN));
            }
            return n;
        }
    }
}
