package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.visitor.ModifierVisitor;
import com.github.javaparser.ast.visitor.Visitable;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;

public class Main {
    public static void main(String[] args) throws Exception {
        String projectDir = "/workspace/openfire-hazelcast-plugin/src/java";
        List<Path> javaFiles = Files.walk(Paths.get(projectDir))
                .filter(p -> p.toString().endsWith(".java"))
                .collect(Collectors.toList());

        for (Path path : javaFiles) {
            CompilationUnit cu = StaticJavaParser.parse(path);
            boolean changed = false;

            // HazelcastClusterNodeInfo.java
            if (path.toString().endsWith("HazelcastClusterNodeInfo.java")) {
                cu.accept(new ModifierVisitor<Void>() {
                    @Override
                    public Visitable visit(MethodCallExpr n, Void arg) {
                        if (n.getNameAsString().equals("getStringAttribute")) {
                            n.setName("getAttribute");
                        }
                        return super.visit(n, arg);
                    }
                }, null);
                changed = true;
            }

            // ClusteredCache.java
            if (path.toString().endsWith("ClusteredCache.java")) {
                cu.accept(new ModifierVisitor<Void>() {
                    @Override
                    public Visitable visit(MethodCallExpr n, Void arg) {
                        if (n.getNameAsString().equals("addEntryListener") && n.getScope().isPresent() && n.getScope().get().toString().equals("map")) {
                            // map.addEntryListener -> map.addEntryListener(...).toString()
                            MethodCallExpr newExpr = new MethodCallExpr(n.clone(), "toString");
                            return newExpr;
                        }
                        if (n.getNameAsString().equals("removeEntryListener") && n.getScope().isPresent() && n.getScope().get().toString().equals("map")) {
                            // map.removeEntryListener(listenerId) -> map.removeEntryListener(java.util.UUID.fromString(listenerId))
                            if (n.getArguments().size() == 1 && n.getArgument(0).toString().equals("listenerId")) {
                                n.setArgument(0, StaticJavaParser.parseExpression("java.util.UUID.fromString(" + n.getArgument(0).toString() + ")"));
                            }
                            // and the one in destroy()
                            if (n.getArguments().size() == 1 && n.getArgument(0).toString().equals("id")) {
                                n.setArgument(0, StaticJavaParser.parseExpression("java.util.UUID.fromString(" + n.getArgument(0).toString() + ")"));
                            }
                        }
                        return super.visit(n, arg);
                    }
                }, null);

                // Add entryExpired to EntryListener
                cu.accept(new ModifierVisitor<Void>() {
                    @Override
                    public Visitable visit(ObjectCreationExpr n, Void arg) {
                        if (n.getType().getNameAsString().equals("EntryListener")) {
                            boolean hasEntryExpired = n.getAnonymousClassBody().get().stream()
                                    .filter(bd -> bd instanceof MethodDeclaration)
                                    .map(bd -> (MethodDeclaration) bd)
                                    .anyMatch(md -> md.getNameAsString().equals("entryExpired"));

                            if (!hasEntryExpired) {
                                MethodDeclaration entryExpired = StaticJavaParser.parseMethodDeclaration(
                                        "@Override\n" +
                                        "public void entryExpired(EntryEvent event) {\n" +
                                        "    if (includeEventsFromLocalNode || !event.getMember().localMember()) {\n" +
                                        "        final NodeID eventNodeId = ClusteredCacheFactory.getNodeID(event.getMember());\n" +
                                        "        logger.trace(\"Processing entry expired event of node '{}' for key '{}'\", eventNodeId, event.getKey());\n" +
                                        "        clusteredCacheEntryListener.entryEvicted((K) event.getKey(), (V) event.getOldValue(), eventNodeId);\n" +
                                        "    }\n" +
                                        "}"
                                );
                                n.getAnonymousClassBody().get().add(entryExpired);
                            }
                        }
                        return super.visit(n, arg);
                    }
                }, null);
                
                // also destroy uses listeners.forEach(map::removeEntryListener);
                // change to listeners.forEach(id -> map.removeEntryListener(java.util.UUID.fromString(id)));
                cu.accept(new ModifierVisitor<Void>() {
                    @Override
                    public Visitable visit(MethodCallExpr n, Void arg) {
                        if (n.getNameAsString().equals("forEach") && n.getScope().isPresent() && n.getScope().get().toString().equals("listeners")) {
                            if (n.getArguments().size() == 1 && n.getArgument(0).toString().equals("map::removeEntryListener")) {
                                n.setArgument(0, StaticJavaParser.parseExpression("id -> map.removeEntryListener(java.util.UUID.fromString(id))"));
                            }
                        }
                        return super.visit(n, arg);
                    }
                }, null);
                changed = true;
            }

            // ClusteredCacheFactory.java
            if (path.toString().endsWith("ClusteredCacheFactory.java")) {
                cu.accept(new ModifierVisitor<Void>() {
                    @Override
                    public Visitable visit(FieldDeclaration n, Void arg) {
                        if (n.getVariables().size() == 1) {
                            String name = n.getVariable(0).getNameAsString();
                            if (name.equals("lifecycleListener") || name.equals("membershipListener")) {
                                n.getVariable(0).setType("java.util.UUID");
                            }
                        }
                        return super.visit(n, arg);
                    }

                    @Override
                    public Visitable visit(MethodCallExpr n, Void arg) {
                        if (n.getNameAsString().equals("setStringAttribute")) {
                            n.setName("setAttribute");
                        }
                        if (n.getNameAsString().equals("getStringAttribute")) {
                            n.setName("getAttribute");
                        }
                        return super.visit(n, arg);
                    }
                }, null);
                changed = true;
            }

            if (changed) {
                Files.write(path, cu.toString().getBytes());
                System.out.println("Updated: " + path);
            }
        }
    }
}
