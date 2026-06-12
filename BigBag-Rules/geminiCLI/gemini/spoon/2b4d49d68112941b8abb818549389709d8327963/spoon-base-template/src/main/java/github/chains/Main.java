package github.chains;

import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.code.CtStatement;
import spoon.reflect.declaration.CtImport;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.reference.CtExecutableReference;
import spoon.reflect.visitor.filter.TypeFilter;
import spoon.reflect.cu.CompilationUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Main {
    public static void main(String[] args) {
        Launcher launcher = new Launcher();
        launcher.addInputResource("/workspace/openfire-hazelcast-plugin/src/java");
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true);
        
        CtModel model = launcher.buildModel();
        
        Map<String, String> replacements = new HashMap<>();
        replacements.put("com.hazelcast.core.Member", "com.hazelcast.cluster.Member");
        replacements.put("com.hazelcast.core.Cluster", "com.hazelcast.cluster.Cluster");
        replacements.put("com.hazelcast.core.MemberAttributeEvent", "com.hazelcast.cluster.MemberAttributeEvent");
        replacements.put("com.hazelcast.core.MembershipEvent", "com.hazelcast.cluster.MembershipEvent");
        replacements.put("com.hazelcast.core.MembershipListener", "com.hazelcast.cluster.MembershipListener");
        replacements.put("com.hazelcast.core.IMap", "com.hazelcast.map.IMap");
        replacements.put("com.hazelcast.core.MapEvent", "com.hazelcast.map.MapEvent");
        replacements.put("com.hazelcast.monitor.LocalMapStats", "com.hazelcast.map.LocalMapStats");
        
        for (CtExecutableReference<?> execRef : model.getElements(new TypeFilter<>(CtExecutableReference.class))) {
            for (CtTypeReference<?> paramRef : execRef.getParameters()) {
                if (replacements.containsKey(paramRef.getQualifiedName())) {
                    String newName = replacements.get(paramRef.getQualifiedName());
                    paramRef.setSimpleName(newName.substring(newName.lastIndexOf('.') + 1));
                    paramRef.getPackage().setSimpleName(newName.substring(0, newName.lastIndexOf('.')));
                }
            }
            if (execRef.getType() != null && replacements.containsKey(execRef.getType().getQualifiedName())) {
                String newName = replacements.get(execRef.getType().getQualifiedName());
                execRef.getType().setSimpleName(newName.substring(newName.lastIndexOf('.') + 1));
                execRef.getType().getPackage().setSimpleName(newName.substring(0, newName.lastIndexOf('.')));
            }
        }
        
        for (CtTypeReference<?> ref : model.getElements(new TypeFilter<>(CtTypeReference.class))) {
            String oldName = ref.getQualifiedName();
            if (replacements.containsKey(oldName)) {
                String newName = replacements.get(oldName);
                ref.setSimpleName(newName.substring(newName.lastIndexOf('.') + 1));
                if (ref.getPackage() != null) {
                    ref.getPackage().setSimpleName(newName.substring(0, newName.lastIndexOf('.')));
                }
            }
        }
        
        for (CompilationUnit cu : launcher.getFactory().CompilationUnit().getMap().values()) {
            List<CtImport> newImports = new ArrayList<>();
            for (CtImport imp : cu.getImports()) {
                String impStr = imp.toString();
                if (impStr.contains("com.hazelcast.core.Member;") || 
                    impStr.contains("com.hazelcast.core.Cluster;") ||
                    impStr.contains("com.hazelcast.core.MemberAttributeEvent;") ||
                    impStr.contains("com.hazelcast.cluster.MemberAttributeEvent;") ||
                    impStr.contains("com.hazelcast.core.MembershipEvent;") ||
                    impStr.contains("com.hazelcast.core.MembershipListener;") ||
                    impStr.contains("com.hazelcast.core.IMap;") ||
                    impStr.contains("com.hazelcast.core.MapEvent;") ||
                    impStr.contains("com.hazelcast.monitor.LocalMapStats;") ||
                    impStr.contains("com.hazelcast.config.MaxSizeConfig;")) {
                    // Skip
                } else {
                    newImports.add(imp);
                }
            }
            cu.setImports(newImports);
        }
        
        for (CtMethod<?> method : model.getElements(new TypeFilter<>(CtMethod.class))) {
            if ("memberAttributeChanged".equals(method.getSimpleName())) {
                method.delete();
            }
        }
        
        for (CtInvocation<?> inv : model.getElements(new TypeFilter<>(CtInvocation.class))) {
            if (inv.getExecutable().getSimpleName().equals("setMaxSizeConfig")) {
                CtStatement stmt1 = launcher.getFactory().Code().createCodeSnippetStatement("com.hazelcast.config.EvictionConfig evictionConfig = new com.hazelcast.config.EvictionConfig()");
                CtStatement stmt2 = launcher.getFactory().Code().createCodeSnippetStatement("evictionConfig.setSize(hazelcastMaxCacheSizeInMegaBytes)");
                CtStatement stmt3 = launcher.getFactory().Code().createCodeSnippetStatement("evictionConfig.setMaxSizePolicy(com.hazelcast.config.MaxSizePolicy.USED_HEAP_SIZE)");
                CtStatement stmt4 = launcher.getFactory().Code().createCodeSnippetStatement("dynamicConfig.setEvictionConfig(evictionConfig)");
                
                inv.insertBefore(stmt1);
                inv.insertBefore(stmt2);
                inv.insertBefore(stmt3);
                inv.insertBefore(stmt4);
                inv.delete();
            }
        }
        
        launcher.setSourceOutputDirectory("/workspace/openfire-hazelcast-plugin/src/java");
        launcher.prettyprint();
    }
}
