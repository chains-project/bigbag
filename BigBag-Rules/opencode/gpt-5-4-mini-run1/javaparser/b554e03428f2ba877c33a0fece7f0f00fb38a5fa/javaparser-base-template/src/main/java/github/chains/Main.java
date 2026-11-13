package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.CallableDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.NullLiteralExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.type.ClassOrInterfaceType;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class Main {
    private static final String OLD_TYPE = "org.apache.maven.shared.dependency.graph.internal.Maven31DependencyGraphBuilder";
    private static final String NEW_TYPE = "org.apache.maven.shared.dependency.graph.internal.DefaultDependencyGraphBuilder";
    private static final String RESOLVER_TYPE = "org.apache.maven.project.ProjectDependenciesResolver";

    public static void main(String[] args) {
        if (args.length == 0) {
            System.err.println("Usage: Main <source-root>");
            return;
        }

        Path root = Paths.get(args[0]);
        if (!Files.exists(root)) {
            System.err.println("Source root does not exist: " + root);
            return;
        }

        try (Stream<Path> paths = Files.walk(root)) {
            List<Path> javaFiles = paths.filter(p -> Files.isRegularFile(p) && p.toString().endsWith(".java"))
                    .collect(Collectors.toList());
            for (Path file : javaFiles) {
                transformFile(file);
            }
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }
    }

    private static void transformFile(Path file) throws IOException {
        CompilationUnit cu = StaticJavaParser.parse(file);
        boolean changed = false;
        boolean replacedOldDependencyGraphBuilder = false;

        for (ObjectCreationExpr creation : cu.findAll(ObjectCreationExpr.class)) {
            if (matchesOldType(creation.getType().toString())) {
                creation.setType(StaticJavaParser.parseClassOrInterfaceType(simpleName(NEW_TYPE)));
                if (creation.getArguments().isEmpty()) {
                    creation.addArgument(resolveProjectDependenciesResolver(creation).orElseGet(NullLiteralExpr::new));
                }
                changed = true;
                replacedOldDependencyGraphBuilder = true;
            }
        }

        for (ClassOrInterfaceType type : cu.findAll(ClassOrInterfaceType.class)) {
            if (matchesOldType(type.toString())) {
                type.replace(StaticJavaParser.parseClassOrInterfaceType(simpleName(NEW_TYPE)));
                changed = true;
                replacedOldDependencyGraphBuilder = true;
            }
        }

        changed |= replaceImports(cu, replacedOldDependencyGraphBuilder);

        if (changed) {
            Files.writeString(file, cu.toString(), StandardCharsets.UTF_8);
        }
    }

    private static boolean replaceImports(CompilationUnit cu, boolean replaceWithNewType) {
        boolean changed = false;
        if (cu.getImports().removeIf(i -> i.getNameAsString().equals(OLD_TYPE))) {
            changed = true;
        }
        if (replaceWithNewType && cu.getImports().stream().noneMatch(i -> i.getNameAsString().equals(NEW_TYPE))) {
            cu.addImport(NEW_TYPE);
            changed = true;
        }
        return changed;
    }

    private static Optional<com.github.javaparser.ast.expr.Expression> resolveProjectDependenciesResolver(ObjectCreationExpr creation) {
        com.github.javaparser.ast.Node current = creation;
        while (current.getParentNode().isPresent()) {
            current = current.getParentNode().get();

            if (current instanceof CallableDeclaration) {
                CallableDeclaration<?> callable = (CallableDeclaration<?>) current;
                for (Parameter parameter : callable.getParameters()) {
                    if (matchesResolverType(parameter.getType().toString())) {
                        return Optional.of(new NameExpr(parameter.getNameAsString()));
                    }
                }
            }

            if (current instanceof ClassOrInterfaceDeclaration) {
                ClassOrInterfaceDeclaration clazz = (ClassOrInterfaceDeclaration) current;
                for (FieldDeclaration field : clazz.getFields()) {
                    if (matchesResolverType(field.getElementType().toString()) && !field.getVariables().isEmpty()) {
                        return Optional.of(new NameExpr(field.getVariable(0).getNameAsString()));
                    }
                }
            }
        }
        return Optional.empty();
    }

    private static boolean matchesOldType(String typeName) {
        return OLD_TYPE.equals(typeName) || simpleName(OLD_TYPE).equals(typeName) || typeName.endsWith("." + simpleName(OLD_TYPE));
    }

    private static boolean matchesResolverType(String typeName) {
        return RESOLVER_TYPE.equals(typeName) || simpleName(RESOLVER_TYPE).equals(typeName) || typeName.endsWith("." + simpleName(RESOLVER_TYPE));
    }

    private static String simpleName(String fqcn) {
        int idx = fqcn.lastIndexOf('.');
        return idx >= 0 ? fqcn.substring(idx + 1) : fqcn;
    }
}
