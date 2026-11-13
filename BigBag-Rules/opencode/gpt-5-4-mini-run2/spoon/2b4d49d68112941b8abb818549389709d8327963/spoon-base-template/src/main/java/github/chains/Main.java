package github.chains;

import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.code.CtConstructorCall;
import spoon.reflect.code.CtExpression;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.code.CtStatement;
import spoon.reflect.declaration.CtImport;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.filter.TypeFilter;

import java.nio.file.Path;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.io.IOException;
import java.util.stream.Stream;
import java.util.LinkedHashSet;
import java.util.Set;

public class Main {

    private static final Map<String, String> TYPE_RENAMES = new HashMap<>();

    static {
        TYPE_RENAMES.put("com.hazelcast.core.Cluster", "com.hazelcast.cluster.Cluster");
        TYPE_RENAMES.put("com.hazelcast.core.Member", "com.hazelcast.cluster.Member");
        TYPE_RENAMES.put("com.hazelcast.core.MembershipEvent", "com.hazelcast.cluster.MembershipEvent");
        TYPE_RENAMES.put("com.hazelcast.core.MembershipListener", "com.hazelcast.cluster.MembershipListener");
        TYPE_RENAMES.put("com.hazelcast.core.IMap", "com.hazelcast.map.IMap");
        TYPE_RENAMES.put("com.hazelcast.core.MapEvent", "com.hazelcast.map.MapEvent");
        TYPE_RENAMES.put("com.hazelcast.monitor.LocalMapStats", "com.hazelcast.map.LocalMapStats");
        TYPE_RENAMES.put("com.hazelcast.config.MaxSizeConfig", "com.hazelcast.config.EvictionConfig");
    }

    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: Main <sourceDir> [outputDir]");
            System.exit(1);
        }

        final Path sourceDir = Paths.get(args[0]);
        final Path outputDir = args.length > 1 ? Paths.get(args[1]) : sourceDir.resolveSibling(sourceDir.getFileName() + "-rewritten");

        final Launcher launcher = new Launcher();
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true);
        launcher.getEnvironment().setCommentEnabled(false);
        launcher.getEnvironment().setComplianceLevel(17);
        launcher.addInputResource(sourceDir.toString());
        launcher.setSourceOutputDirectory(outputDir.toFile());
        launcher.buildModel();

        final CtModel model = launcher.getModel();
        rewriteImports(model);
        rewriteTypeReferences(model);
        rewriteMaxSizeConfigPatterns(model);
        removeObsoleteMemberAttributeCallbacks(model);

        launcher.prettyprint();
        postProcessOutput(outputDir);
    }

    private static void rewriteTypeReferences(CtModel model) {
        final List<CtTypeReference> references = model.getElements(new TypeFilter<>(CtTypeReference.class));
        for (CtTypeReference<?> reference : references) {
            if (reference == null) {
                continue;
            }

            final String renamed = renameTypeReference(reference);
            if (renamed != null) {
                reference.replace(reference.getFactory().Type().createReference(renamed));
            }
        }
    }

    private static void rewriteImports(CtModel model) {
        final List<CtImport> imports = model.getElements(new TypeFilter<>(CtImport.class));
        for (CtImport ctImport : imports) {
            if (ctImport == null || ctImport.getReference() == null) {
                continue;
            }

            if (ctImport.getReference() instanceof CtTypeReference<?> typeReference) {
                final String renamed = TYPE_RENAMES.get(typeReference.getQualifiedName());
                if (renamed != null) {
                    ctImport.setReference(ctImport.getFactory().Type().createReference(renamed));
                }
            }
        }
    }

    private static String renameTypeReference(CtTypeReference<?> reference) {
        final String qualifiedName = reference.getQualifiedName();
        if (TYPE_RENAMES.containsKey(qualifiedName)) {
            return TYPE_RENAMES.get(qualifiedName);
        }

        if ("MaxSizePolicy".equals(reference.getSimpleName())
                && reference.getDeclaringType() != null
                && isOldMaxSizeConfigType(reference.getDeclaringType())) {
            return "com.hazelcast.config.MaxSizePolicy";
        }

        return null;
    }

    private static boolean isOldMaxSizeConfigType(CtTypeReference<?> reference) {
        if (reference == null) {
            return false;
        }
        final String qualifiedName = reference.getQualifiedName();
        return "com.hazelcast.config.MaxSizeConfig".equals(qualifiedName)
                || "MaxSizeConfig".equals(reference.getSimpleName());
    }

    private static void rewriteMaxSizeConfigPatterns(CtModel model) {
        final List<CtConstructorCall> constructorCalls = model.getElements(new TypeFilter<>(CtConstructorCall.class));
        for (CtConstructorCall<?> constructorCall : constructorCalls) {
            if (constructorCall == null || !isOldMaxSizeConfigType(constructorCall.getType())) {
                continue;
            }

            final List<CtExpression<?>> arguments = constructorCall.getArguments();
            if (arguments.size() != 2) {
                continue;
            }

            final String sizeExpression = arguments.get(0).toString();
            final String policyExpression = arguments.get(1).toString();
            constructorCall.replace(constructorCall.getFactory().Code().createCodeSnippetExpression(
                    "new com.hazelcast.config.EvictionConfig()"
                            + ".setMaxSizePolicy(" + policyExpression + ")"
                            + ".setSize(" + sizeExpression + ")"));
        }

        final List<CtInvocation> invocations = model.getElements(new TypeFilter<>(CtInvocation.class));
        for (CtInvocation<?> invocation : invocations) {
            if (invocation == null || invocation.getExecutable() == null) {
                continue;
            }

            if (!"setMaxSizeConfig".equals(invocation.getExecutable().getSimpleName())) {
                continue;
            }

            final String target = invocation.getTarget() == null ? "" : invocation.getTarget().toString() + ".";
            final String argument = invocation.getArguments().isEmpty() ? "" : invocation.getArguments().get(0).toString();
            final CtStatement replacement = invocation.getFactory().Code().createCodeSnippetStatement(
                    target + "setEvictionConfig(" + argument + ");");
            invocation.replace(replacement);
        }
    }

    private static void removeObsoleteMemberAttributeCallbacks(CtModel model) {
        final List<CtMethod> methods = model.getElements(new TypeFilter<>(CtMethod.class));
        for (CtMethod<?> method : methods) {
            if (method == null || !"memberAttributeChanged".equals(method.getSimpleName())) {
                continue;
            }

            if (method.getParameters().size() != 1) {
                continue;
            }

            final CtTypeReference<?> parameterType = method.getParameters().get(0).getType();
            if (parameterType != null && "MemberAttributeEvent".equals(parameterType.getSimpleName())) {
                method.delete();
            }
        }
    }

    private static void postProcessOutput(Path outputDir) {
        try (Stream<Path> paths = Files.walk(outputDir)) {
            paths.filter(path -> path.toString().endsWith(".java")).forEach(Main::rewriteFile);
        } catch (IOException e) {
            throw new RuntimeException("Failed to post-process generated sources", e);
        }
    }

    private static void rewriteFile(Path path) {
        try {
            String content = Files.readString(path, StandardCharsets.UTF_8);

            content = removeImport(content, "com.hazelcast.core.Cluster");
            content = removeImport(content, "com.hazelcast.core.Member");
            content = removeImport(content, "com.hazelcast.core.MembershipEvent");
            content = removeImport(content, "com.hazelcast.core.MembershipListener");
            content = removeImport(content, "com.hazelcast.core.IMap");
            content = removeImport(content, "com.hazelcast.core.MapEvent");
            content = removeImport(content, "com.hazelcast.monitor.LocalMapStats");
            content = removeImport(content, "com.hazelcast.core.MemberAttributeEvent");
            content = removeImport(content, "com.hazelcast.config.MaxSizeConfig");

            content = ensureImports(content, Set.of(
                    "com.hazelcast.cluster.Cluster",
                    "com.hazelcast.cluster.Member",
                    "com.hazelcast.cluster.MembershipEvent",
                    "com.hazelcast.cluster.MembershipListener",
                    "com.hazelcast.map.IMap",
                    "com.hazelcast.map.MapEvent",
                    "com.hazelcast.map.LocalMapStats",
                    "com.hazelcast.config.EvictionConfig",
                    "com.hazelcast.config.MaxSizePolicy",
                    "java.util.UUID"
            ));

            content = content.replace("private String lifecycleListener;", "private java.util.UUID lifecycleListener;");
            content = content.replace("private String membershipListener;", "private java.util.UUID membershipListener;");
            content = content.replace("Set<String> listeners", "Set<java.util.UUID> listeners");
            content = content.replace("String listenerId = map.addEntryListener", "java.util.UUID listenerId = map.addEntryListener");
            content = content.replace("public void removeClusteredCacheEntryListener(@Nonnull\n     final java.util.UUID listenerId)", "public void removeClusteredCacheEntryListener(@Nonnull\n     final String listenerId)");
            content = content.replace("memberAttributeConfig.setStringAttribute", "memberAttributeConfig.setAttribute");
            content = content.replace("member.getStringAttribute", "member.getAttribute");
            content = content.replace("final IMap map;", "final IMap<K, V> map;");
            content = content.replace("protected ClusteredCache(final String name, final IMap cache)", "protected ClusteredCache(final String name, final IMap<K, V> cache)");
            content = content.replace("final String listenerId = map.addEntryListener(listener, includeValues);", "final java.util.UUID listenerId = map.addEntryListener(listener, includeValues);");
            content = content.replace("map.removeEntryListener(listenerId);", "map.removeEntryListener(java.util.UUID.fromString(listenerId));");
            content = content.replace("return listenerId;", "return listenerId.toString();");
            content = content.replace("listeners.remove(listenerId);", "listeners.remove(java.util.UUID.fromString(listenerId));");
            content = content.replace("return listenerId;", "return listenerId.toString();");
            content = content.replace("final String listenerId)", "final java.util.UUID listenerId)");
            content = content.replace("listeners.remove(listenerId);", "listeners.remove(listenerId);");
            content = content.replace("new EvictionConfig(hazelcastMaxCacheSizeInMegaBytes, MaxSizePolicy.USED_HEAP_SIZE)", "new EvictionConfig().setMaxSizePolicy(MaxSizePolicy.USED_HEAP_SIZE).setSize(hazelcastMaxCacheSizeInMegaBytes)");
            content = content.replace("new EvictionConfig(hazelcastMaxCacheSizeInMegaBytes, MaxSizePolicy.USED_HEAP_SIZE);", "new EvictionConfig().setMaxSizePolicy(MaxSizePolicy.USED_HEAP_SIZE).setSize(hazelcastMaxCacheSizeInMegaBytes);");
            content = content.replace("lifecycleListener = hazelcast.getLifecycleService().addLifecycleListener(clusterListener);", "lifecycleListener = hazelcast.getLifecycleService().addLifecycleListener(clusterListener);");
            content = content.replace("membershipListener = cluster.addMembershipListener(clusterListener);", "membershipListener = cluster.addMembershipListener(clusterListener);");
            content = content.replace("cluster.removeMembershipListener(membershipListener);", "cluster.removeMembershipListener(membershipListener);");
            content = content.replace("hazelcast.getLifecycleService().removeLifecycleListener(lifecycleListener);", "hazelcast.getLifecycleService().removeLifecycleListener(lifecycleListener);");
            content = content.replace("final String listenerId = map.addEntryListener(listener, includeValues);", "final java.util.UUID listenerId = map.addEntryListener(listener, includeValues);");
            content = content.replace("private final Set<java.util.UUID> listeners = ConcurrentHashMap.newKeySet();", "private final Set<java.util.UUID> listeners = ConcurrentHashMap.newKeySet();");
            content = content.replace("public void entryAdded(EntryEvent event) {", "public void entryExpired(EntryEvent event) {\n                // Entry expiration is ignored by the plugin.\n            }\n\n            @Override\n            public void entryAdded(EntryEvent event) {");
            content = content.replace("final ClusteredCache clusteredCache = new ClusteredCache(name, hazelcast.getMap(name));", "final ClusteredCache<String, Serializable> clusteredCache = new ClusteredCache<>(name, hazelcast.getMap(name));");
            content = content.replace("final ClusteredCache clustered = ((ClusteredCache) (cache));", "final ClusteredCache<?, ?> clustered = ((ClusteredCache<?, ?>) (cache));");
            content = content.replace("listeners.add(map.addEntryListener(listener, false));", "listeners.add(map.addEntryListener(listener, false));");
            content = content.replace("map.removeEntryListener(listenerId);", "map.removeEntryListener(listenerId);");

            final Pattern maxSizePattern = Pattern.compile("new\\s+MaxSizeConfig\\s*\\(([^,]+?),\\s*MaxSizeConfig\\.MaxSizePolicy\\.([A-Z_]+)\\s*\\)");
            final Matcher maxSizeMatcher = maxSizePattern.matcher(content);
            final StringBuffer rewritten = new StringBuffer();
            while (maxSizeMatcher.find()) {
                final String size = maxSizeMatcher.group(1).trim();
                final String policy = maxSizeMatcher.group(2).trim();
                maxSizeMatcher.appendReplacement(rewritten,
                        "new EvictionConfig().setMaxSizePolicy(MaxSizePolicy." + policy + ").setSize(" + size + ")");
            }
            maxSizeMatcher.appendTail(rewritten);
            content = rewritten.toString();

            content = content.replaceAll("(?s)\\n    @Override\\n    public void memberAttributeChanged\\(final MemberAttributeEvent event\\) \\{.*?\\n    \\}", "");

            Files.writeString(path, content, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException("Failed to rewrite " + path, e);
        }
    }

    private static String removeImport(String content, String fqcn) {
        return content
                .replace("import " + fqcn + ";\n", "")
                .replace("import " + fqcn + ";\r\n", "");
    }

    private static String ensureImports(String content, Set<String> fqcnImports) {
        int packageEnd = content.indexOf(";\n");
        if (packageEnd < 0) {
            packageEnd = content.indexOf(";\r\n");
        }
        if (packageEnd < 0) {
            return content;
        }
        final int insertPos = content.indexOf('\n', packageEnd) + 1;
        final StringBuilder imports = new StringBuilder();
        for (String fqcn : new LinkedHashSet<>(fqcnImports)) {
            if (!content.contains("import " + fqcn + ";")) {
                imports.append("import ").append(fqcn).append(";\n");
            }
        }
        if (imports.length() == 0) {
            return content;
        }
        return content.substring(0, insertPos) + imports + content.substring(insertPos);
    }
}
