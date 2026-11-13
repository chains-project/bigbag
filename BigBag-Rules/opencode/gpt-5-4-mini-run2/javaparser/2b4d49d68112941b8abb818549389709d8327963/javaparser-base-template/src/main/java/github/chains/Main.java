package github.chains;

import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.type.ClassOrInterfaceType;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Main {
    private static final Map<String, String> IMPORT_RENAMES = new HashMap<>();

    static {
        IMPORT_RENAMES.put("com.hazelcast.core.Cluster", "com.hazelcast.cluster.Cluster");
        IMPORT_RENAMES.put("com.hazelcast.core.Member", "com.hazelcast.cluster.Member");
        IMPORT_RENAMES.put("com.hazelcast.core.MembershipEvent", "com.hazelcast.cluster.MembershipEvent");
        IMPORT_RENAMES.put("com.hazelcast.core.MembershipListener", "com.hazelcast.cluster.MembershipListener");
        IMPORT_RENAMES.put("com.hazelcast.core.IMap", "com.hazelcast.map.IMap");
        IMPORT_RENAMES.put("com.hazelcast.core.MapEvent", "com.hazelcast.map.MapEvent");
        IMPORT_RENAMES.put("com.hazelcast.monitor.LocalMapStats", "com.hazelcast.map.LocalMapStats");
        IMPORT_RENAMES.put("com.hazelcast.config.MaxSizeConfig", "com.hazelcast.config.EvictionConfig");
    }

    public static void main(String[] args) {
        if (args.length != 1) {
            throw new IllegalArgumentException("Expected exactly one argument: the source directory");
        }

        StaticJavaParser.setConfiguration(new ParserConfiguration().setLanguageLevel(ParserConfiguration.LanguageLevel.JAVA_11));

        Path root = Paths.get(args[0]);
        try {
            Files.walk(root)
                .filter(path -> Files.isRegularFile(path) && path.toString().endsWith(".java"))
                .forEach(Main::transformFile);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static void transformFile(Path path) {
        try {
            String original = Files.readString(path, StandardCharsets.UTF_8);
            CompilationUnit cu = StaticJavaParser.parse(original);

            renameImports(cu);
            renameMethodCalls(cu);
            rewriteMaxSizeConfigCalls(cu);
            removeMemberAttributeChangedMethods(cu);

            Files.writeString(path, cu.toString(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException("Failed to transform " + path, e);
        }
    }

    private static void renameImports(CompilationUnit cu) {
        List<ImportDeclaration> imports = cu.getImports();
        for (ImportDeclaration importDeclaration : imports) {
            String oldName = importDeclaration.getNameAsString();
            String replacement = IMPORT_RENAMES.get(oldName);
            if (replacement != null) {
                importDeclaration.setName(replacement);
            }
        }

        cu.getImports().removeIf(importDeclaration ->
            "com.hazelcast.core.MemberAttributeEvent".equals(importDeclaration.getNameAsString())
                || "com.hazelcast.config.MaxSizeConfig".equals(importDeclaration.getNameAsString())
        );
    }

    private static void renameMethodCalls(CompilationUnit cu) {
        for (MethodCallExpr call : cu.findAll(MethodCallExpr.class)) {
            if ("getStringAttribute".equals(call.getNameAsString())) {
                call.setName("getAttribute");
            } else if ("setStringAttribute".equals(call.getNameAsString())) {
                call.setName("setAttribute");
            }
        }
    }

    private static boolean isListenerFactoryMethod(String name) {
        return "addEntryListener".equals(name)
            || "addLifecycleListener".equals(name)
            || "addMembershipListener".equals(name)
            || "addDistributedObjectListener".equals(name);
    }

    private static void rewriteMaxSizeConfigCalls(CompilationUnit cu) {
        cu.findAll(MethodCallExpr.class).forEach(call -> {
            if ("setMaxSizeConfig".equals(call.getNameAsString()) && call.getArguments().size() == 1) {
                call.setName("setEvictionConfig");
                call.getArguments().set(0, buildEvictionConfigExpression(call.getArgument(0)));
            }
        });

        cu.findAll(ObjectCreationExpr.class).forEach(objectCreation -> {
            if ("MaxSizeConfig".equals(objectCreation.getType().getNameAsString()) && objectCreation.getArguments().size() == 2) {
                objectCreation.replace(buildEvictionConfigExpression(objectCreation));
            }
        });
    }

    private static com.github.javaparser.ast.expr.Expression buildEvictionConfigExpression(com.github.javaparser.ast.expr.Expression source) {
        if (source.isObjectCreationExpr()) {
            ObjectCreationExpr creation = source.asObjectCreationExpr();
            if (creation.getArguments().size() == 2) {
                com.github.javaparser.ast.expr.Expression size = creation.getArgument(0);
                com.github.javaparser.ast.expr.Expression policy = creation.getArgument(1);
                return StaticJavaParser.parseExpression("new com.hazelcast.config.EvictionConfig().setSize(" + size + ").setMaxSizePolicy(" + policy + ")");
            }
        }
        return StaticJavaParser.parseExpression("new com.hazelcast.config.EvictionConfig()");
    }

    private static void removeMemberAttributeChangedMethods(CompilationUnit cu) {
    }
}
