package github.chains;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.FieldAccessExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.expr.StringLiteralExpr;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
import com.github.javaparser.printer.lexicalpreservation.LexicalPreservingPrinter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.Optional;
import java.util.stream.Stream;

public class Main {
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: Main <source-root>");
            System.exit(1);
        }

        Path sourceRoot = Paths.get(args[0]);
        JavaParser parser = new JavaParser(new ParserConfiguration());

        try (Stream<Path> paths = Files.walk(sourceRoot)) {
            paths.filter(path -> Files.isRegularFile(path) && path.toString().endsWith(".java"))
                    .sorted(Comparator.naturalOrder())
                    .forEach(path -> transformFile(parser, path));
        } catch (IOException e) {
            throw new RuntimeException("Failed to traverse source root: " + sourceRoot, e);
        }
    }

    private static void transformFile(JavaParser parser, Path path) {
        try {
            Optional<CompilationUnit> maybeCu = parser.parse(path).getResult();
            if (!maybeCu.isPresent()) {
                return;
            }

            CompilationUnit cu = maybeCu.get();
            LexicalPreservingPrinter.setup(cu);
            BouncyCastleTlsAlertTransformer transformer = new BouncyCastleTlsAlertTransformer();
            transformer.visit(cu, null);
            boolean changed = transformer.changed;
            if (changed) {
                Files.write(path, LexicalPreservingPrinter.print(cu).getBytes(StandardCharsets.UTF_8));
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to transform " + path, e);
        }
    }

    private static final class BouncyCastleTlsAlertTransformer extends VoidVisitorAdapter<Void> {
        private boolean changed;

        @Override
        public void visit(CompilationUnit cu, Void arg) {
            changed |= cu.getImports().removeIf(importDecl -> {
                String name = importDecl.getNameAsString();
                return name.equals("org.bouncycastle.crypto.tls.AlertDescription")
                        || name.equals("org.bouncycastle.crypto.tls.TlsFatalAlert");
            });

            if (replaceTlsFatalAlertCreations(cu)) {
                changed = true;
            }

            if (changed && !cu.getImports().stream().anyMatch(i -> i.getNameAsString().equals("java.io.IOException"))) {
                cu.addImport(IOException.class);
            }
            super.visit(cu, arg);
        }

        private boolean replaceTlsFatalAlertCreations(CompilationUnit cu) {
            final boolean[] changed = {false};
            cu.findAll(ObjectCreationExpr.class).forEach(creation -> {
                if (!isTlsFatalAlert(creation)) {
                    return;
                }

                String message = creation.getArguments().isEmpty()
                        ? "Bouncy Castle TLS alert"
                        : alertMessage(creation.getArgument(0));
                ObjectCreationExpr replacement = new ObjectCreationExpr(
                        null,
                        new ClassOrInterfaceType(null, "IOException"),
                        NodeList.nodeList(new StringLiteralExpr(message)));
                creation.replace(replacement);
                changed[0] = true;
            });
            return changed[0];
        }

        private boolean isTlsFatalAlert(ObjectCreationExpr creation) {
            String typeName = creation.getType().asString();
            return typeName.equals("TlsFatalAlert") || typeName.endsWith(".TlsFatalAlert");
        }

        private String alertMessage(Expression expression) {
            if (expression.isFieldAccessExpr()) {
                FieldAccessExpr field = expression.asFieldAccessExpr();
                if (isAlertDescription(field)) {
                    return field.getNameAsString();
                }
            }
            return expression.toString();
        }

        private boolean isAlertDescription(FieldAccessExpr field) {
            Expression scope = field.getScope();
            return scope.isNameExpr() && scope.asNameExpr().getNameAsString().equals("AlertDescription");
        }
    }
}
