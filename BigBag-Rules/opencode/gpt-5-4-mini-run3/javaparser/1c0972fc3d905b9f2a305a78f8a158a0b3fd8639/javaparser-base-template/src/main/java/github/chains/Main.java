package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Modifier;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.printer.lexicalpreservation.LexicalPreservingPrinter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.Optional;

public class Main {
    private static final String OLD_BUILDER_FQCN = "org.apache.maven.shared.dependency.graph.internal.Maven31DependencyGraphBuilder";
    private static final String NEW_BUILDER_FQCN = "org.apache.maven.shared.dependency.graph.internal.DefaultDependencyGraphBuilder";
    private static final String RESOLVER_FQCN = "org.apache.maven.project.ProjectDependenciesResolver";
    private static final String COMPONENT_FQCN = "org.apache.maven.plugins.annotations.Component";
    private static final String RESOLVER_FIELD = "projectDependenciesResolver";

    public static void main(String[] args) {
        if (args.length < 1) {
            throw new IllegalArgumentException("Usage: Main <input-source-root> [output-source-root]");
        }

        Path inputRoot = Paths.get(args[0]).toAbsolutePath().normalize();
        Path outputRoot = args.length > 1 ? Paths.get(args[1]).toAbsolutePath().normalize() : inputRoot;

        try {
            try (var paths = Files.walk(inputRoot)) {
                paths.sorted(Comparator.naturalOrder()).forEach(path -> processPath(inputRoot, outputRoot, path));
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to transform sources under " + inputRoot, e);
        }
    }

    private static void processPath(Path inputRoot, Path outputRoot, Path path) {
        try {
            Path relative = inputRoot.relativize(path);
            Path target = outputRoot.resolve(relative);

            if (Files.isDirectory(path)) {
                Files.createDirectories(target);
                return;
            }

            if (!path.toString().endsWith(".java")) {
                Files.createDirectories(target.getParent());
                Files.copy(path, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                return;
            }

            CompilationUnit cu = StaticJavaParser.parse(path);
            LexicalPreservingPrinter.setup(cu);

            transform(cu);
            Files.createDirectories(target.getParent());
            Files.writeString(target, LexicalPreservingPrinter.print(cu), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException("Failed to process " + path, e);
        }
    }

    private static void transform(CompilationUnit cu) {
        boolean needsResolverImport = false;
        boolean needsComponentImport = false;

        if (cu.getImports().removeIf(importDeclaration -> importDeclaration.getNameAsString().equals(OLD_BUILDER_FQCN))) {
            cu.addImport(NEW_BUILDER_FQCN);
        }

        for (ObjectCreationExpr creation : cu.findAll(ObjectCreationExpr.class)) {
            if (!isOldBuilderCreation(creation)) {
                continue;
            }

            ClassOrInterfaceDeclaration owner = creation.findAncestor(ClassOrInterfaceDeclaration.class).orElse(null);
            if (owner == null) {
                continue;
            }

            String resolverName = ensureResolverField(cu, owner);
            needsResolverImport = true;
            needsComponentImport = true;

            ObjectCreationExpr replacement = new ObjectCreationExpr();
            replacement.setType(new ClassOrInterfaceType(null, simpleName(NEW_BUILDER_FQCN)));
            replacement.setArguments(NodeList.nodeList(new NameExpr(resolverName)));
            creation.replace(replacement);
        }

        if (needsResolverImport) {
            cu.addImport(RESOLVER_FQCN);
        }
        if (needsComponentImport) {
            cu.addImport(COMPONENT_FQCN);
        }
    }

    private static String ensureResolverField(CompilationUnit cu, ClassOrInterfaceDeclaration owner) {
        Optional<FieldDeclaration> existing = owner.getFields().stream()
            .filter(field -> field.getElementType().asString().equals(simpleName(RESOLVER_FQCN)))
            .findFirst();

        if (existing.isPresent()) {
            return existing.get().getVariable(0).getNameAsString();
        }

        FieldDeclaration field = owner.addField(simpleName(RESOLVER_FQCN), RESOLVER_FIELD, Modifier.Keyword.PROTECTED);
        field.addAnnotation(simpleName(COMPONENT_FQCN));
        return RESOLVER_FIELD;
    }

    private static boolean isOldBuilderCreation(ObjectCreationExpr creation) {
        return creation.getType().asString().equals(simpleName(OLD_BUILDER_FQCN))
            || creation.getType().asString().equals(OLD_BUILDER_FQCN)
            || creation.getType().getNameWithScope().equals(OLD_BUILDER_FQCN);
    }

    private static String simpleName(String fqcn) {
        int dot = fqcn.lastIndexOf('.');
        return dot >= 0 ? fqcn.substring(dot + 1) : fqcn;
    }
}
