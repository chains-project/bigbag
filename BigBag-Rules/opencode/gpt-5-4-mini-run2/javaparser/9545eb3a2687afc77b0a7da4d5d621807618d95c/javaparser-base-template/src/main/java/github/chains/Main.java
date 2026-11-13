package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.printer.lexicalpreservation.LexicalPreservingPrinter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

public class Main {

    private static final Map<String, String> IMPORT_RENAMES = new HashMap<>();
    private static final Set<String> REMOVED_IMPORTS = new HashSet<>();

    static {
        IMPORT_RENAMES.put("com.hazelcast.core.Cluster", "com.hazelcast.cluster.Cluster");
        IMPORT_RENAMES.put("com.hazelcast.core.Member", "com.hazelcast.cluster.Member");
        IMPORT_RENAMES.put("com.hazelcast.core.MembershipEvent", "com.hazelcast.cluster.MembershipEvent");
        IMPORT_RENAMES.put("com.hazelcast.core.MembershipListener", "com.hazelcast.cluster.MembershipListener");
        IMPORT_RENAMES.put("com.hazelcast.core.IMap", "com.hazelcast.map.IMap");
        IMPORT_RENAMES.put("com.hazelcast.core.MapEvent", "com.hazelcast.map.MapEvent");
        IMPORT_RENAMES.put("com.hazelcast.monitor.LocalMapStats", "com.hazelcast.map.LocalMapStats");

        REMOVED_IMPORTS.add("com.hazelcast.core.MemberAttributeEvent");
        REMOVED_IMPORTS.add("com.hazelcast.config.MaxSizeConfig");
    }

    public static void main(String[] args) throws IOException {
        final Path root = args.length > 0 ? Paths.get(args[0]) : Paths.get(".");
        try (Stream<Path> paths = Files.walk(root)) {
            paths.filter(path -> path.toString().endsWith(".java")).forEach(Main::rewrite);
        }
    }

    private static void rewrite(Path file) {
        try {
            final CompilationUnit cu = StaticJavaParser.parse(file);
            LexicalPreservingPrinter.setup(cu);

            boolean changed = false;
            changed |= rewriteImports(cu);
            changed |= rewriteMethodNames(cu);
            changed |= removeObsoleteMethods(cu);
            changed |= rewriteMaxSizeConfigUsages(cu);
            changed |= rewriteListenerRegistrations(cu);
            changed |= rewriteListenerRemovals(cu);
            changed |= rewriteListenerCollections(cu);
            changed |= addMissingEntryExpiredMethods(cu);

            if (changed) {
                Files.writeString(file, LexicalPreservingPrinter.print(cu), StandardCharsets.UTF_8);
            }
        } catch (Exception ignored) {
        }
    }

    private static boolean rewriteImports(CompilationUnit cu) {
        boolean changed = false;
        final NodeList<ImportDeclaration> imports = cu.getImports();
        for (int i = imports.size() - 1; i >= 0; i--) {
            final ImportDeclaration importDeclaration = imports.get(i);
            final String importName = importDeclaration.getNameAsString();
            if (REMOVED_IMPORTS.contains(importName)) {
                importDeclaration.remove();
                changed = true;
                continue;
            }
            final String replacement = IMPORT_RENAMES.get(importName);
            if (replacement != null) {
                importDeclaration.setName(replacement);
                changed = true;
            }
        }
        return changed;
    }

    private static boolean rewriteMethodNames(CompilationUnit cu) {
        boolean changed = false;
        for (MethodCallExpr call : cu.findAll(MethodCallExpr.class)) {
            if ("getStringAttribute".equals(call.getNameAsString())) {
                call.setName("getAttribute");
                changed = true;
            } else if ("setMaxSizeConfig".equals(call.getNameAsString())) {
                call.setName("setEvictionConfig");
                changed = true;
            } else if ("setStringAttribute".equals(call.getNameAsString())) {
                call.setName("setAttribute");
                changed = true;
            } else if ("addEntryListener".equals(call.getNameAsString()) && call.getArguments().size() == 2) {
                // Hazelcast 5 returns UUID, so preserve the UUID instead of converting it to String.
                changed = true;
            }
        }
        return changed;
    }

    private static boolean removeObsoleteMethods(CompilationUnit cu) {
        boolean changed = false;
        for (MethodDeclaration method : cu.findAll(MethodDeclaration.class)) {
            if ("memberAttributeChanged".equals(method.getNameAsString()) && method.getParameters().size() == 1
                && method.getParameter(0).getType().asString().endsWith("MemberAttributeEvent")) {
                method.remove();
                changed = true;
            }
        }
        return changed;
    }

