package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.type.Type;
import com.github.javaparser.ast.NodeList;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

public class Main {

    private static final String TARGET_METHOD = "getRegistry";
    private static final String TARGET_CONTAINER_TYPE = "SortedMap";
    private static final String TARGET_VALUE_TYPE = "ManagedObject";

    public static void main(String[] args) throws IOException {
        if (args.length == 0) {
            throw new IllegalArgumentException("Expected source root path as the first argument");
        }

        Path root = Paths.get(args[0]);
        try (Stream<Path> paths = Files.walk(root)) {
            List<Path> javaFiles = paths.filter(p -> Files.isRegularFile(p) && p.toString().endsWith(".java")).toList();
            for (Path file : javaFiles) {
                transformFile(file);
            }
        }
    }

    private static void transformFile(Path file) throws IOException {
        String source = Files.readString(file, StandardCharsets.UTF_8);
        CompilationUnit cu = StaticJavaParser.parse(source);
        boolean[] changed = {false};

        cu.findAll(VariableDeclarator.class).forEach(variable -> {
            if (rewriteDeclaredRegistryType(variable)) {
                changed[0] = true;
            }
        });

        if (changed[0]) {
            Files.writeString(file, cu.toString(), StandardCharsets.UTF_8);
        }
    }

    private static boolean rewriteDeclaredRegistryType(VariableDeclarator variable) {
        Optional<Expression> initializer = variable.getInitializer();
        if (initializer.isEmpty() || !isTargetRegistryCall(initializer.get())) {
            return false;
        }

        Type type = variable.getType();
        if (!type.isClassOrInterfaceType()) {
            return false;
        }

        ClassOrInterfaceType mapType = type.asClassOrInterfaceType();
        if (!TARGET_CONTAINER_TYPE.equals(mapType.getNameAsString()) || mapType.getTypeArguments().isEmpty()) {
            return false;
        }

        List<Type> arguments = mapType.getTypeArguments().get().stream().toList();
        if (arguments.size() < 2) {
            return false;
        }

        Type valueType = arguments.get(1);
        if (!valueType.isClassOrInterfaceType()) {
            return false;
        }

        ClassOrInterfaceType managedObjectType = valueType.asClassOrInterfaceType();
        if (!TARGET_VALUE_TYPE.equals(managedObjectType.getNameAsString()) || managedObjectType.getTypeArguments().isPresent()) {
            return false;
        }

        ClassOrInterfaceType updatedValueType = StaticJavaParser.parseClassOrInterfaceType(TARGET_VALUE_TYPE + "<?>");
        NodeList<Type> updatedTypeArguments = new NodeList<>();
        updatedTypeArguments.add(arguments.get(0));
        updatedTypeArguments.add(updatedValueType);
        mapType.setTypeArguments(updatedTypeArguments);
        return true;
    }

    private static boolean isTargetRegistryCall(Expression expression) {
        return expression.isMethodCallExpr() && isTargetRegistryCall(expression.asMethodCallExpr());
    }

    private static boolean isTargetRegistryCall(MethodCallExpr methodCall) {
        return TARGET_METHOD.equals(methodCall.getNameAsString());
    }
}
