package github.chains;

import spoon.Launcher;
import spoon.reflect.code.CtConstructorCall;
import spoon.reflect.code.CtExpression;
import spoon.reflect.declaration.CtAnnotation;
import spoon.reflect.declaration.CtClass;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.declaration.CtParameter;
import spoon.reflect.factory.Factory;
import spoon.reflect.declaration.CtImport;
import spoon.reflect.reference.CtExecutableReference;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.filter.TypeFilter;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class Main {

    private static final Map<String, String> TYPE_RENAMES = new LinkedHashMap<>();

    static {
        TYPE_RENAMES.put("com.hazelcast.core.Cluster", "com.hazelcast.cluster.Cluster");
        TYPE_RENAMES.put("com.hazelcast.core.Member", "com.hazelcast.cluster.Member");
        TYPE_RENAMES.put("com.hazelcast.core.MembershipEvent", "com.hazelcast.cluster.MembershipEvent");
        TYPE_RENAMES.put("com.hazelcast.core.MembershipListener", "com.hazelcast.cluster.MembershipListener");
        TYPE_RENAMES.put("com.hazelcast.core.MemberAttributeEvent", "com.hazelcast.cluster.MembershipEvent");
        TYPE_RENAMES.put("com.hazelcast.core.MapEvent", "com.hazelcast.map.MapEvent");
        TYPE_RENAMES.put("com.hazelcast.core.IMap", "com.hazelcast.map.IMap");
        TYPE_RENAMES.put("com.hazelcast.monitor.LocalMapStats", "com.hazelcast.map.LocalMapStats");
        TYPE_RENAMES.put("com.hazelcast.config.MaxSizeConfig", "com.hazelcast.config.EvictionConfig");
    }

    public static void main(String[] args) {
        if (args.length < 2) {
            throw new IllegalArgumentException("Usage: Main <input-source-dir> <output-dir>");
        }

        Launcher launcher = new Launcher();
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true);
        launcher.getEnvironment().setCommentEnabled(true);
        launcher.addInputResource(args[0]);
        launcher.buildModel();

        Factory factory = launcher.getFactory();
        applyTypeRenames(factory);
        cleanupOldImports(factory);
        applyMaxSizeConfigMigration(factory);
        applyRemovedMemberAttributeCallbackMigration(factory);

        launcher.setSourceOutputDirectory(Path.of(args[1]).toFile());
        launcher.prettyprint();
    }

    private static void applyTypeRenames(Factory factory) {
        for (Map.Entry<String, String> entry : TYPE_RENAMES.entrySet()) {
            String oldName = entry.getKey();
            String newName = entry.getValue();
            CtTypeReference<?> newRef = factory.Type().createReference(newName);

            for (CtTypeReference<?> ref : factory.getModel().getElements(new TypeFilter<>(CtTypeReference.class))) {
                if (oldName.equals(ref.getQualifiedName())) {
                    ref.replace(newRef.clone());
                }
            }

            for (CtExecutableReference<?> ref : factory.getModel().getElements(new TypeFilter<>(CtExecutableReference.class))) {
                if (ref.getDeclaringType() != null && oldName.equals(ref.getDeclaringType().getQualifiedName())) {
                    ref.getDeclaringType().replace(newRef.clone());
                }
            }
        }
    }

    private static void cleanupOldImports(Factory factory) {
        for (CtImport imp : factory.getModel().getElements(new TypeFilter<>(CtImport.class))) {
            String qualifiedName = imp.getReference() == null ? null : imp.getReference().getSimpleName();
            if (qualifiedName == null) {
                continue;
            }
            for (String oldName : TYPE_RENAMES.keySet()) {
                if (qualifiedName.equals(oldName) || qualifiedName.startsWith(oldName + ".")) {
                    imp.delete();
                    break;
                }
            }
        }
    }

    private static void applyMaxSizeConfigMigration(Factory factory) {
        CtTypeReference<?> evictionConfigType = factory.Type().createReference("com.hazelcast.config.EvictionConfig");
        CtTypeReference<?> maxSizePolicyType = factory.Type().createReference("com.hazelcast.config.MaxSizePolicy");

        for (CtConstructorCall<?> call : factory.getModel().getElements(new TypeFilter<>(CtConstructorCall.class))) {
            CtTypeReference<?> type = call.getType();
            if (type == null || !"com.hazelcast.config.MaxSizeConfig".equals(type.getQualifiedName())) {
                continue;
            }
            List<CtExpression<?>> args = call.getArguments();
            if (args.size() != 2) {
                continue;
            }
            String replacement = "new com.hazelcast.config.EvictionConfig()"
                    + ".setSize(" + args.get(0).toString() + ")"
                    + ".setMaxSizePolicy(" + args.get(1).toString() + ")";
            call.replace(factory.Code().createCodeSnippetExpression(replacement));
        }
    }

    private static void applyRemovedMemberAttributeCallbackMigration(Factory factory) {
        for (CtClass<?> type : factory.getModel().getElements(new TypeFilter<>(CtClass.class))) {
            for (CtMethod<?> method : type.getMethods()) {
                if (!"memberAttributeChanged".equals(method.getSimpleName()) || method.getParameters().size() != 1) {
                    continue;
                }
                CtParameter<?> parameter = method.getParameters().get(0);
                if (parameter.getType() != null
                        && "com.hazelcast.core.MemberAttributeEvent".equals(parameter.getType().getQualifiedName())) {
                    parameter.setType(factory.Type().createReference("com.hazelcast.cluster.MembershipEvent"));
                    for (CtAnnotation<?> annotation : method.getAnnotations().toArray(new CtAnnotation[0])) {
                        if (annotation.getAnnotationType() != null && "Override".equals(annotation.getAnnotationType().getSimpleName())) {
                            annotation.delete();
                        }
                    }
                }
            }
        }
    }
}
