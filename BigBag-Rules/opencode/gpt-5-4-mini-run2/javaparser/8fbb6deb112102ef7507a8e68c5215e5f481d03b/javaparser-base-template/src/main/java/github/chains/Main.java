package github.chains;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ConstructorDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.FieldAccessExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.expr.ThisExpr;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.Range;
import com.github.javaparser.printer.lexicalpreservation.LexicalPreservingPrinter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class Main {
    private static final String OLD_CONNECTOR = "org.eclipse.jetty.server.nio.SelectChannelConnector";
    private static final String NEW_CONNECTOR = "org.eclipse.jetty.server.ServerConnector";
    private static final String OLD_SERVLET_PREFIX = "javax.servlet";
    private static final String NEW_SERVLET_PREFIX = "jakarta.servlet";
    private static final String SERVER_TYPE = "org.eclipse.jetty.server.Server";

    public static void main(String[] args) throws IOException {
        if (args.length == 0) {
            throw new IllegalArgumentException("Expected a source root directory");
        }

        Path root = Paths.get(args[0]);
        JavaParser parser = new JavaParser(new ParserConfiguration());

        try (Stream<Path> paths = Files.walk(root)) {
            List<Path> javaFiles = paths
                    .filter(p -> Files.isRegularFile(p) && p.toString().endsWith(".java"))
                    .collect(Collectors.toList());

            for (Path file : javaFiles) {
                transformFile(parser, file);
            }
        }
    }

    private static void transformFile(JavaParser parser, Path file) throws IOException {
        CompilationUnit cu = parser.parse(file).getResult().orElse(null);
        if (cu == null) {
            return;
        }

        LexicalPreservingPrinter.setup(cu);

        boolean changed = false;

        for (ImportDeclaration imp : cu.getImports()) {
            String name = imp.getNameAsString();
            if (OLD_CONNECTOR.equals(name)) {
                imp.setName(NEW_CONNECTOR);
                changed = true;
            } else if (name.startsWith(OLD_SERVLET_PREFIX)) {
                imp.setName(NEW_SERVLET_PREFIX + name.substring(OLD_SERVLET_PREFIX.length()));
                changed = true;
            }
        }

        for (ClassOrInterfaceType type : cu.findAll(ClassOrInterfaceType.class)) {
            if ("SelectChannelConnector".equals(type.getNameAsString())) {
                type.setName("ServerConnector");
                changed = true;
            }
        }

        for (ObjectCreationExpr expr : cu.findAll(ObjectCreationExpr.class)) {
            if ("SelectChannelConnector".equals(expr.getType().getNameAsString())) {
                expr.setType("ServerConnector");
                changed = true;
                if (expr.getArguments().isEmpty()) {
                    Optional<Expression> serverArg = inferServerArgument(expr);
                    if (serverArg.isPresent()) {
                        expr.addArgument(serverArg.get());
                        changed = true;
                    }
                }
            }
        }

        if (changed) {
            Files.write(file, LexicalPreservingPrinter.print(cu).getBytes(StandardCharsets.UTF_8));
        }
    }

    private static Optional<Expression> inferServerArgument(ObjectCreationExpr creation) {
        Optional<Node> scope = creation.findAncestor(ConstructorDeclaration.class).map(n -> (Node) n)
                .or(() -> creation.findAncestor(MethodDeclaration.class).map(n -> (Node) n))
                .or(() -> creation.findAncestor(com.github.javaparser.ast.body.InitializerDeclaration.class).map(n -> (Node) n))
                .or(() -> creation.findAncestor(com.github.javaparser.ast.body.TypeDeclaration.class).map(n -> (Node) n));

        if (!scope.isPresent()) {
            return Optional.empty();
        }

        Optional<Range> creationRange = creation.getRange();

        List<VariableDeclarator> locals = scope.get().findAll(VariableDeclarator.class).stream()
                .filter(v -> isServerType(v.getType()))
                .sorted(Comparator.comparing(v -> v.getRange().map(r -> r.begin).orElse(new com.github.javaparser.Position(0, 0))))
                .collect(Collectors.toList());

        VariableDeclarator variable = pickNearestBefore(creationRange, locals);
        if (variable != null) {
            return Optional.of(new NameExpr(variable.getNameAsString()));
        }

        List<FieldDeclaration> fields = scope.get().findAll(FieldDeclaration.class).stream()
                .filter(f -> f.getVariables().stream().anyMatch(v -> isServerType(v.getType())))
                .sorted(Comparator.comparing(f -> f.getRange().map(r -> r.begin).orElse(new com.github.javaparser.Position(0, 0))))
                .collect(Collectors.toList());

        FieldDeclaration field = pickNearestBeforeField(creationRange, fields);
        if (field != null) {
            String fieldName = field.getVariable(0).getNameAsString();
            return Optional.of(new FieldAccessExpr(new ThisExpr(), fieldName));
        }

        return Optional.empty();
    }

    private static VariableDeclarator pickNearestBefore(Optional<Range> creationRange, List<VariableDeclarator> variables) {
        if (variables.isEmpty()) {
            return null;
        }
        VariableDeclarator variable = variables.get(variables.size() - 1);
        if (creationRange.isPresent()) {
            for (int i = variables.size() - 1; i >= 0; i--) {
                VariableDeclarator candidate = variables.get(i);
                if (!candidate.getRange().isPresent() || candidate.getRange().get().begin.isBefore(creationRange.get().begin)) {
                    variable = candidate;
                    break;
                }
            }
        }
        return variable;
    }

    private static FieldDeclaration pickNearestBeforeField(Optional<Range> creationRange, List<FieldDeclaration> fields) {
        if (fields.isEmpty()) {
            return null;
        }
        FieldDeclaration field = fields.get(fields.size() - 1);
        if (creationRange.isPresent()) {
            for (int i = fields.size() - 1; i >= 0; i--) {
                FieldDeclaration candidate = fields.get(i);
                if (!candidate.getRange().isPresent() || candidate.getRange().get().begin.isBefore(creationRange.get().begin)) {
                    field = candidate;
                    break;
                }
            }
        }
        return field;
    }

    private static boolean isServerType(com.github.javaparser.ast.type.Type type) {
        if (type.isClassOrInterfaceType()) {
            ClassOrInterfaceType cit = type.asClassOrInterfaceType();
            String name = cit.getNameAsString();
            if ("Server".equals(name) || SERVER_TYPE.equals(cit.getNameWithScope())) {
                return true;
            }
        }
        return false;
    }
}