    private static boolean rewriteMaxSizeConfigUsages(CompilationUnit cu) {
        boolean changed = false;
        for (ObjectCreationExpr creation : cu.findAll(ObjectCreationExpr.class)) {
            if (!"MaxSizeConfig".equals(creation.getType().getNameAsString()) || creation.getArguments().size() != 2) {
                continue;
            }

            final Expression size = creation.getArgument(0).clone();
            final Expression policy = creation.getArgument(1).clone();
            final String replacement = "new EvictionConfig().setSize(" + size + ")"
                + ".setMaxSizePolicy(" + policy.toString().replace("MaxSizeConfig.MaxSizePolicy", "com.hazelcast.config.MaxSizePolicy") + ")";
            creation.replace(StaticJavaParser.parseExpression(replacement));
            changed = true;
        }
        return changed;
    }

    private static boolean rewriteListenerRegistrations(CompilationUnit cu) {
        boolean changed = false;
        for (VariableDeclarator variable : cu.findAll(VariableDeclarator.class)) {
            final Expression initializer = variable.getInitializer().orElse(null);
            if (initializer instanceof MethodCallExpr) {
                final MethodCallExpr call = (MethodCallExpr) initializer;
                if (isHazelcastListenerIdCall(call)) {
                    if ("String".equals(variable.getType().asString())) {
                        variable.setType("java.util.UUID");
                        changed = true;
                    }
                }
            }
        }
        return changed;
    }

    private static boolean rewriteListenerRemovals(CompilationUnit cu) {
        boolean changed = false;
        for (MethodCallExpr call : cu.findAll(MethodCallExpr.class)) {
            if (isHazelcastListenerRemovalCall(call) && call.getArguments().size() == 1) {
                final Expression argument = call.getArgument(0);
                if (argument.toString().endsWith(".toString()")) {
                    call.setArgument(0, StaticJavaParser.parseExpression(argument.toString().replace(".toString()", "")));
                    changed = true;
                } else if (!argument.toString().startsWith("java.util.UUID.fromString(")) {
                    call.setArgument(0, StaticJavaParser.parseExpression("java.util.UUID.fromString(" + argument + ")"));
                    changed = true;
                }
            }
        }
        return changed;
    }

    private static boolean isHazelcastListenerIdCall(MethodCallExpr call) {
        final String name = call.getNameAsString();
        return "addEntryListener".equals(name)
            || "addMembershipListener".equals(name)
            || "addLifecycleListener".equals(name)
            || "addEntryListener".equals(name);
    }

    private static boolean isHazelcastListenerRemovalCall(MethodCallExpr call) {
        final String name = call.getNameAsString();
        return "removeEntryListener".equals(name)
            || "removeMembershipListener".equals(name)
            || "removeLifecycleListener".equals(name);
    }

    private static boolean addMissingEntryExpiredMethods(CompilationUnit cu) {
        boolean changed = false;
        for (ObjectCreationExpr creation : cu.findAll(ObjectCreationExpr.class)) {
            if (creation.getAnonymousClassBody().isEmpty()) {
                continue;
            }
            final boolean hasEntryMethods = creation.getAnonymousClassBody().get().stream()
                .filter(MethodDeclaration.class::isInstance)
                .map(MethodDeclaration.class::cast)
                .anyMatch(method -> "entryAdded".equals(method.getNameAsString()) || "entryRemoved".equals(method.getNameAsString()));
            final boolean hasEntryExpired = creation.getAnonymousClassBody().get().stream()
                .filter(MethodDeclaration.class::isInstance)
                .map(MethodDeclaration.class::cast)
                .anyMatch(method -> "entryExpired".equals(method.getNameAsString()));
            if (hasEntryMethods && !hasEntryExpired) {
                creation.getAnonymousClassBody().get().add(
                    StaticJavaParser.parseBodyDeclaration("public void entryExpired(com.hazelcast.core.EntryEvent event) {}")
                );
                changed = true;
            }
        }
        return changed;
    }

    private static boolean rewriteListenerCollections(CompilationUnit cu) {
        boolean changed = false;
        for (FieldDeclaration field : cu.findAll(FieldDeclaration.class)) {
            if (field.getVariables().size() != 1) {
                continue;
            }
            final VariableDeclarator variable = field.getVariable(0);
            if (variable.getType().asString().equals("Set<String>")
                && variable.getNameAsString().toLowerCase().contains("listener")) {
                variable.setType("Set<java.util.UUID>");
                changed = true;
            }
        }
        return changed;
    }
}
