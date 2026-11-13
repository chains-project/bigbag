package github.chains;

import spoon.Launcher;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.reference.CtExecutableReference;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.filter.TypeFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.Map;

public class Main {
    private static final Map<String, String> TYPE_MAP = new LinkedHashMap<>();

    static {
        TYPE_MAP.put("com.hazelcast.core.Cluster", "com.hazelcast.cluster.Cluster");
        TYPE_MAP.put("com.hazelcast.core.Member", "com.hazelcast.cluster.Member");
        TYPE_MAP.put("com.hazelcast.core.MembershipEvent", "com.hazelcast.cluster.MembershipEvent");
        TYPE_MAP.put("com.hazelcast.core.MembershipListener", "com.hazelcast.cluster.MembershipListener");
        TYPE_MAP.put("com.hazelcast.core.IMap", "com.hazelcast.map.IMap");
        TYPE_MAP.put("com.hazelcast.core.MapEvent", "com.hazelcast.map.MapEvent");
        TYPE_MAP.put("com.hazelcast.monitor.LocalMapStats", "com.hazelcast.map.LocalMapStats");
    }

    public static void main(String[] args) {
        if (args.length < 2) {
            throw new IllegalArgumentException("Usage: Main <inputDir> <outputDir>");
        }

        Path inputDir = Paths.get(args[0]);
        Path outputDir = Paths.get(args[1]);

        Launcher launcher = new Launcher();
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true);
        launcher.getEnvironment().setCommentEnabled(false);
        launcher.getEnvironment().setSourceOutputDirectory(outputDir.toFile());
        launcher.addInputResource(inputDir.toString());
        launcher.buildModel();

        replaceTypes(launcher);
        replaceGetStringAttribute(launcher);
        launcher.prettyprint();

        postProcess(outputDir);
    }

    private static void replaceTypes(Launcher launcher) {
        for (CtTypeReference<?> ref : launcher.getModel().getElements(new TypeFilter<>(CtTypeReference.class))) {
            String replacement = TYPE_MAP.get(ref.getQualifiedName());
            if (replacement != null) {
                ref.replace(launcher.getFactory().Type().createReference(replacement));
            }
        }
    }

    private static void replaceGetStringAttribute(Launcher launcher) {
        for (CtInvocation<?> invocation : launcher.getModel().getElements(new TypeFilter<>(CtInvocation.class))) {
            CtExecutableReference<?> executable = invocation.getExecutable();
            if (executable != null && "getStringAttribute".equals(executable.getSimpleName())) {
                executable.setSimpleName("getAttribute");
            }
        }
    }

    private static void postProcess(Path outputDir) {
        try {
            Files.walk(outputDir)
                .filter(path -> path.toString().endsWith(".java"))
                .forEach(path -> {
                    try {
                        String content = Files.readString(path, StandardCharsets.UTF_8);
                        content = content.replace("import com.hazelcast.config.MaxSizeConfig;\n", "")
                            .replace("import MaxSizeConfig.MaxSizePolicy;\n", "")
                            .replace("import com.hazelcast.cluster.MemberAttributeEvent;\n", "")
                            .replace("import com.hazelcast.core.MemberAttributeEvent;\n", "")
                            .replace("com.hazelcast.core.Cluster", "com.hazelcast.cluster.Cluster")
                            .replace("com.hazelcast.core.Member", "com.hazelcast.cluster.Member")
                            .replace("com.hazelcast.core.MembershipEvent", "com.hazelcast.cluster.MembershipEvent")
                            .replace("com.hazelcast.core.MembershipListener", "com.hazelcast.cluster.MembershipListener")
                            .replace("com.hazelcast.core.IMap", "com.hazelcast.map.IMap")
                            .replace("com.hazelcast.core.MapEvent", "com.hazelcast.map.MapEvent")
                            .replace("com.hazelcast.monitor.LocalMapStats", "com.hazelcast.map.LocalMapStats")
                            .replace("getStringAttribute(", "getAttribute(")
                            .replace("setMaxSizeConfig(new MaxSizeConfig(hazelcastMaxCacheSizeInMegaBytes, MaxSizeConfig.MaxSizePolicy.USED_HEAP_SIZE))",
                                "setEvictionConfig(new com.hazelcast.config.EvictionConfig().setMaxSizePolicy(com.hazelcast.config.MaxSizePolicy.USED_HEAP_SIZE).setSize(hazelcastMaxCacheSizeInMegaBytes))");

                        int idx = content.indexOf("public void memberAttributeChanged(final MemberAttributeEvent event)");
                        if (idx >= 0) {
                            int end = content.indexOf("boolean isClusterMember()", idx);
                            if (end > idx) {
                                content = content.substring(0, idx) + content.substring(end);
                            }
                        }

                        Files.writeString(path, content, StandardCharsets.UTF_8);
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                });
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
