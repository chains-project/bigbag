package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.AssignExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.expr.FieldAccessExpr;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.type.ClassOrInterfaceType;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

public class Main {

    private static final Map<String, String> IMPORT_RENAMES = Map.of(
        "com.hazelcast.core.Cluster", "com.hazelcast.cluster.Cluster",
        "com.hazelcast.core.Member", "com.hazelcast.cluster.Member",
        "com.hazelcast.core.MembershipEvent", "com.hazelcast.cluster.MembershipEvent",
        "com.hazelcast.core.MemberAttributeEvent", "com.hazelcast.cluster.MemberAttributeEvent",
        "com.hazelcast.core.MembershipListener", "com.hazelcast.cluster.MembershipListener",
        "com.hazelcast.core.IMap", "com.hazelcast.map.IMap",
        "com.hazelcast.core.MapEvent", "com.hazelcast.map.MapEvent",
        "com.hazelcast.monitor.LocalMapStats", "com.hazelcast.map.LocalMapStats",
        "com.hazelcast.config.MaxSizeConfig", "com.hazelcast.config.EvictionConfig"
    );

    public static void main(String[] args) {
        final Path root = args.length > 0 ? Paths.get(args[0]) : Paths.get(".");
        try (Stream<Path> paths = Files.walk(root)) {
            paths.filter(Files::isRegularFile)
                .filter(path -> path.toString().endsWith(".java"))
                .forEach(Main::transformFile);
        } catch (IOException e) {
            throw new RuntimeException("Failed to traverse source tree: " + root, e);
        }
    }

