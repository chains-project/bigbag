package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.FieldAccessExpr;

import java.io.File;
import java.nio.file.Files;
import java.util.List;

public class Main {
    public static void main(String[] args) throws Exception {
        String basePath = "/workspace/openfire-hazelcast-plugin/src/java/org/jivesoftware/openfire/plugin/";

        // 1. HazelcastClusterNodeInfo.java
        File f1 = new File(basePath + "util/cluster/HazelcastClusterNodeInfo.java");
        CompilationUnit cu1 = StaticJavaParser.parse(f1);
        cu1.getImports().forEach(i -> {
            if (i.getNameAsString().equals("com.hazelcast.core.Member")) {
                i.setName("com.hazelcast.cluster.Member");
            }
        });
        Files.write(f1.toPath(), cu1.toString().getBytes());

        // 2. ClusterListener.java
        File f2 = new File(basePath + "util/cache/ClusterListener.java");
        CompilationUnit cu2 = StaticJavaParser.parse(f2);
        cu2.getImports().removeIf(i -> i.getNameAsString().equals("com.hazelcast.core.MemberAttributeEvent"));
        cu2.getImports().forEach(i -> {
            String name = i.getNameAsString();
            if (name.equals("com.hazelcast.core.Cluster")) {
                i.setName("com.hazelcast.cluster.Cluster");
            } else if (name.equals("com.hazelcast.core.Member")) {
                i.setName("com.hazelcast.cluster.Member");
            } else if (name.equals("com.hazelcast.core.MembershipEvent")) {
                i.setName("com.hazelcast.cluster.MembershipEvent");
            } else if (name.equals("com.hazelcast.core.MembershipListener")) {
                i.setName("com.hazelcast.cluster.MembershipListener");
            }
        });
        List<MethodDeclaration> methods = cu2.findAll(MethodDeclaration.class);
        methods.forEach(m -> {
            if (m.getNameAsString().equals("memberAttributeChanged")) {
                m.remove();
            }
        });
        Files.write(f2.toPath(), cu2.toString().getBytes());

        // 3. ClusteredCache.java
        File f3 = new File(basePath + "util/cache/ClusteredCache.java");
        CompilationUnit cu3 = StaticJavaParser.parse(f3);
        cu3.getImports().forEach(i -> {
            String name = i.getNameAsString();
            if (name.equals("com.hazelcast.core.IMap")) {
                i.setName("com.hazelcast.map.IMap");
            } else if (name.equals("com.hazelcast.core.MapEvent")) {
                i.setName("com.hazelcast.map.MapEvent");
            } else if (name.equals("com.hazelcast.monitor.LocalMapStats")) {
                i.setName("com.hazelcast.map.LocalMapStats");
            }
        });
        Files.write(f3.toPath(), cu3.toString().getBytes());

        // 4. ClusteredCacheFactory.java
        File f4 = new File(basePath + "util/cache/ClusteredCacheFactory.java");
        CompilationUnit cu4 = StaticJavaParser.parse(f4);
        boolean addedMaxSizePolicy = false;
        boolean addedEvictionConfig = false;
        for (ImportDeclaration i : cu4.getImports()) {
            String name = i.getNameAsString();
            if (name.equals("com.hazelcast.core.Cluster")) {
                i.setName("com.hazelcast.cluster.Cluster");
            } else if (name.equals("com.hazelcast.core.Member")) {
                i.setName("com.hazelcast.cluster.Member");
            } else if (name.equals("com.hazelcast.config.MaxSizeConfig")) {
                i.setName("com.hazelcast.config.EvictionConfig");
                addedEvictionConfig = true;
            }
        }
        if (!addedEvictionConfig) {
            cu4.addImport("com.hazelcast.config.EvictionConfig");
        }
        cu4.addImport("com.hazelcast.config.MaxSizePolicy");
        
        cu4.findAll(MethodCallExpr.class).forEach(mc -> {
            if (mc.getNameAsString().equals("setMaxSizeConfig")) {
                mc.setName("setEvictionConfig");
                mc.getArguments().clear();
                
                ObjectCreationExpr evictionConfig = new ObjectCreationExpr()
                    .setType("EvictionConfig");
                
                MethodCallExpr setSizeCall = new MethodCallExpr(evictionConfig, "setSize");
                setSizeCall.addArgument("hazelcastMaxCacheSizeInMegaBytes");
                
                MethodCallExpr setPolicyCall = new MethodCallExpr(setSizeCall, "setMaxSizePolicy");
                setPolicyCall.addArgument(new FieldAccessExpr(new NameExpr("MaxSizePolicy"), "USED_HEAP_SIZE"));
                
                mc.addArgument(setPolicyCall);
            }
        });
        Files.write(f4.toPath(), cu4.toString().getBytes());
        System.out.println("Transformation complete.");
    }
}
