package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.FieldAccessExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.ImportDeclaration;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

public class Main {
    private static final Map<String, String> IMPORT_REWRITES = new HashMap<>();

    static {
        IMPORT_REWRITES.put("com.hazelcast.core.Cluster", "com.hazelcast.cluster.Cluster");
        IMPORT_REWRITES.put("com.hazelcast.core.Member", "com.hazelcast.cluster.Member");
        IMPORT_REWRITES.put("com.hazelcast.core.MembershipEvent", "com.hazelcast.cluster.MembershipEvent");
        IMPORT_REWRITES.put("com.hazelcast.core.MembershipListener", "com.hazelcast.cluster.MembershipListener");
        IMPORT_REWRITES.put("com.hazelcast.core.IMap", "com.hazelcast.map.IMap");
        IMPORT_REWRITES.put("com.hazelcast.core.MapEvent", "com.hazelcast.map.MapEvent");
        IMPORT_REWRITES.put("com.hazelcast.monitor.LocalMapStats", "com.hazelcast.map.LocalMapStats");
        IMPORT_REWRITES.put("com.hazelcast.config.MaxSizeConfig", "com.hazelcast.config.EvictionConfig");
    }

    public static void main(String[] args) throws IOException {
        Path root = Paths.get(args.length > 0 ? args[0] : ".");
        try (Stream<Path> paths = Files.walk(root)) {
            paths.filter(path -> path.toString().endsWith(".java")).forEach(Main::rewriteFile);
        }
    }

    private static void rewriteFile(Path path) {
        try {
            CompilationUnit cu = StaticJavaParser.parse(path, StandardCharsets.UTF_8);
            boolean changed = false;
            changed |= rewriteImports(cu);
            changed |= rewriteTypeNames(cu);
            changed |= removeDeprecatedMemberAttributeListener(cu);
            changed |= rewriteMethodCalls(cu);
            changed |= rewriteMaxSizeConstructors(cu);
            if (changed) {
                Files.writeString(path, cu.toString(), StandardCharsets.UTF_8);
            }
        } catch (Exception ignored) {
            // Leave files that cannot be parsed untouched.
        }
    }

    private static boolean rewriteImports(CompilationUnit cu) {
        boolean changed = false;
        for (ImportDeclaration importDeclaration : cu.getImports()) {
            String imported = importDeclaration.getNameAsString();
            String replacement = IMPORT_REWRITES.get(imported);
            if (replacement != null) {
                importDeclaration.setName(StaticJavaParser.parseName(replacement));
                changed = true;
            }
        }

        if (cu.getImports().removeIf(importDeclaration ->
                "com.hazelcast.core.MemberAttributeEvent".equals(importDeclaration.getNameAsString())
                        || "com.hazelcast.cluster.MemberAttributeEvent".equals(importDeclaration.getNameAsString()))) {
            changed = true;
        }

        if (cu.getImports().stream().noneMatch(i -> i.getNameAsString().equals("com.hazelcast.config.EvictionConfig"))
                && containsMaxSizeUsage(cu)) {
            cu.addImport("com.hazelcast.config.EvictionConfig");
            changed = true;
        }

        if (cu.getImports().stream().noneMatch(i -> i.getNameAsString().equals("com.hazelcast.config.MaxSizePolicy"))
                && containsMaxSizeUsage(cu)) {
            cu.addImport("com.hazelcast.config.MaxSizePolicy");
            changed = true;
        }

        return changed;
    }

    private static boolean containsMaxSizeUsage(CompilationUnit cu) {
        return cu.findAll(ObjectCreationExpr.class).stream().anyMatch(expr -> expr.getType().getNameAsString().equals("MaxSizeConfig"))
                || cu.findAll(MethodCallExpr.class).stream().anyMatch(call -> call.getNameAsString().equals("setMaxSizeConfig"));
    }

    private static boolean rewriteTypeNames(CompilationUnit cu) {
        boolean changed = false;
        for (ClassOrInterfaceType type : cu.findAll(ClassOrInterfaceType.class)) {
            String name = type.getNameAsString();
            if ("MaxSizeConfig".equals(name)) {
                type.setName("EvictionConfig");
                changed = true;
            }
        }
        return changed;
    }

    private static boolean removeDeprecatedMemberAttributeListener(CompilationUnit cu) {
        boolean changed = false;
        for (MethodDeclaration method : cu.findAll(MethodDeclaration.class)) {
            if (!"memberAttributeChanged".equals(method.getNameAsString()) || method.getParameters().size() != 1) {
                continue;
            }
            String parameterType = method.getParameter(0).getType().asString();
            if ("MemberAttributeEvent".equals(parameterType) || "com.hazelcast.core.MemberAttributeEvent".equals(parameterType)) {
                method.getParentNode().ifPresent(parent -> parent.remove(method));
                changed = true;
            }
        }
        return changed;
    }

    private static boolean rewriteMethodCalls(CompilationUnit cu) {
        boolean changed = false;
        for (MethodCallExpr call : cu.findAll(MethodCallExpr.class)) {
            if ("setMaxSizeConfig".equals(call.getNameAsString())) {
                call.setName("setEvictionConfig");
                changed = true;
            }
        }
        return changed;
    }

    private static boolean rewriteMaxSizeConstructors(CompilationUnit cu) {
        boolean changed = false;
        for (ObjectCreationExpr creation : cu.findAll(ObjectCreationExpr.class)) {
            if (!"MaxSizeConfig".equals(creation.getType().getNameAsString())) {
                continue;
            }

            NodeList<Expression> args = creation.getArguments();
            if (args.size() < 2) {
                continue;
            }

            Expression sizeArg = args.get(0).clone();
            Expression policyArg = normalizeMaxSizePolicy(args.get(1).clone());

            ObjectCreationExpr evictionConfig = new ObjectCreationExpr(null, new ClassOrInterfaceType(null, "EvictionConfig"), new NodeList<>());
            MethodCallExpr setSize = new MethodCallExpr(evictionConfig, "setSize");
            setSize.addArgument(sizeArg);
            MethodCallExpr setPolicy = new MethodCallExpr(setSize, "setMaxSizePolicy");
            setPolicy.addArgument(policyArg);

            creation.replace(setPolicy);
            changed = true;
        }
        return changed;
    }

    private static Expression normalizeMaxSizePolicy(Expression expression) {
        if (expression instanceof FieldAccessExpr) {
            FieldAccessExpr fieldAccessExpr = (FieldAccessExpr) expression;
            Expression scope = fieldAccessExpr.getScope();
            if (scope instanceof FieldAccessExpr) {
                FieldAccessExpr nested = (FieldAccessExpr) scope;
                Expression nestedScope = nested.getScope();
                if (nestedScope instanceof NameExpr) {
                    NameExpr nameExpr = (NameExpr) nestedScope;
                    if ("MaxSizeConfig".equals(nameExpr.getNameAsString())
                            && "MaxSizePolicy".equals(nested.getNameAsString())) {
                        fieldAccessExpr.setScope(new NameExpr("MaxSizePolicy"));
                    }
                }
            }
        }
        return expression;
    }
}
