package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.printer.lexicalpreservation.LexicalPreservingPrinter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.HashMap;
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
        IMPORT_RENAMES.put("com.hazelcast.core.MemberAttributeEvent", null);
        IMPORT_RENAMES.put("com.hazelcast.config.MaxSizeConfig", null);
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 1) {
            throw new IllegalArgumentException("Usage: Main <source-directory>");
        }

        final Path sourceDir = Path.of(args[0]);
        if (!Files.isDirectory(sourceDir)) {
            throw new IllegalArgumentException("Not a directory: " + sourceDir);
        }

        Files.walk(sourceDir)
            .filter(path -> Files.isRegularFile(path) && path.toString().endsWith(".java"))
            .forEach(path -> {
                try {
                    transform(path);
                } catch (IOException e) {
                    throw new RuntimeException("Failed to transform " + path, e);
                }
            });
    }

    private static void transform(Path path) throws IOException {
        final CompilationUnit cu = StaticJavaParser.parse(path, StandardCharsets.UTF_8);
        LexicalPreservingPrinter.setup(cu);

        boolean changed = false;

        for (var importDecl : List.copyOf(cu.getImports())) {
            final String name = importDecl.getNameAsString();
            if (IMPORT_RENAMES.containsKey(name)) {
                final String replacement = IMPORT_RENAMES.get(name);
                if (replacement == null) {
                    importDecl.remove();
                } else {
                    importDecl.setName(replacement);
                }
                changed = true;
            }
        }

        for (MethodCallExpr methodCall : cu.findAll(MethodCallExpr.class)) {
            if ("getStringAttribute".equals(methodCall.getNameAsString())) {
                methodCall.setName("getAttribute");
                changed = true;
            }

            if ("setStringAttribute".equals(methodCall.getNameAsString())) {
                methodCall.setName("setAttribute");
                changed = true;
            }

            if ("addEntryListener".equals(methodCall.getNameAsString()) && methodCall.getArguments().size() >= 1) {
                methodCall.setName("addEntryListener");
                changed = true;
            }

            if ("removeEntryListener".equals(methodCall.getNameAsString()) && methodCall.getArguments().size() == 1) {
                final String replacement = String.format("java.util.UUID.fromString(%s)", methodCall.getArgument(0));
                methodCall.setArgument(0, StaticJavaParser.parseExpression(replacement));
                changed = true;
            }

            if (("addLifecycleListener".equals(methodCall.getNameAsString()) || "addMembershipListener".equals(methodCall.getNameAsString()))
                && methodCall.getArguments().size() == 1) {
                final String replacement = methodCall + ".toString()";
                methodCall.replace(StaticJavaParser.parseExpression(replacement));
                changed = true;
            }

            if (("removeLifecycleListener".equals(methodCall.getNameAsString()) || "removeMembershipListener".equals(methodCall.getNameAsString()))
                && methodCall.getArguments().size() == 1) {
                final String replacement = String.format("java.util.UUID.fromString(%s)", methodCall.getArgument(0));
                methodCall.setArgument(0, StaticJavaParser.parseExpression(replacement));
                changed = true;
            }

            if ("setMaxSizeConfig".equals(methodCall.getNameAsString()) && methodCall.getArguments().size() == 1) {
                final Expression argument = methodCall.getArgument(0);
                if (argument.isObjectCreationExpr()) {
                    final ObjectCreationExpr creationExpr = argument.asObjectCreationExpr();
                    if ("MaxSizeConfig".equals(creationExpr.getTypeAsString()) && creationExpr.getArguments().size() == 2) {
                        final Expression size = creationExpr.getArgument(0);
                        final Expression policy = creationExpr.getArgument(1);
                        final String replacement = String.format(
                            "new com.hazelcast.config.EvictionConfig().setMaxSizePolicy(%s).setSize(%s)",
                            policy,
                            size
                        );
                        methodCall.setName("setEvictionConfig");
                        methodCall.setArgument(0, StaticJavaParser.parseExpression(replacement));
                        changed = true;
                    }
                }
            }
        }

        for (ClassOrInterfaceDeclaration type : cu.findAll(ClassOrInterfaceDeclaration.class)) {
            boolean removed = false;
            for (MethodDeclaration method : List.copyOf(type.getMethodsByName("memberAttributeChanged"))) {
                method.remove();
                removed = true;
            }
            if (removed) {
                changed = true;
            }
        }

        if (changed) {
            ensureImports(cu);
            Files.writeString(path, LexicalPreservingPrinter.print(cu), StandardCharsets.UTF_8);
        }
    }

    private static void ensureImports(CompilationUnit cu) {
        if (referencesSimpleName(cu, "EvictionConfig")) {
            addImportIfMissing(cu, "com.hazelcast.config.EvictionConfig");
        }
        if (referencesSimpleName(cu, "MaxSizePolicy")) {
            addImportIfMissing(cu, "com.hazelcast.config.MaxSizePolicy");
        }
    }

    private static boolean referencesSimpleName(CompilationUnit cu, String simpleName) {
        return cu.findAll(ClassOrInterfaceType.class).stream()
            .anyMatch(type -> simpleName.equals(type.getNameAsString()))
            || cu.findAll(ObjectCreationExpr.class).stream()
            .anyMatch(expr -> simpleName.equals(expr.getType().getNameAsString()))
            || cu.findAll(MethodCallExpr.class).stream()
            .anyMatch(expr -> expr.getNameAsString().contains(simpleName));
    }

    private static void addImportIfMissing(CompilationUnit cu, String importName) {
        if (cu.getImports().stream().noneMatch(importDecl -> importDecl.getNameAsString().equals(importName))) {
            cu.addImport(importName);
        }
    }
}
