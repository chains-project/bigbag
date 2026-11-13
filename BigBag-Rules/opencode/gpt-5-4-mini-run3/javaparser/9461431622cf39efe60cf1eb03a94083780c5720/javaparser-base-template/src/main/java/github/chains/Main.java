package github.chains;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseResult;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.type.Type;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class Main {
    private static final String MANAGED_OBJECT = "org.snmp4j.agent.ManagedObject";
    private static final String SUB_REQUEST = "org.snmp4j.agent.request.SubRequest";

    public static void main(String[] args) throws IOException {
        Path sourceRoot = Paths.get(args.length > 0 ? args[0] : ".").toAbsolutePath().normalize();
        JavaParser parser = new JavaParser(new ParserConfiguration());

        try (Stream<Path> paths = Files.walk(sourceRoot)) {
            List<Path> javaFiles = paths
                    .filter(path -> Files.isRegularFile(path) && path.toString().endsWith(".java"))
                    .sorted(Comparator.naturalOrder())
                    .collect(Collectors.toList());

            for (Path path : javaFiles) {
                ParseResult<CompilationUnit> result = parser.parse(path);
                if (!result.isSuccessful() || result.getResult().isEmpty()) {
                    throw new IOException("Failed to parse " + path + ": " + result.getProblems());
                }

                CompilationUnit cu = result.getResult().get();
                boolean changed = rewriteTypes(cu);
                if (changed) {
                    Files.writeString(path, cu.toString(), StandardCharsets.UTF_8);
                }
            }
        }
    }

    private static boolean rewriteTypes(CompilationUnit cu) {
        boolean changed = false;

        for (ClassOrInterfaceDeclaration declaration : cu.findAll(ClassOrInterfaceDeclaration.class)) {
            changed |= rewriteInheritedManagedObjectTypes(declaration);
        }

        for (ClassOrInterfaceType type : cu.findAll(ClassOrInterfaceType.class)) {
            if (isRawType(type, MANAGED_OBJECT)) {
                type.setTypeArguments(NodeList.nodeList(StaticJavaParser.parseType("org.snmp4j.agent.request.SubRequest")));
                changed = true;
            }
        }

        return changed;
    }

    private static boolean rewriteInheritedManagedObjectTypes(ClassOrInterfaceDeclaration declaration) {
        boolean changed = false;
        for (ClassOrInterfaceType type : declaration.getImplementedTypes()) {
            if (isRawType(type, MANAGED_OBJECT)) {
                type.setTypeArguments(NodeList.nodeList(StaticJavaParser.parseType("org.snmp4j.agent.request.SubRequest<?>")));
                changed = true;
            }
        }
        for (ClassOrInterfaceType type : declaration.getExtendedTypes()) {
            if (isRawType(type, MANAGED_OBJECT)) {
                type.setTypeArguments(NodeList.nodeList(StaticJavaParser.parseType("org.snmp4j.agent.request.SubRequest<?>")));
                changed = true;
            }
        }
        return changed;
    }

    private static boolean isRawType(Type type, String fqcn) {
        if (!(type instanceof ClassOrInterfaceType)) {
            return false;
        }
        ClassOrInterfaceType cit = (ClassOrInterfaceType) type;
        return cit.getTypeArguments().isEmpty() && matchesQualifiedName(cit, fqcn);
    }

    private static boolean matchesQualifiedName(ClassOrInterfaceType type, String fqcn) {
        if (type.getNameAsString().equals(simpleName(fqcn))) {
            if (type.getScope().isEmpty()) {
                return true;
            }
            return type.toString().equals(fqcn);
        }
        return false;
    }

    private static String simpleName(String fqcn) {
        int idx = fqcn.lastIndexOf('.');
        return idx < 0 ? fqcn : fqcn.substring(idx + 1);
    }
}
