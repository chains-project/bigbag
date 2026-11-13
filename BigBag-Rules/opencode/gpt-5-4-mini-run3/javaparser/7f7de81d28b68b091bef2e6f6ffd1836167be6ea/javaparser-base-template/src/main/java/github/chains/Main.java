package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.type.Type;
import com.github.javaparser.ast.type.WildcardType;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.Optional;
import java.util.stream.Stream;

public class Main {

    private static final String TARGET_PACKAGE = "org.snmp4j.agent";
    private static final String TARGET_SIMPLE_NAME = "ManagedObject";
    private static final String TARGET_FQN = TARGET_PACKAGE + "." + TARGET_SIMPLE_NAME;

    public static void main(String[] args) {
        Path sourceRoot = args.length > 0 ? Paths.get(args[0]) : Paths.get(".");
        try {
            transform(sourceRoot);
        } catch (IOException e) {
            throw new RuntimeException("failed to transform sources under " + sourceRoot, e);
        }
    }

    private static void transform(Path sourceRoot) throws IOException {
        try (Stream<Path> paths = Files.walk(sourceRoot)) {
            paths.filter(path -> Files.isRegularFile(path) && path.toString().endsWith(".java"))
                    .sorted(Comparator.naturalOrder())
                    .forEach(Main::rewriteFile);
        }
    }

    private static void rewriteFile(Path path) {
        try {
            CompilationUnit cu = StaticJavaParser.parse(path, StandardCharsets.UTF_8);
            boolean changed = cu.findAll(ClassOrInterfaceType.class).stream()
                    .filter(type -> shouldParameterizeManagedObject(cu, type))
                    .peek(Main::parameterizeManagedObject)
                    .count() > 0;
            if (changed) {
                Files.writeString(path, cu.toString(), StandardCharsets.UTF_8);
            }
        } catch (IOException e) {
            throw new RuntimeException("failed to rewrite " + path, e);
        }
    }

    private static boolean shouldParameterizeManagedObject(CompilationUnit cu, ClassOrInterfaceType type) {
        if (!TARGET_SIMPLE_NAME.equals(type.getNameAsString())) {
            return false;
        }
        return isTargetManagedObjectReference(cu, type);
    }

    private static boolean isTargetManagedObjectReference(CompilationUnit cu, ClassOrInterfaceType type) {
        if (type.getScope().isPresent()) {
            return TARGET_PACKAGE.equals(type.getScope().get().toString());
        }
        return cu.getImports().stream().anyMatch(Main::isTargetImport)
                || cu.getPackageDeclaration().map(pkg -> TARGET_PACKAGE.equals(pkg.getNameAsString())).orElse(false);
    }

    private static boolean isTargetImport(ImportDeclaration importDeclaration) {
        return importDeclaration.getNameAsString().equals(TARGET_FQN)
                || (importDeclaration.isAsterisk() && importDeclaration.getNameAsString().equals(TARGET_PACKAGE));
    }

    private static void parameterizeManagedObject(ClassOrInterfaceType type) {
        try {
            NodeList<Type> typeArguments = new NodeList<>();
            if (isDeclarationSite(type)) {
                typeArguments.add(StaticJavaParser.parseType("org.snmp4j.agent.request.SubRequest<? extends java.lang.Object>"));
            } else {
                typeArguments.add(new WildcardType());
            }
            type.setTypeArguments(typeArguments);
        } catch (Exception e) {
            throw new RuntimeException("failed to create replacement type", e);
        }
    }

    private static boolean isDeclarationSite(ClassOrInterfaceType type) {
        return type.findAncestor(ClassOrInterfaceDeclaration.class)
                .map(decl -> decl.getImplementedTypes().stream().anyMatch(t -> t == type)
                        || decl.getExtendedTypes().stream().anyMatch(t -> t == type))
                .orElse(false);
    }
}
