package github.chains;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.visitor.ModifierVisitor;
import com.github.javaparser.printer.lexicalpreservation.LexicalPreservingPrinter;
import com.github.javaparser.resolution.declarations.ResolvedMethodDeclaration;
import com.github.javaparser.symbolsolver.JavaSymbolSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.CombinedTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.JavaParserTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.ReflectionTypeSolver;

public class Main {

    private static final String TINSPIN_PREFIX = "org.tinspin.index.";

    public static void main(String[] args) {
        if (args.length == 0) {
            throw new IllegalArgumentException("Usage: Main <source-root>");
        }

        Path sourceRoot = Paths.get(args[0]).toAbsolutePath().normalize();
        JavaParser parser = createParser(sourceRoot);

        try {
            Files.walk(sourceRoot)
                    .filter(p -> p.toString().endsWith(".java"))
                    .forEach(path -> transformFile(parser, path));
        } catch (IOException e) {
            throw new RuntimeException("Failed to walk source tree: " + sourceRoot, e);
        }
    }

    private static JavaParser createParser(Path sourceRoot) {
        CombinedTypeSolver typeSolver = new CombinedTypeSolver();
        typeSolver.add(new ReflectionTypeSolver());
        typeSolver.add(new JavaParserTypeSolver(sourceRoot));

        ParserConfiguration configuration = new ParserConfiguration()
                .setSymbolResolver(new JavaSymbolSolver(typeSolver));
        return new JavaParser(configuration);
    }

    private static void transformFile(JavaParser parser, Path path) {
        try {
            parser.parse(path).getResult().ifPresent(cu -> {
                LexicalPreservingPrinter.setup(cu);
                TinspinVisitor visitor = new TinspinVisitor();
                visitor.visit(cu, null);
                if (visitor.changed) {
                    try {
                        Files.writeString(path, LexicalPreservingPrinter.print(cu));
                    } catch (IOException e) {
                        throw new RuntimeException("Failed to write transformed file: " + path, e);
                    }
                }
            });
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse file: " + path, e);
        }
    }

    private static final class TinspinVisitor extends ModifierVisitor<Void> {
        private boolean changed;
        private final Map<String, String> renames = new HashMap<>();

        private TinspinVisitor() {
            renames.put("nnQuery", "query1NN");
            renames.put("knnQuery", "queryKNN");
            renames.put("queryNearest", "query1NN");
            renames.put("nearestNeighbor", "query1NN");
        }

        @Override
        public Node visit(MethodCallExpr n, Void arg) {
            super.visit(n, arg);

            String replacement = renames.get(n.getNameAsString());
            if (replacement == null) {
                return n;
            }

            if (isTinspinCall(n)) {
                n.setName(replacement);
                changed = true;
            }
            return n;
        }

        private boolean isTinspinCall(MethodCallExpr call) {
            try {
                ResolvedMethodDeclaration resolved = call.resolve();
                String typeName = resolved.declaringType().getQualifiedName();
                return typeName.startsWith(TINSPIN_PREFIX);
            } catch (RuntimeException e) {
                return false;
            }
        }
    }
}