    private static void transformFile(Path path) {
        try {
            CompilationUnit cu = StaticJavaParser.parse(path, StandardCharsets.UTF_8);
            boolean changed = false;

            changed |= rewriteImports(cu);
            changed |= rewriteQualifiedTypeNames(cu);
            changed |= rewriteMethodNames(cu);
            changed |= rewriteMaxSizeConfigUsages(cu);
            changed |= removeDeprecatedMemberAttributeCallback(cu);
            changed |= rewriteHazelcastUuidListeners(cu);
            changed |= rewriteMemberAttributeAccessors(cu);
            changed |= addMissingEntryExpiredHandler(cu);

            if (changed) {
                Files.writeString(path, cu.toString(), StandardCharsets.UTF_8);
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to transform " + path, e);
        }
    }

    private static boolean rewriteImports(CompilationUnit cu) {
        boolean changed = false;
        for (var importDeclaration : cu.getImports()) {
            String current = importDeclaration.getNameAsString();
            String replacement = IMPORT_RENAMES.get(current);
            if (replacement != null) {
                importDeclaration.setName(replacement);
                changed = true;
            }
        }
        return changed;
    }

    private static boolean rewriteQualifiedTypeNames(CompilationUnit cu) {
        boolean changed = false;
        for (ClassOrInterfaceType type : new ArrayList<>(cu.findAll(ClassOrInterfaceType.class))) {
            String current = type.asString();
            String replacement = IMPORT_RENAMES.get(current);
            if (replacement == null && "MaxSizeConfig".equals(current)) {
                replacement = "com.hazelcast.config.EvictionConfig";
            }
            if (replacement != null) {
                type.replace(StaticJavaParser.parseClassOrInterfaceType(replacement));
                changed = true;
            }
        }
        return changed;
    }

    private static boolean rewriteMethodNames(CompilationUnit cu) {
        boolean changed = false;
        for (MethodCallExpr call : new ArrayList<>(cu.findAll(MethodCallExpr.class))) {
            String name = call.getNameAsString();
            if ("setMaxSizeConfig".equals(name)) {
                call.setName("setEvictionConfig");
                changed = true;
            } else if ("getMaxSizeConfig".equals(name)) {
                call.setName("getEvictionConfig");
                changed = true;
            }
        }
        return changed;
    }

    private static boolean rewriteMaxSizeConfigUsages(CompilationUnit cu) {
        boolean changed = false;
        for (ObjectCreationExpr creation : new ArrayList<>(cu.findAll(ObjectCreationExpr.class))) {
            if (creation.getArguments().size() == 2 && isEvictionStyleMaxSizeConstructor(creation)) {
                Expression size = creation.getArgument(0).clone();
                Expression policy = normalizeMaxSizePolicy(creation.getArgument(1).clone());
                String replacement = String.format(
                    "new com.hazelcast.config.EvictionConfig().setSize(%s).setMaxSizePolicy(%s)",
                    size, policy);
                creation.replace(StaticJavaParser.parseExpression(replacement));
                changed = true;
            }
        }

        for (FieldAccessExpr fieldAccess : new ArrayList<>(cu.findAll(FieldAccessExpr.class))) {
            String text = fieldAccess.toString();
            if (text.startsWith("MaxSizeConfig.MaxSizePolicy.")) {
                fieldAccess.replace(StaticJavaParser.parseExpression(
                    text.replace("MaxSizeConfig.", "com.hazelcast.config.")));
                changed = true;
            }
        }

        return changed;
    }

    private static boolean rewriteHazelcastUuidListeners(CompilationUnit cu) {
        boolean changed = false;
        for (FieldDeclaration field : new ArrayList<>(cu.findAll(FieldDeclaration.class))) {
            boolean listenerInitializer = field.getVariables().stream()
                .map(VariableDeclarator::getInitializer)
                .filter(java.util.Optional::isPresent)
                .map(java.util.Optional::get)
                .filter(Expression.class::isInstance)
                .map(Expression.class::cast)
                .filter(expr -> expr.isMethodCallExpr() && isHazelcastListenerRegistration(expr.asMethodCallExpr().getNameAsString()))
                .findAny().isPresent();
            if (listenerInitializer && field.getElementType().isClassOrInterfaceType() && "String".equals(field.getElementType().asString())) {
                field.getElementType().replace(StaticJavaParser.parseClassOrInterfaceType("java.util.UUID"));
                changed = true;
            }
        }

        for (VariableDeclarator variable : new ArrayList<>(cu.findAll(VariableDeclarator.class))) {
            if (!variable.getType().isClassOrInterfaceType() || !"String".equals(variable.getType().asString())) {
                continue;
            }
            if (variable.getInitializer().filter(Expression::isMethodCallExpr)
                .map(Expression::asMethodCallExpr)
                .filter(call -> isHazelcastListenerRegistration(call.getNameAsString()))
                .isPresent()) {
                variable.setType("java.util.UUID");
                changed = true;
            }
        }

        for (MethodDeclaration method : new ArrayList<>(cu.findAll(MethodDeclaration.class))) {
            for (var parameter : method.getParameters()) {
                if (!parameter.getType().isClassOrInterfaceType() || !"String".equals(parameter.getType().asString())) {
                    continue;
                }
                final String parameterName = parameter.getNameAsString();
                final boolean listenerRemovalUse = method.findAll(MethodCallExpr.class).stream()
                    .filter(call -> call.getArguments().stream().anyMatch(arg -> arg.toString().equals(parameterName)))
                    .anyMatch(call -> call.getNameAsString().startsWith("remove") && call.getNameAsString().endsWith("Listener"));
                if (listenerRemovalUse) {
                    parameter.setType("java.util.UUID");
                    changed = true;
                }
            }
        }

        for (AssignExpr assign : new ArrayList<>(cu.findAll(AssignExpr.class))) {
            if (assign.getValue().isMethodCallExpr()
                && isHazelcastListenerRegistration(assign.getValue().asMethodCallExpr().getNameAsString())) {
            }
        }

        return changed;
    }

    private static boolean isHazelcastListenerRegistration(String methodName) {
        return "addEntryListener".equals(methodName)
            || "addLocalEntryListener".equals(methodName)
            || "addLifecycleListener".equals(methodName)
            || "addMembershipListener".equals(methodName);
    }

    private static boolean rewriteMemberAttributeAccessors(CompilationUnit cu) {
        boolean changed = false;
        for (MethodCallExpr call : new ArrayList<>(cu.findAll(MethodCallExpr.class))) {
            String name = call.getNameAsString();
            if ("getStringAttribute".equals(name)) {
                call.setName("getAttribute");
                changed = true;
            } else if ("setStringAttribute".equals(name)) {
                call.setName("setAttribute");
                changed = true;
            }
        }
        return changed;
    }

    private static boolean addMissingEntryExpiredHandler(CompilationUnit cu) {
        boolean changed = false;
        for (ObjectCreationExpr creation : new ArrayList<>(cu.findAll(ObjectCreationExpr.class))) {
            if (!creation.getAnonymousClassBody().isPresent() || !creation.getType().asString().endsWith("EntryListener")) {
                continue;
            }
            boolean hasEntryExpired = creation.getAnonymousClassBody().get().stream()
                .filter(node -> node instanceof MethodDeclaration)
                .map(node -> (MethodDeclaration) node)
                .anyMatch(method -> "entryExpired".equals(method.getNameAsString()));
            if (!hasEntryExpired) {
                MethodDeclaration stub = StaticJavaParser.parseBodyDeclaration(
                    "@Override public void entryExpired(com.hazelcast.core.EntryEvent event) {}")
                    .asMethodDeclaration();
                creation.getAnonymousClassBody().get().add(stub);
                changed = true;
            }
        }
        return changed;
    }

    private static boolean removeDeprecatedMemberAttributeCallback(CompilationUnit cu) {
        boolean changed = false;
        for (MethodDeclaration method : new ArrayList<>(cu.findAll(MethodDeclaration.class))) {
            if ("memberAttributeChanged".equals(method.getNameAsString())
                && !method.getParameters().isEmpty()
                && method.getParameter(0).getType().asString().endsWith("MemberAttributeEvent")) {
                method.remove();
                changed = true;
            }
        }
        if (cu.toString().contains("MemberAttributeEvent")) {
            cu.getImports().removeIf(importDeclaration -> importDeclaration.getNameAsString().endsWith("MemberAttributeEvent"));
            changed = true;
        }
        return changed;
    }

    private static boolean isEvictionStyleMaxSizeConstructor(ObjectCreationExpr creation) {
        String typeName = creation.getType().asString();
        return "EvictionConfig".equals(typeName) || "com.hazelcast.config.EvictionConfig".equals(typeName)
            || "MaxSizeConfig".equals(typeName) || "com.hazelcast.config.MaxSizeConfig".equals(typeName);
    }

    private static Expression normalizeMaxSizePolicy(Expression expression) {
        String text = expression.toString();
        if (text.startsWith("MaxSizeConfig.MaxSizePolicy.")) {
            text = text.replace("MaxSizeConfig.", "com.hazelcast.config.");
        }
        return StaticJavaParser.parseExpression(text);
    }
}
