package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.type.WildcardType;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Stream;

public class Main {

    private static final String TARGET_TYPE = "org.snmp4j.agent.ManagedObject";

    public static void main(String[] args) throws IOException {
        if (args.length == 0) {
            throw new IllegalArgumentException("expected source root path");
        }

        Path root = Paths.get(args[0]);
        try (Stream<Path> paths = Files.walk(root)) {
            paths.filter(Main::isJavaSource)
                    .forEach(Main::rewriteFile);
        }
    }

    private static boolean isJavaSource(Path path) {
        return Files.isRegularFile(path)
                && path.toString().endsWith(".java")
                && !path.toString().contains("/target/");
    }

    private static void rewriteFile(Path path) {
        try {
            CompilationUnit compilationUnit = StaticJavaParser.parse(path);

            boolean changed = false;
            for (ClassOrInterfaceType type : compilationUnit.findAll(ClassOrInterfaceType.class)) {
                if (shouldUpgrade(type)) {
                    type.setTypeArguments(NodeList.nodeList(new WildcardType()));
                    changed = true;
                }
            }

            if (changed) {
                Files.writeString(path, compilationUnit.toString(), StandardCharsets.UTF_8);
            }
        } catch (IOException e) {
            throw new RuntimeException("failed to rewrite " + path, e);
        }
    }

    private static boolean shouldUpgrade(ClassOrInterfaceType type) {
        if (!isTargetManagedObject(type)) {
            return false;
        }
        if (type.getTypeArguments().isPresent()) {
            return false;
        }
        return true;
    }

    private static boolean isTargetManagedObject(ClassOrInterfaceType type) {
        if (!type.getNameAsString().equals("ManagedObject")) {
            return false;
        }

        if (TARGET_TYPE.equals(type.getNameWithScope())) {
            return true;
        }

        String targetPackage = TARGET_TYPE.substring(0, TARGET_TYPE.lastIndexOf('.'));
        return type.findCompilationUnit()
                .map(cu -> cu.getImports().stream()
                        .anyMatch(importDecl -> TARGET_TYPE.equals(importDecl.getNameAsString())
                                || (importDecl.isAsterisk() && targetPackage.equals(importDecl.getNameAsString()))))
                .orElse(false);
    }

}
