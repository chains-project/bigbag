package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.expr.InstanceOfExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.type.ArrayType;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.type.Type;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.stream.Stream;

public class Main {

    private static final String OLD_TYPE = "Xpp3Dom";
    private static final String OLD_FQN = "org.codehaus.plexus.util.xml.Xpp3Dom";
    private static final String NEW_INTERFACE = "PlexusConfiguration";
    private static final String NEW_INTERFACE_FQN = "org.codehaus.plexus.configuration.PlexusConfiguration";
    private static final String NEW_IMPL = "DefaultPlexusConfiguration";
    private static final String NEW_IMPL_FQN = "org.codehaus.plexus.configuration.DefaultPlexusConfiguration";

    public static void main(String[] args) {
        Path root = args.length == 0 ? Path.of(".") : Path.of(args[0]);
        try {
            transformTree(root);
        } catch (IOException e) {
            throw new RuntimeException("Failed to transform sources under " + root, e);
        }
    }

    private static void transformTree(Path root) throws IOException {
        try (Stream<Path> paths = Files.walk(root)) {
            paths.filter(path -> Files.isRegularFile(path) && path.toString().endsWith(".java"))
                    .sorted(Comparator.naturalOrder())
                    .forEach(Main::transformFileUnchecked);
        }
    }

    private static void transformFileUnchecked(Path file) {
        try {
            transformFile(file);
        } catch (IOException e) {
            throw new RuntimeException("Failed to transform " + file, e);
        }
    }

    private static void transformFile(Path file) throws IOException {
        CompilationUnit unit = StaticJavaParser.parse(Files.readString(file, StandardCharsets.UTF_8));

        boolean changed = false;
        changed |= rewriteInstantiations(unit);
        changed |= rewriteTypes(unit);
        changed |= rewriteImports(unit);

        if (changed) {
            Files.writeString(file, unit.toString(), StandardCharsets.UTF_8);
        }
    }

    private static boolean rewriteImports(CompilationUnit unit) {
        boolean changed = unit.getImports().removeIf(importDecl -> importDecl.getNameAsString().equals(OLD_FQN));
        if (usesTypeReference(unit)) {
            changed |= addImportIfMissing(unit, NEW_INTERFACE_FQN);
        }
        if (usesConstruction(unit)) {
            changed |= addImportIfMissing(unit, NEW_IMPL_FQN);
        }
        return changed;
    }

    private static boolean addImportIfMissing(CompilationUnit unit, String fqcn) {
        if (unit.getImports().stream().anyMatch(importDecl -> importDecl.getNameAsString().equals(fqcn))) {
            return false;
        }
        unit.addImport(fqcn);
        return true;
    }

    private static boolean rewriteTypes(CompilationUnit unit) {
        boolean changed = false;
        for (ClassOrInterfaceType type : unit.findAll(ClassOrInterfaceType.class)) {
            if (type.getParentNode().filter(parent -> parent instanceof ObjectCreationExpr).isPresent()) {
                continue;
            }
            if (isOldType(type.getNameAsString())) {
                type.setName(NEW_INTERFACE);
                changed = true;
            }
        }
        for (ArrayType type : unit.findAll(ArrayType.class)) {
            Type component = type.getComponentType();
            if (component.isClassOrInterfaceType() && isOldType(component.asClassOrInterfaceType().getNameAsString())) {
                component.asClassOrInterfaceType().setName(NEW_INTERFACE);
                changed = true;
            }
        }
        for (Parameter parameter : unit.findAll(Parameter.class)) {
            Type type = parameter.getType();
            if (type.isClassOrInterfaceType() && isOldType(type.asClassOrInterfaceType().getNameAsString())) {
                type.asClassOrInterfaceType().setName(NEW_INTERFACE);
                changed = true;
            }
        }
        for (InstanceOfExpr expr : unit.findAll(InstanceOfExpr.class)) {
            if (expr.getType().isClassOrInterfaceType() && isOldType(expr.getType().asClassOrInterfaceType().getNameAsString())) {
                expr.getType().asClassOrInterfaceType().setName(NEW_INTERFACE);
                changed = true;
            }
        }
        return changed;
    }

    private static boolean rewriteInstantiations(CompilationUnit unit) {
        boolean changed = false;
        for (ObjectCreationExpr expr : unit.findAll(ObjectCreationExpr.class)) {
            if (isOldType(expr.getType().getNameAsString()) || NEW_INTERFACE.equals(expr.getType().getNameAsString())) {
                expr.getType().setName(NEW_IMPL);
                changed = true;
            }
        }
        return changed;
    }

    private static boolean usesTypeReference(CompilationUnit unit) {
        return unit.findAll(ClassOrInterfaceType.class).stream().anyMatch(type -> isOldType(type.getNameAsString()))
                || unit.findAll(Parameter.class).stream().anyMatch(parameter -> parameter.getType().isClassOrInterfaceType()
                && isOldType(parameter.getType().asClassOrInterfaceType().getNameAsString()))
                || unit.findAll(InstanceOfExpr.class).stream().anyMatch(expr -> expr.getType().isClassOrInterfaceType()
                && isOldType(expr.getType().asClassOrInterfaceType().getNameAsString()));
    }

    private static boolean usesConstruction(CompilationUnit unit) {
        return unit.findAll(ObjectCreationExpr.class).stream().anyMatch(expr -> isOldType(expr.getType().getNameAsString()));
    }

    private static boolean isOldType(String name) {
        return OLD_TYPE.equals(name) || OLD_FQN.equals(name);
    }
}
