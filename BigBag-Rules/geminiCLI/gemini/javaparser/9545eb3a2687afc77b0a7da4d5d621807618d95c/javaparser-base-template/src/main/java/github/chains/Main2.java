package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.VariableDeclarationExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.stmt.ExpressionStmt;

import java.io.File;
import java.nio.file.Files;

public class Main2 {
    public static void main(String[] args) throws Exception {
        String basePath = "/workspace/openfire-hazelcast-plugin/src/java/org/jivesoftware/openfire/plugin/";

        // 1. HazelcastClusterNodeInfo.java
        File f1 = new File(basePath + "util/cluster/HazelcastClusterNodeInfo.java");
        CompilationUnit cu1 = StaticJavaParser.parse(f1);
        cu1.findAll(MethodCallExpr.class).forEach(mc -> {
            if (mc.getNameAsString().equals("getStringAttribute")) {
                mc.setName("getAttribute");
            }
        });
        Files.write(f1.toPath(), cu1.toString().getBytes());

        // 2. ClusteredCache.java
        File f2 = new File(basePath + "util/cache/ClusteredCache.java");
        CompilationUnit cu2 = StaticJavaParser.parse(f2);
        if (cu2.getImports().stream().noneMatch(i -> i.getNameAsString().equals("java.util.UUID"))) {
            cu2.addImport("java.util.UUID");
        }
        
        cu2.findAll(MethodCallExpr.class).forEach(mc -> {
            if (mc.getNameAsString().equals("addEntryListener") && mc.getScope().isPresent() && mc.getScope().get().toString().equals("map")) {
                // UUID id = map.addEntryListener(...) -> map.addEntryListener(...).toString()
                if (!(mc.getParentNode().get() instanceof MethodCallExpr && ((MethodCallExpr)mc.getParentNode().get()).getNameAsString().equals("toString"))) {
                    MethodCallExpr toStringCall = new MethodCallExpr(mc.clone(), "toString");
                    mc.replace(toStringCall);
                }
            } else if (mc.getNameAsString().equals("removeEntryListener") && mc.getScope().isPresent() && mc.getScope().get().toString().equals("map")) {
                // map.removeEntryListener(listenerId) -> map.removeEntryListener(UUID.fromString(listenerId))
                Expression arg = mc.getArgument(0);
                if (!arg.toString().startsWith("UUID.fromString")) {
                    MethodCallExpr uuidCall = new MethodCallExpr(new NameExpr("UUID"), "fromString");
                    uuidCall.addArgument(arg.clone());
                    mc.setArgument(0, uuidCall);
                }
            } else if (mc.getNameAsString().equals("forEach") && mc.getScope().isPresent() && mc.getScope().get().toString().equals("listeners")) {
                // listeners.forEach(map::removeEntryListener);
                // change to listeners.forEach(id -> map.removeEntryListener(UUID.fromString(id)));
                Expression newExpr = StaticJavaParser.parseExpression("id -> map.removeEntryListener(UUID.fromString(id))");
                mc.setArgument(0, newExpr);
            }
        });
        
        // Add entryExpired method to the anonymous EntryListener class
        cu2.findAll(ObjectCreationExpr.class).forEach(oce -> {
            if (oce.getType().getNameAsString().equals("EntryListener")) {
                boolean hasExpired = oce.getAnonymousClassBody().get().stream()
                    .filter(bd -> bd instanceof MethodDeclaration)
                    .map(bd -> (MethodDeclaration) bd)
                    .anyMatch(md -> md.getNameAsString().equals("entryExpired"));
                    
                if (!hasExpired) {
                    MethodDeclaration md = StaticJavaParser.parseMethodDeclaration(
                        "@Override\n" +
                        "public void entryExpired(EntryEvent event) {\n" +
                        "    if (includeEventsFromLocalNode || !event.getMember().localMember()) {\n" +
                        "        final NodeID eventNodeId = ClusteredCacheFactory.getNodeID(event.getMember());\n" +
                        "        logger.trace(\"Processing entry expired event of node '{}' for key '{}'\", eventNodeId, event.getKey());\n" +
                        "        clusteredCacheEntryListener.entryEvicted((K) event.getKey(), (V) event.getOldValue(), eventNodeId);\n" +
                        "    }\n" +
                        "}");
                    oce.getAnonymousClassBody().get().add(md);
                }
            }
        });
        Files.write(f2.toPath(), cu2.toString().getBytes());

        // 3. ClusteredCacheFactory.java
        File f3 = new File(basePath + "util/cache/ClusteredCacheFactory.java");
        CompilationUnit cu3 = StaticJavaParser.parse(f3);
        if (cu3.getImports().stream().noneMatch(i -> i.getNameAsString().equals("java.util.UUID"))) {
            cu3.addImport("java.util.UUID");
        }
        
        cu3.findAll(MethodCallExpr.class).forEach(mc -> {
            if (mc.getNameAsString().equals("setStringAttribute")) {
                mc.setName("setAttribute");
            } else if (mc.getNameAsString().equals("getStringAttribute")) {
                mc.setName("getAttribute");
            } else if (mc.getNameAsString().equals("addLifecycleListener")) {
                if (!(mc.getParentNode().get() instanceof MethodCallExpr && ((MethodCallExpr)mc.getParentNode().get()).getNameAsString().equals("toString"))) {
                    MethodCallExpr toStringCall = new MethodCallExpr(mc.clone(), "toString");
                    mc.replace(toStringCall);
                }
            } else if (mc.getNameAsString().equals("addMembershipListener")) {
                if (!(mc.getParentNode().get() instanceof MethodCallExpr && ((MethodCallExpr)mc.getParentNode().get()).getNameAsString().equals("toString"))) {
                    MethodCallExpr toStringCall = new MethodCallExpr(mc.clone(), "toString");
                    mc.replace(toStringCall);
                }
            } else if (mc.getNameAsString().equals("removeLifecycleListener") || mc.getNameAsString().equals("removeMembershipListener")) {
                Expression arg = mc.getArgument(0);
                if (arg instanceof NameExpr && !arg.toString().startsWith("UUID.fromString")) {
                    MethodCallExpr uuidCall = new MethodCallExpr(new NameExpr("UUID"), "fromString");
                    uuidCall.addArgument(arg.clone());
                    mc.setArgument(0, uuidCall);
                }
            }
        });
        Files.write(f3.toPath(), cu3.toString().getBytes());
        System.out.println("Transformation 2 complete.");
    }
}
