package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.body.CallableDeclaration;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.FieldAccessExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.expr.ThisExpr;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.visitor.ModifierVisitor;
import com.github.javaparser.printer.lexicalpreservation.LexicalPreservingPrinter;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class Main {
    public static void main(String[] args) throws IOException {
        if (args.length != 1) {
            throw new IllegalArgumentException("Usage: Main <source-root>");
        }

        transform(Paths.get(args[0]));
    }

    private static void transform(Path sourceRoot) throws IOException {
        try (Stream<Path> paths = Files.walk(sourceRoot)) {
            List<Path> javaFiles = paths
                    .filter(path -> Files.isRegularFile(path) && path.toString().endsWith(".java"))
                    .filter(path -> !path.toString().contains("/target/"))
                    .sorted(Comparator.naturalOrder())
                    .collect(Collectors.toList());

            for (Path javaFile : javaFiles) {
                CompilationUnit cu = StaticJavaParser.parse(javaFile);
                LexicalPreservingPrinter.setup(cu);
                cu.accept(new Jetty11MigrationVisitor(), null);
                Files.writeString(javaFile, LexicalPreservingPrinter.print(cu));
            }
        }
    }

    private static final class Jetty11MigrationVisitor extends ModifierVisitor<Void> {
        @Override
        public Node visit(ImportDeclaration n, Void arg) {
            String name = n.getNameAsString();
            if (name.startsWith("javax.servlet")) {
                n.setName(name.replaceFirst("^javax", "jakarta"));
            }
            if (name.equals("org.eclipse.jetty.server.nio.SelectChannelConnector")) {
                n.setName("org.eclipse.jetty.server.ServerConnector");
            }
            super.visit(n, arg);
            return n;
        }

        @Override
        public Node visit(ClassOrInterfaceType n, Void arg) {
            String text = n.asString();
            if (text.startsWith("javax.servlet")) {
                return StaticJavaParser.parseClassOrInterfaceType(text.replaceFirst("^javax", "jakarta"));
            }
            if (text.equals("SelectChannelConnector")) {
                return StaticJavaParser.parseClassOrInterfaceType("ServerConnector");
            }
            super.visit(n, arg);
            return n;
        }

        @Override
        public Node visit(ObjectCreationExpr n, Void arg) {
            String typeName = n.getType().asString();
            if (typeName.equals("SelectChannelConnector") || typeName.equals("org.eclipse.jetty.server.nio.SelectChannelConnector")) {
                Optional<Expression> serverName = findServerName(n);
                if (serverName.isPresent()) {
                    n.setType("ServerConnector");
                    n.setArguments(com.github.javaparser.ast.NodeList.nodeList(serverName.get()));
                }
            }
            super.visit(n, arg);
            return n;
        }

        private Optional<Expression> findServerName(Node node) {
            CallableDeclaration<?> callable = node.findAncestor(CallableDeclaration.class)
                    .map(cd -> (CallableDeclaration<?>) cd)
                    .orElse(null);
            if (callable != null) {
                for (Parameter parameter : callable.getParameters()) {
                    if (isServerType(parameter)) {
                        return Optional.of(new NameExpr(parameter.getNameAsString()));
                    }
                }
            }

            Optional<ClassOrInterfaceDeclaration> clazz = node.findAncestor(ClassOrInterfaceDeclaration.class);
            if (clazz.isPresent()) {
                for (FieldDeclaration field : clazz.get().getFields()) {
                    for (VariableDeclarator variable : field.getVariables()) {
                        if (isServerType(variable)) {
                            if (field.isStatic()) {
                                return Optional.of(new NameExpr(variable.getNameAsString()));
                            }
                            return Optional.of(new FieldAccessExpr(new ThisExpr(), variable.getNameAsString()));
                        }
                    }
                }
            }

            return Optional.empty();
        }

        private boolean isServerType(VariableDeclarator node) {
            String typeName = node.getType().asString();
            return typeName.equals("Server") || typeName.equals("org.eclipse.jetty.server.Server");
        }

        private boolean isServerType(Parameter node) {
            String typeName = node.getType().asString();
            return typeName.equals("Server") || typeName.equals("org.eclipse.jetty.server.Server");
        }
    }
}
