package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.BodyDeclaration;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.ConstructorDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.type.Type;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

public class Main {

    private static final String OLD_TYPE = "org.apache.maven.shared.dependency.graph.internal.Maven31DependencyGraphBuilder";
    private static final String NEW_TYPE = "org.apache.maven.shared.dependency.graph.internal.DefaultDependencyGraphBuilder";
    private static final String RESOLVER_TYPE = "org.apache.maven.project.ProjectDependenciesResolver";

    public static void main(String[] args) throws IOException {
        if (args.length < 1) {
            System.err.println("Usage: Main <source-root>");
            System.exit(1);
        }

        Path root = Paths.get(args[0]);
        if (!Files.isDirectory(root)) {
            throw new IllegalArgumentException("Source root does not exist: " + root);
        }

        try (var paths = Files.walk(root)) {
            paths.filter(path -> path.toString().endsWith(".java"))
                    .sorted(Comparator.naturalOrder())
                    .forEach(Main::rewriteFile);
        }
    }

    private static void rewriteFile(Path path) {
        try {
            CompilationUnit cu = StaticJavaParser.parse(path);
            boolean changed = false;

            changed |= replaceLegacyConstructorCalls(cu);
            changed |= removeOldImports(cu);
            changed |= addNewImportIfNeeded(cu);

            if (changed) {
                Files.writeString(path, cu.toString(), StandardCharsets.UTF_8);
            }
        } catch (Exception ex) {
            throw new RuntimeException("Failed to rewrite " + path, ex);
        }
    }

    private static boolean replaceLegacyConstructorCalls(CompilationUnit cu) {
        boolean changed = false;
        for (MethodCallExpr call : cu.findAll(MethodCallExpr.class)) {
            if (!call.getNameAsString().equals("orElse") || call.getArguments().size() != 1) {
                continue;
            }

            Expression arg = call.getArgument(0);
            if (!(arg instanceof ObjectCreationExpr)) {
                continue;
            }

            ObjectCreationExpr creation = (ObjectCreationExpr) arg;
            if (!isType(creation.getType(), OLD_TYPE)) {
                continue;
            }

            if (call.getScope().isPresent() && call.getScope().get() instanceof MethodCallExpr) {
                MethodCallExpr scope = (MethodCallExpr) call.getScope().get();
                if (scope.getNameAsString().equals("ofNullable") && scope.getArguments().size() == 1) {
                    call.replace(scope.getArgument(0).clone());
                    changed = true;
                }
            }
        }

        return changed;
    }

    private static Optional<Expression> findResolverExpression(ObjectCreationExpr creation) {
        Optional<ClassOrInterfaceDeclaration> clazz = creation.findAncestor(ClassOrInterfaceDeclaration.class);
        if (clazz.isPresent()) {
            for (FieldDeclaration field : clazz.get().findAll(FieldDeclaration.class)) {
                for (var variable : field.getVariables()) {
                    if (isResolverType(field.getElementType())) {
                        return Optional.of(new NameExpr(variable.getNameAsString()));
                    }
                }
            }
        }

        Optional<MethodDeclaration> method = creation.findAncestor(MethodDeclaration.class);
        if (method.isPresent()) {
            for (Parameter parameter : method.get().getParameters()) {
                if (isResolverType(parameter.getType())) {
                    return Optional.of(new NameExpr(parameter.getNameAsString()));
                }
            }
        }

        Optional<ConstructorDeclaration> constructor = creation.findAncestor(ConstructorDeclaration.class);
        if (constructor.isPresent()) {
            for (Parameter parameter : constructor.get().getParameters()) {
                if (isResolverType(parameter.getType())) {
                    return Optional.of(new NameExpr(parameter.getNameAsString()));
                }
            }
        }

        return Optional.empty();
    }

    private static boolean removeOldImports(CompilationUnit cu) {
        return cu.getImports().removeIf(importDeclaration -> importDeclaration.getNameAsString().equals(OLD_TYPE));
    }

    private static boolean addNewImportIfNeeded(CompilationUnit cu) {
        boolean usesNewType = cu.findAll(ObjectCreationExpr.class).stream().anyMatch(expr -> isType(expr.getType(), NEW_TYPE));
        if (!usesNewType) {
            return false;
        }
        if (cu.getImports().stream().noneMatch(i -> i.getNameAsString().equals(NEW_TYPE))) {
            cu.addImport(NEW_TYPE);
            return true;
        }
        return false;
    }

    private static boolean isResolverType(Type type) {
        return isType(type, RESOLVER_TYPE);
    }

    private static boolean isType(Type type, String fqcn) {
        return type.asString().equals(simpleName(fqcn)) || type.asString().equals(fqcn);
    }

    private static String simpleName(String fqcn) {
        int lastDot = fqcn.lastIndexOf('.');
        return lastDot >= 0 ? fqcn.substring(lastDot + 1) : fqcn;
    }
}
