package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.ConstructorDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

public class Main {
    private static final String OLD_CONNECTOR = "SelectChannelConnector";
    private static final String NEW_CONNECTOR = "ServerConnector";
    private static final String SERVER_TYPE = "org.eclipse.jetty.server.Server";
    private static final String NEW_CONNECTOR_IMPORT = "org.eclipse.jetty.server.ServerConnector";

    public static void main(String[] args) throws Exception {
        if (args.length != 1) {
            throw new IllegalArgumentException("Expected a single source directory argument");
        }

        Path root = Paths.get(args[0]);
        if (!Files.isDirectory(root)) {
            throw new IllegalArgumentException("Source directory does not exist: " + root);
        }

        try (Stream<Path> paths = Files.walk(root)) {
            paths.filter(p -> p.toString().endsWith(".java"))
                    .sorted(Comparator.naturalOrder())
                    .forEach(Main::transformFile);
        }
    }

    private static void transformFile(Path file) {
        try {
            CompilationUnit cu = StaticJavaParser.parse(file, StandardCharsets.UTF_8);
            boolean changed = new ConnectorMigrationVisitor().visitAndTransform(cu);

            if (changed) {
                cu.getImports().removeIf(i -> i.getNameAsString().equals("org.eclipse.jetty.server.nio.SelectChannelConnector"));
                cu.addImport(NEW_CONNECTOR_IMPORT);
                Files.write(file, cu.toString().getBytes(StandardCharsets.UTF_8));
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to transform " + file, e);
        }
    }

    private static final class ConnectorMigrationVisitor extends VoidVisitorAdapter<Void> {
        private boolean changed;

        boolean visitAndTransform(CompilationUnit cu) {
            visit(cu, null);
            return changed;
        }

        @Override
        public void visit(ObjectCreationExpr expr, Void arg) {
            super.visit(expr, arg);

            if (!expr.getType().getNameAsString().equals(OLD_CONNECTOR)) {
                return;
            }

            Optional<Expression> serverExpression = findServerExpression(expr);
            if (!serverExpression.isPresent()) {
                return;
            }

            expr.setType(StaticJavaParser.parseClassOrInterfaceType(NEW_CONNECTOR));
            if (expr.getArguments().isEmpty()) {
                expr.addArgument(serverExpression.get());
            }
            changed = true;
        }

        @Override
        public void visit(ClassOrInterfaceType type, Void arg) {
            super.visit(type, arg);

            if (type.getNameAsString().equals(OLD_CONNECTOR)) {
                type.setName(NEW_CONNECTOR);
                changed = true;
            }
        }

        private Optional<Expression> findServerExpression(ObjectCreationExpr expr) {
            Optional<ConstructorDeclaration> constructor = expr.findAncestor(ConstructorDeclaration.class);
            if (constructor.isPresent()) {
                Optional<Expression> inConstructor = findInParameters(constructor.get().getParameters());
                if (inConstructor.isPresent()) {
                    return inConstructor;
                }
            }

            Optional<MethodDeclaration> method = expr.findAncestor(MethodDeclaration.class);
            if (method.isPresent()) {
                Optional<Expression> inMethod = findInParameters(method.get().getParameters());
                if (inMethod.isPresent()) {
                    return inMethod;
                }
            }

            Optional<ClassOrInterfaceDeclaration> clazz = expr.findAncestor(ClassOrInterfaceDeclaration.class);
            if (clazz.isPresent()) {
                Optional<Expression> inFields = findInFields(clazz.get().getFields());
                if (inFields.isPresent()) {
                    return inFields;
                }
            }

            return expr.findAncestor(CompilationUnit.class)
                    .flatMap(cu -> cu.findAll(VariableDeclarator.class).stream()
                            .filter(v -> isServerType(v.getType().asString()))
                            .filter(v -> v.getBegin().isPresent() && expr.getBegin().isPresent()
                                    && v.getBegin().get().isBefore(expr.getBegin().get()))
                            .map(v -> (Expression) new NameExpr(v.getNameAsString()))
                            .findFirst());
        }

        private Optional<Expression> findInParameters(List<Parameter> parameters) {
            return parameters.stream()
                    .filter(p -> isServerType(p.getType().asString()))
                    .map(p -> (Expression) new NameExpr(p.getNameAsString()))
                    .findFirst();
        }

        private Optional<Expression> findInFields(List<FieldDeclaration> fields) {
            return fields.stream()
                    .flatMap(field -> field.getVariables().stream())
                    .filter(v -> isServerType(v.getType().asString()))
                    .map(v -> (Expression) new NameExpr(v.getNameAsString()))
                    .findFirst();
        }

        private boolean isServerType(String typeName) {
            return SERVER_TYPE.equals(typeName) || typeName.endsWith(".Server");
        }
    }
}
