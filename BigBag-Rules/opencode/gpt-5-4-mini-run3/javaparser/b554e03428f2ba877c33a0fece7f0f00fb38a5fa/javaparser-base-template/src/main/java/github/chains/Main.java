package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.NullLiteralExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.visitor.ModifierVisitor;
import com.github.javaparser.ast.visitor.Visitable;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.Optional;
import java.util.stream.Stream;

public class Main {

    private static final String OLD_TYPE = "org.apache.maven.shared.dependency.graph.internal.Maven31DependencyGraphBuilder";
    private static final String OLD_SIMPLE = "Maven31DependencyGraphBuilder";
    private static final String NEW_TYPE = "org.apache.maven.shared.dependency.graph.internal.DefaultDependencyGraphBuilder";
    private static final String NEW_SIMPLE = "DefaultDependencyGraphBuilder";
    private static final String RESOLVER_TYPE = "org.apache.maven.project.ProjectDependenciesResolver";

    public static void main(String[] args) throws IOException {
        if (args.length != 1) {
            throw new IllegalArgumentException("Expected one argument: source directory");
        }

        Path root = Paths.get(args[0]).toAbsolutePath().normalize();
        try (Stream<Path> paths = Files.walk(root)) {
            for (Path file : (Iterable<Path>) paths.filter(p -> p.toString().endsWith(".java")).sorted(Comparator.naturalOrder())::iterator) {
                CompilationUnit cu = StaticJavaParser.parse(file);
                boolean[] changed = new boolean[] { false };
                boolean[] needsNewImport = new boolean[] { false };

                cu.getImports().removeIf(Main::isOldImport);

                cu.accept(new ModifierVisitor<Void>() {
                    @Override
                    public Visitable visit(ObjectCreationExpr expr, Void unused) {
                        Visitable visited = super.visit(expr, unused);
                        ObjectCreationExpr creation = (ObjectCreationExpr) visited;
                        if (isOldCreation(creation)) {
                            Expression resolver = findResolverExpression(creation).orElseGet(NullLiteralExpr::new);
                            ObjectCreationExpr replacement = new ObjectCreationExpr();
                            replacement.setType(new ClassOrInterfaceType(null, NEW_SIMPLE));
                            replacement.addArgument(resolver.clone());
                            changed[0] = true;
                            needsNewImport[0] = true;
                            return replacement;
                        }
                        return creation;
                    }
                }, null);

                if (changed[0]) {
                    if (needsNewImport[0]) {
                        cu.addImport(NEW_TYPE);
                    }
                    Files.writeString(file, cu.toString(), StandardCharsets.UTF_8);
                }
            }
        }
    }

    private static boolean isOldImport(ImportDeclaration importDeclaration) {
        return !importDeclaration.isAsterisk() && importDeclaration.getNameAsString().equals(OLD_TYPE);
    }

    private static boolean isOldCreation(ObjectCreationExpr expr) {
        String typeName = expr.getType().asString();
        return OLD_SIMPLE.equals(typeName) || OLD_TYPE.equals(typeName);
    }

    private static Optional<Expression> findResolverExpression(Node node) {
        Optional<Expression> fromMethod = node.findAncestor(MethodDeclaration.class).flatMap(Main::findInMethod);
        if (fromMethod.isPresent()) {
            return fromMethod;
        }

        return node.findAncestor(ClassOrInterfaceDeclaration.class).flatMap(Main::findInClass);
    }

    private static Optional<Expression> findInMethod(MethodDeclaration method) {
        for (int i = 0; i < method.getParameters().size(); i++) {
            if (matchesType(method.getParameter(i).getType().asString(), RESOLVER_TYPE)) {
                return Optional.of(new NameExpr(method.getParameter(i).getNameAsString()));
            }
        }

        return Optional.empty();
    }

    private static Optional<Expression> findInClass(ClassOrInterfaceDeclaration clazz) {
        for (FieldDeclaration field : clazz.getFields()) {
            for (VariableDeclarator variable : field.getVariables()) {
                if (matchesType(variable.getType().asString(), RESOLVER_TYPE)) {
                    return Optional.of(new NameExpr(variable.getNameAsString()));
                }
            }
        }

        return Optional.empty();
    }

    private static boolean matchesType(String actualType, String expectedType) {
        return expectedType.equals(actualType) || expectedType.endsWith("." + actualType);
    }
}
