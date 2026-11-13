package github.chains;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.ConstructorDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.printer.lexicalpreservation.LexicalPreservingPrinter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public class Main {
    private static final String OLD_SELECT_CHANNEL_CONNECTOR = "org.eclipse.jetty.server.nio.SelectChannelConnector";
    private static final String NEW_SERVER_CONNECTOR = "org.eclipse.jetty.server.ServerConnector";
    private static final String OLD_SERVLET_PREFIX = "javax.servlet";
    private static final String NEW_SERVLET_PREFIX = "jakarta.servlet";

    public static void main(String[] args) throws IOException {
        if (args.length != 1) {
            throw new IllegalArgumentException("Expected a single source directory argument");
        }

        Path root = Paths.get(args[0]);
        List<Path> files;
        try (var stream = Files.walk(root)) {
            files = stream
                    .filter(path -> path.toString().endsWith(".java"))
                    .sorted(Comparator.naturalOrder())
                    .collect(Collectors.toList());
        }

        JavaParser parser = new JavaParser(new ParserConfiguration());
        for (Path file : files) {
            String source = Files.readString(file, StandardCharsets.UTF_8);
            CompilationUnit cu = parser.parse(source).getResult()
                    .orElseThrow(() -> new IllegalStateException("Unable to parse " + file));

            LexicalPreservingPrinter.setup(cu);

            if (transform(cu)) {
                Files.writeString(file, LexicalPreservingPrinter.print(cu), StandardCharsets.UTF_8);
            }
        }
    }

    private static boolean transform(CompilationUnit cu) {
        boolean changed = false;

        changed |= updateImports(cu);
        changed |= updateTypes(cu);
        changed |= updateConnectorConstruction(cu);

        return changed;
    }

    private static boolean updateImports(CompilationUnit cu) {
        boolean changed = false;
        List<ImportDeclaration> imports = new ArrayList<ImportDeclaration>(cu.getImports());

        for (ImportDeclaration imp : imports) {
            String name = imp.getNameAsString();
            if (name.startsWith(OLD_SERVLET_PREFIX + ".")) {
                imp.setName(name.replace(OLD_SERVLET_PREFIX, NEW_SERVLET_PREFIX));
                changed = true;
            } else if (OLD_SELECT_CHANNEL_CONNECTOR.equals(name)) {
                imp.setName(NEW_SERVER_CONNECTOR);
                changed = true;
            }
        }

        ensureImport(cu, "jakarta.servlet.ServletException", containsAnyType(cu, "ServletException", OLD_SERVLET_PREFIX));
        ensureImport(cu, "jakarta.servlet.http.HttpServletRequest", containsAnyType(cu, "HttpServletRequest", OLD_SERVLET_PREFIX));
        ensureImport(cu, "jakarta.servlet.http.HttpServletResponse", containsAnyType(cu, "HttpServletResponse", OLD_SERVLET_PREFIX));
        ensureImport(cu, NEW_SERVER_CONNECTOR, containsAnyType(cu, "SelectChannelConnector", OLD_SELECT_CHANNEL_CONNECTOR));

        return changed;
    }

    private static void ensureImport(CompilationUnit cu, String fqcn, boolean needed) {
        if (needed && cu.getImports().stream().noneMatch(i -> i.getNameAsString().equals(fqcn))) {
            cu.addImport(fqcn);
        }
    }

    private static boolean containsAnyType(CompilationUnit cu, String simpleName, String fqcnPrefix) {
        return cu.findAll(ClassOrInterfaceType.class).stream().anyMatch(type -> {
            String text = type.asString();
            return simpleName.equals(type.getNameAsString()) || text.startsWith(fqcnPrefix + ".") || text.equals(fqcnPrefix) || text.equals(simpleName);
        });
    }

    private static boolean updateTypes(CompilationUnit cu) {
        boolean changed = false;

        for (ClassOrInterfaceType type : cu.findAll(ClassOrInterfaceType.class)) {
            String text = type.asString();
            if (text.equals("SelectChannelConnector") || text.equals(OLD_SELECT_CHANNEL_CONNECTOR) || text.endsWith(".SelectChannelConnector")) {
                type.replace(new ClassOrInterfaceType(null, "ServerConnector"));
                changed = true;
            } else if (text.startsWith(OLD_SERVLET_PREFIX + ".")) {
                type.replace(StaticJavaParser.parseClassOrInterfaceType(text.replace(OLD_SERVLET_PREFIX, NEW_SERVLET_PREFIX)));
                changed = true;
            }
        }

        return changed;
    }

    private static boolean updateConnectorConstruction(CompilationUnit cu) {
        boolean changed = false;
        for (ObjectCreationExpr creation : cu.findAll(ObjectCreationExpr.class)) {
            String text = creation.getType().asString();
            if (text.equals("SelectChannelConnector") || text.equals(OLD_SELECT_CHANNEL_CONNECTOR) || text.endsWith(".SelectChannelConnector")) {
                Expression serverExpr = inferServerExpression(creation);
                if (serverExpr != null) {
                    creation.setType(StaticJavaParser.parseClassOrInterfaceType("ServerConnector"));
                    NodeList<Expression> args = new NodeList<Expression>();
                    args.add(serverExpr);
                    creation.setArguments(args);
                    changed = true;
                }
            }
        }
        return changed;
    }

    private static Expression inferServerExpression(Node node) {
        Optional<MethodDeclaration> method = node.findAncestor(MethodDeclaration.class);
        if (method.isPresent()) {
            Expression fromParameters = findServerFromParameters(method.get().getParameters());
            if (fromParameters != null) {
                return fromParameters;
            }
            if (method.get().getBody().isPresent()) {
                Expression fromLocals = findServerFromVariables(method.get().getBody().get().findAll(VariableDeclarator.class));
                if (fromLocals != null) {
                    return fromLocals;
                }
            }
        }

        Optional<ConstructorDeclaration> constructor = node.findAncestor(ConstructorDeclaration.class);
        if (constructor.isPresent()) {
            Expression fromParameters = findServerFromParameters(constructor.get().getParameters());
            if (fromParameters != null) {
                return fromParameters;
            }
            if (constructor.get().getBody() != null) {
                Expression fromLocals = findServerFromVariables(constructor.get().getBody().findAll(VariableDeclarator.class));
                if (fromLocals != null) {
                    return fromLocals;
                }
            }
        }

        Optional<ClassOrInterfaceDeclaration> clazz = node.findAncestor(ClassOrInterfaceDeclaration.class);
        if (clazz.isPresent()) {
            for (FieldDeclaration field : clazz.get().getFields()) {
                for (VariableDeclarator variable : field.getVariables()) {
                    if (isServerType(variable.getType().asString())) {
                        return new NameExpr(variable.getNameAsString());
                    }
                }
            }
        }

        return null;
    }

    private static Expression findServerFromParameters(List<Parameter> parameters) {
        for (Parameter parameter : parameters) {
            if (isServerType(parameter.getType().asString())) {
                return new NameExpr(parameter.getNameAsString());
            }
        }
        return null;
    }

    private static Expression findServerFromVariables(List<VariableDeclarator> variables) {
        for (VariableDeclarator variable : variables) {
            if (isServerType(variable.getType().asString())) {
                return new NameExpr(variable.getNameAsString());
            }
        }
        return null;
    }

    private static boolean isServerType(String typeName) {
        return "Server".equals(typeName) || "org.eclipse.jetty.server.Server".equals(typeName) || typeName.endsWith(".Server");
    }
}
