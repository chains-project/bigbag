package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.Modifier.Keyword;
import com.github.javaparser.ast.type.ClassOrInterfaceType;

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
import java.util.stream.Stream;

public class Main {
    private static final String OLD_TYPE = "org.apache.maven.shared.dependency.graph.internal.Maven31DependencyGraphBuilder";
    private static final String NEW_TYPE = "org.apache.maven.shared.dependency.graph.internal.DefaultDependencyGraphBuilder";
    private static final String OLD_SIMPLE = "Maven31DependencyGraphBuilder";
    private static final String NEW_SIMPLE = "DefaultDependencyGraphBuilder";
    private static final String RESOLVER_TYPE = "org.apache.maven.project.ProjectDependenciesResolver";
    private static final String RESOLVER_SIMPLE = "ProjectDependenciesResolver";
    private static final String RESOLVER_FIELD = "projectDependenciesResolver";
    private static final String COMPONENT_TYPE = "org.apache.maven.plugins.annotations.Component";

    public static void main(String[] args) {
        Path root = args.length > 0 ? Paths.get(args[0]) : Paths.get(".");
        try (Stream<Path> paths = Files.walk(root)) {
            List<Path> javaFiles = paths
                .filter(p -> Files.isRegularFile(p) && p.toString().endsWith(".java"))
                .sorted(Comparator.naturalOrder())
                .collect(Collectors.toList());

            for (Path file : javaFiles) {
                transform(file);
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to traverse source tree: " + root, e);
        }
    }

    private static void transform(Path file) throws IOException {
        CompilationUnit cu = StaticJavaParser.parse(file);
        if (cu == null) {
            return;
        }

        boolean changed = false;

        for (var it = cu.getImports().iterator(); it.hasNext(); ) {
            var imp = it.next();
            if (imp.getNameAsString().equals(OLD_TYPE)) {
                imp.setName(NEW_TYPE);
                changed = true;
            }
        }

        for (ObjectCreationExpr creation : cu.findAll(ObjectCreationExpr.class)) {
            if (matchesOldType(creation.getType())) {
                creation.setType(NEW_SIMPLE);
                if (creation.getArguments().isEmpty()) {
                    String resolverName = findResolverName(cu).orElseGet(() -> injectResolver(cu));
                    creation.addArgument(new NameExpr(resolverName));
                }
                changed = true;
            }
        }

        if (changed) {
            Files.writeString(file, cu.toString(), StandardCharsets.UTF_8);
        }
    }

    private static boolean matchesOldType(ClassOrInterfaceType type) {
        return type.getNameAsString().equals(OLD_SIMPLE) || type.toString().equals(OLD_TYPE);
    }

    private static Optional<String> findResolverName(CompilationUnit cu) {
        for (FieldDeclaration field : cu.findAll(FieldDeclaration.class)) {
            if (field.getElementType().asString().equals(RESOLVER_SIMPLE)) {
                return Optional.of(field.getVariable(0).getNameAsString());
            }
        }
        for (Parameter parameter : cu.findAll(Parameter.class)) {
            if (parameter.getType().asString().equals(RESOLVER_SIMPLE)) {
                return Optional.of(parameter.getNameAsString());
            }
        }
        return Optional.empty();
    }

    private static String injectResolver(CompilationUnit cu) {
        String simpleImport = addImportIfMissing(cu, RESOLVER_TYPE);
        addImportIfMissing(cu, COMPONENT_TYPE);
        ClassOrInterfaceDeclaration type = cu.findFirst(ClassOrInterfaceDeclaration.class).orElseThrow();
        if (type.getFieldByName(RESOLVER_FIELD).isPresent()) {
            return RESOLVER_FIELD;
        }
        FieldDeclaration field = type.addField(simpleImport, RESOLVER_FIELD, Keyword.PRIVATE);
        field.addMarkerAnnotation("Component");
        return RESOLVER_FIELD;
    }

    private static String addImportIfMissing(CompilationUnit cu, String fqcn) {
        if (cu.getImports().stream().noneMatch(i -> i.getNameAsString().equals(fqcn))) {
            cu.addImport(fqcn);
        }
        return fqcn.substring(fqcn.lastIndexOf('.') + 1);
    }
}
