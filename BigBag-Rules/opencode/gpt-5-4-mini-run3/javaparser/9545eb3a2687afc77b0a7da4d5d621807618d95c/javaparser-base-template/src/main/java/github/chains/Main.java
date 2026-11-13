package github.chains;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.FieldAccessExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.stmt.ExpressionStmt;
import com.github.javaparser.ast.stmt.Statement;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.visitor.Visitable;
import com.github.javaparser.ast.visitor.ModifierVisitor;
import com.github.javaparser.printer.lexicalpreservation.LexicalPreservingPrinter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class Main {
    public static void main(String[] args) {
        if (args.length < 1) {
            throw new IllegalArgumentException("Usage: Main <source-dir> [output-dir]");
        }

        final Path sourceDir = Paths.get(args[0]).toAbsolutePath().normalize();
        final Path outputDir = args.length > 1 ? Paths.get(args[1]).toAbsolutePath().normalize() : sourceDir;

        final ParserConfiguration configuration = new ParserConfiguration()
            .setLanguageLevel(ParserConfiguration.LanguageLevel.JAVA_11);
        final JavaParser parser = new JavaParser(configuration);

        try {
            Files.walk(sourceDir)
                .filter(path -> Files.isRegularFile(path) && path.toString().endsWith(".java"))
                .forEach(path -> {
                    try {
                        final CompilationUnit cu = parser.parse(path).getResult()
                            .orElseThrow(() -> new IllegalStateException("Unable to parse " + path));
                        LexicalPreservingPrinter.setup(cu);
                        new Hazelcast51Transformer().visit(cu, null);
                        final Path target = outputDir.resolve(sourceDir.relativize(path));
                        Files.createDirectories(target.getParent());
                        Files.writeString(target, LexicalPreservingPrinter.print(cu), StandardCharsets.UTF_8);
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                });
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static final class Hazelcast51Transformer extends ModifierVisitor<Void> {
        private static final Map<String, String> IMPORT_RENAMES = new HashMap<>();
        private static final Set<String> IMPORTS_TO_DROP = new HashSet<>();

        static {
            IMPORT_RENAMES.put("com.hazelcast.core.Cluster", "com.hazelcast.cluster.Cluster");
            IMPORT_RENAMES.put("com.hazelcast.core.Member", "com.hazelcast.cluster.Member");
            IMPORT_RENAMES.put("com.hazelcast.core.MembershipEvent", "com.hazelcast.cluster.MembershipEvent");
            IMPORT_RENAMES.put("com.hazelcast.core.MembershipListener", "com.hazelcast.cluster.MembershipListener");
            IMPORT_RENAMES.put("com.hazelcast.core.IMap", "com.hazelcast.map.IMap");
            IMPORT_RENAMES.put("com.hazelcast.core.MapEvent", "com.hazelcast.map.MapEvent");
            IMPORT_RENAMES.put("com.hazelcast.monitor.LocalMapStats", "com.hazelcast.map.LocalMapStats");

            IMPORTS_TO_DROP.add("com.hazelcast.core.MemberAttributeEvent");
            IMPORTS_TO_DROP.add("com.hazelcast.config.MaxSizeConfig");
        }

        @Override
        public Visitable visit(CompilationUnit cu, Void arg) {
            rewriteImports(cu);
            removeObsoleteMemberAttributeCallbacks(cu);
            return super.visit(cu, arg);
        }

        @Override
        public Visitable visit(MethodCallExpr n, Void arg) {
            if (isListenerRegistration(n)) {
                return new MethodCallExpr(n, "toString");
            }
            if (isListenerRemoval(n) && n.getArguments().size() == 1) {
                return new MethodCallExpr(n.getScope().orElse(null), n.getName(),
                    NodeList.nodeList(new MethodCallExpr(new NameExpr("UUID"), "fromString", NodeList.nodeList(n.getArgument(0).clone()))));
            }
            if ("setStringAttribute".equals(n.getNameAsString()) && n.getArguments().size() == 2) {
                return new MethodCallExpr(n.getScope().orElse(null), "setAttribute", n.getArguments());
            }
            if ("getStringAttribute".equals(n.getNameAsString()) && n.getArguments().size() == 1) {
                return new MethodCallExpr(n.getScope().orElse(null), "getAttribute", n.getArguments());
            }
            if ("setMaxSizeConfig".equals(n.getNameAsString()) && n.getArguments().size() == 1 && n.getArgument(0) instanceof ObjectCreationExpr) {
                final ObjectCreationExpr creation = (ObjectCreationExpr) n.getArgument(0);
                if (isMaxSizeConfigCreation(creation)) {
                    final Expression size = creation.getArgument(0).clone();
                    final Expression policy = rewriteMaxSizePolicyExpr(creation.getArgument(1).clone());
                    final MethodCallExpr evictionConfig = new MethodCallExpr(new ObjectCreationExpr(null, type("EvictionConfig"), new NodeList<>()), "setSize", NodeList.nodeList(size));
                    final MethodCallExpr configured = new MethodCallExpr(evictionConfig, "setMaxSizePolicy", NodeList.nodeList(policy));
                    final MethodCallExpr replacement = new MethodCallExpr(n.getScope().orElse(null), "setEvictionConfig", NodeList.nodeList(configured));
                    return replacement;
                }
            }
            return super.visit(n, arg);
        }

        @Override
        public Visitable visit(ObjectCreationExpr n, Void arg) {
            if (isAnonymousEntryListener(n)) {
                ensureEntryExpiredMethod(n);
            }
            if (isTypeNamed(n.getType(), "MaxSizeConfig")) {
                return n;
            }
            return super.visit(n, arg);
        }

        @Override
        public Visitable visit(FieldAccessExpr n, Void arg) {
            if ("MaxSizeConfig".equals(n.getScope().toString()) && "MaxSizePolicy".equals(n.getNameAsString())) {
                return new NameExpr("MaxSizePolicy");
            }
            return super.visit(n, arg);
        }

        @Override
        public Visitable visit(ClassOrInterfaceType n, Void arg) {
            final String name = n.toString();
            if ("MaxSizeConfig".equals(name)) {
                return type("EvictionConfig");
            }
            if ("com.hazelcast.core.Cluster".equals(name)) {
                return type("com.hazelcast.cluster.Cluster");
            }
            if ("com.hazelcast.core.Member".equals(name)) {
                return type("com.hazelcast.cluster.Member");
            }
            if ("com.hazelcast.core.MembershipEvent".equals(name)) {
                return type("com.hazelcast.cluster.MembershipEvent");
            }
            if ("com.hazelcast.core.MembershipListener".equals(name)) {
                return type("com.hazelcast.cluster.MembershipListener");
            }
            if ("com.hazelcast.core.IMap".equals(name)) {
                return type("com.hazelcast.map.IMap");
            }
            if ("com.hazelcast.core.MapEvent".equals(name)) {
                return type("com.hazelcast.map.MapEvent");
            }
            if ("com.hazelcast.monitor.LocalMapStats".equals(name)) {
                return type("com.hazelcast.map.LocalMapStats");
            }
            return super.visit(n, arg);
        }

        private void rewriteImports(CompilationUnit cu) {
            final List<com.github.javaparser.ast.ImportDeclaration> imports = List.copyOf(cu.getImports());

            for (com.github.javaparser.ast.ImportDeclaration importDecl : imports) {
                final String oldImport = importDecl.getNameAsString();
                if (IMPORTS_TO_DROP.contains(oldImport)) {
                    cu.getImports().remove(importDecl);
                } else if (IMPORT_RENAMES.containsKey(oldImport)) {
                    cu.getImports().remove(importDecl);
                    cu.addImport(IMPORT_RENAMES.get(oldImport));
                }
            }

            ensureImport(cu, "com.hazelcast.config.EvictionConfig");
            ensureImport(cu, "com.hazelcast.config.MaxSizePolicy");
        }

        private void ensureImport(CompilationUnit cu, String fqcn) {
            if (cu.getImports().stream().noneMatch(i -> i.getNameAsString().equals(fqcn))) {
                cu.addImport(fqcn);
            }
        }

        private void removeObsoleteMemberAttributeCallbacks(CompilationUnit cu) {
            cu.findAll(MethodDeclaration.class).stream()
                .filter(method -> "memberAttributeChanged".equals(method.getNameAsString()))
                .filter(method -> method.getParameters().size() == 1)
                .filter(method -> method.getParameter(0).getType().toString().contains("MemberAttributeEvent"))
                .forEach(method -> method.remove());
        }

        private boolean isMaxSizeConfigCreation(ObjectCreationExpr creation) {
            return isTypeNamed(creation.getType(), "MaxSizeConfig") && creation.getArguments().size() == 2;
        }

        private boolean isTypeNamed(ClassOrInterfaceType type, String simpleName) {
            return type.getNameAsString().equals(simpleName) || type.toString().equals(simpleName);
        }

        private ClassOrInterfaceType type(String name) {
            return new ClassOrInterfaceType(null, name);
        }

        private boolean isListenerRegistration(MethodCallExpr n) {
            final String name = n.getNameAsString();
            return ("addEntryListener".equals(name) || "addMembershipListener".equals(name) || "addLifecycleListener".equals(name))
                && n.getParentNode().map(parent -> !(parent instanceof MethodCallExpr && "toString".equals(((MethodCallExpr) parent).getNameAsString()))).orElse(true);
        }

        private boolean isListenerRemoval(MethodCallExpr n) {
            final String name = n.getNameAsString();
            return "removeEntryListener".equals(name) || "removeMembershipListener".equals(name) || "removeLifecycleListener".equals(name);
        }

        private boolean isAnonymousEntryListener(ObjectCreationExpr n) {
            return n.getAnonymousClassBody().isPresent() && n.getType().toString().contains("EntryListener");
        }

        private void ensureEntryExpiredMethod(ObjectCreationExpr n) {
            n.getAnonymousClassBody().ifPresent(list -> {
                final boolean hasEntryExpired = list.stream()
                    .filter(MethodDeclaration.class::isInstance)
                    .map(MethodDeclaration.class::cast)
                    .anyMatch(method -> "entryExpired".equals(method.getNameAsString()));
                if (!hasEntryExpired) {
                    final MethodDeclaration entryExpired = new MethodDeclaration();
                    entryExpired.setType("void");
                    entryExpired.setName("entryExpired");
                    entryExpired.addParameter(new Parameter(type("EntryEvent"), "event"));
                    final BlockStmt body = new BlockStmt();
                    body.addStatement(new ExpressionStmt(new MethodCallExpr(null, "entryEvicted", NodeList.nodeList(new NameExpr("event")))));
                    entryExpired.setBody(body);
                    list.add(entryExpired);
                }
            });
        }

        private Expression rewriteMaxSizePolicyExpr(Expression expression) {
            final String text = expression.toString();
            if (text.contains("MaxSizeConfig.MaxSizePolicy.")) {
                final String suffix = text.substring(text.lastIndexOf('.') + 1);
                return new FieldAccessExpr(new NameExpr("MaxSizePolicy"), suffix);
            }
            return expression;
        }
    }
}
