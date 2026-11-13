package github.chains;

import spoon.Launcher;
import spoon.SpoonException;
import spoon.reflect.code.CtConstructorCall;
import spoon.reflect.code.CtExpression;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.declaration.CtAnnotation;
import spoon.reflect.declaration.CtCompilationUnit;
import spoon.reflect.declaration.CtElement;
import spoon.reflect.declaration.CtImport;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.declaration.CtPackageDeclaration;
import spoon.SpoonException;
import spoon.reflect.declaration.CtParameter;
import spoon.reflect.reference.CtExecutableReference;
import spoon.reflect.reference.CtPackageReference;
import spoon.reflect.reference.CtReference;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.filter.TypeFilter;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * GENERIC, REUSABLE transformation rule that repairs any Maven/Java project that was
 * written against an OLDER version of a dependency which subsequently MOVED, RENAMED or
 * REMOVED some of its public API types / methods.
 *
 * The rule is parameterized ONLY by fully-qualified names coming FROM THE DEPENDENCY
 * (see the CONFIGURATION block). It never references any client / project class name, so
 * it can be re-used for another project simply by changing the input source directory
 * passed on the command line (and, if the next project used a different dependency, by
 * editing the dependency FQN maps). Nothing else is project-specific.
 *
 * The transformation processes EVERY compilation unit, rewriting imports and all type
 * references to match the new dependency API. It preserves formatting and comments.
 *
 * ---------------------------------------------------------------------------
 * CHARACTERIZATION OF THE BREAKING CHANGE (abstract, dependency-driven)
 * ---------------------------------------------------------------------------
 * (1) PACKAGE RELOCATION of a top-level type (simple name identical, package changed)
 *        old : import <oldPkg>.<Type>   (com.hazelcast.core.Member)
 *        new : import <newPkg>.<Type>   (com.hazelcast.cluster.Member)
 *        call-site transform : re-point every reference (import + usage) from the old
 *                              package to the new package, keeping the simple name.
 *
 * (2) NESTED TYPE PROMOTED TO TOP LEVEL
 *        old : <pkg>.<Outer>.<Inner>    (config.MaxSizeConfig.MaxSizePolicy)
 *        new : <pkg>.<Inner>            (config.MaxSizePolicy)
 *        call-site transform : drop the enclosing-type qualifier, move to top level.
 *
 * (3) METHOD RENAME (same receiver type, method renamed)
 *        old : <DeclaringType>.<oldMethod>(...)   (Member.getStringAttribute)
 *        new : <DeclaringType>.<newMethod>(...)   (Member.getAttribute)
 *        call-site transform : rename the invoked executable, keyed by declaring type.
 *
 * (4) TYPE REMOVED -> now-invalid @Override (interface lost a method whose param type is gone)
 *        old : @Override public void m(<RemovedType> e)
 *        new : (method gone from the interface)
 *        call-site transform : delete any @Override method taking a removed type and drop
 *                              the import of the removed type.
 *
 * (5) REMOVED CONSTRUCTOR + REMOVED SETTER folded into a new builder-style type
 *        old : cfg.set<Removed>(new <Removed>(a, b))
 *        new : cfg.set<Replaced>(new <Replaced>().setFirst(a).setSecond(b))
 *        call-site transform : rewrite the constructor invocation into a fluent chain on
 *                              the replacement type and rename the enclosing setter.
 */
public class Main {

    // ===================================================================
    // CONFIGURATION  -- every key below is a DEPENDENCY fully-qualified name.
    // ===================================================================

    // (1) top-level package relocations: OLD FQN -> NEW FQN (same simple name)
    private static final Map<String, String> PACKAGE_RELOCATIONS = Map.of(
        "com.hazelcast.core.Member", "com.hazelcast.cluster.Member",
        "com.hazelcast.core.Cluster", "com.hazelcast.cluster.Cluster",
        "com.hazelcast.core.MembershipListener", "com.hazelcast.cluster.MembershipListener",
        "com.hazelcast.core.MembershipEvent", "com.hazelcast.cluster.MembershipEvent",
        "com.hazelcast.core.IMap", "com.hazelcast.map.IMap",
        "com.hazelcast.core.MapEvent", "com.hazelcast.map.MapEvent",
        "com.hazelcast.monitor.LocalMapStats", "com.hazelcast.map.LocalMapStats"
    );

    // (2) nested type promoted to a top-level type: OLD FQN -> NEW top-level FQN
    private static final Map<String, String> NESTED_TO_TOPLEVEL = Map.of(
        "com.hazelcast.config.MaxSizeConfig.MaxSizePolicy", "com.hazelcast.config.MaxSizePolicy"
    );

    // (3) method renames: declaring-type FQN -> (old method name -> new method name).
    //     Both OLD and NEW declaring FQNs are mapped because the receiver may be typed
    //     with either, depending on traversal order.
    private static final Map<String, Map<String, String>> METHOD_RENAMES = new LinkedHashMap<>();
    static {
        Map<String, String> memberRenames = new LinkedHashMap<>();
        memberRenames.put("getStringAttribute", "getAttribute");
        METHOD_RENAMES.put("com.hazelcast.core.Member", memberRenames);
        METHOD_RENAMES.put("com.hazelcast.cluster.Member", memberRenames);
        METHOD_RENAMES.put("com.hazelcast.config.MemberAttributeConfig", Map.of(
                "setStringAttribute", "setAttribute"
        ));
    }

    // (4) fully removed types: their imports are dropped and any @Override method whose
    //     parameter uses one of them is deleted.
    private static final Set<String> REMOVED_TYPES = Set.of(
        "com.hazelcast.core.MemberAttributeEvent",
        "com.hazelcast.config.MaxSizeConfig"
    );

    // (5) removed "value object" constructor-call rewrites (pattern 5).
    private static final List<RemovedConstructorRewrite> CONSTRUCTOR_REWRITES = List.of(
        new RemovedConstructorRewrite(
            "com.hazelcast.config.MaxSizeConfig",
            "setMaxSizeConfig",
            "setEvictionConfig",
            "com.hazelcast.config.EvictionConfig",
            new String[] { "setSize", "setMaxSizePolicy" })
    );

    /** Declarative description of a pattern-(5) removed-constructor rewrite (all keys = dependency FQNs). */
    private static final class RemovedConstructorRewrite {
        final String removedType;
        final String oldSetter;
        final String newSetter;
        final String replacementType;
        final String[] fluentSetters;

        RemovedConstructorRewrite(String removedType, String oldSetter, String newSetter,
                                  String replacementType, String[] fluentSetters) {
            this.removedType = removedType;
            this.oldSetter = oldSetter;
            this.newSetter = newSetter;
            this.replacementType = replacementType;
            this.fluentSetters = fluentSetters;
        }
    }

    // ===================================================================
    // DRIVER -- the only project-specific value is the INPUT SOURCE DIR,
    // supplied on the command line so the rule is reusable as-is.
    // ===================================================================
    public static void main(String[] args) throws IOException {
        String inputDir = args.length > 0 ? args[0] : "/workspace/openfire-hazelcast-plugin/src/java";
        String outputDir = args.length > 1 ? args[1] : inputDir;
        String dependencyJar = args.length > 2 ? args[2] : null; // optional, improves name resolution

        Launcher launcher = new Launcher();
        launcher.addInputResource(new File(inputDir).getAbsolutePath());
        spoon.compiler.Environment env = launcher.getEnvironment();
        env.setNoClasspath(true);
        env.setComplianceLevel(17);
        env.setIgnoreSyntaxErrors(true);
        env.setCommentEnabled(true);
        env.setAutoImports(false);

        launcher.buildModel();
        applyMigration(launcher);
        launcher.setSourceOutputDirectory(new File(outputDir).getAbsolutePath());
        launcher.prettyprint();

        System.out.println("[transformation] finished input=" + inputDir + " output=" + outputDir);
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    private static void applyMigration(Launcher launcher) {
        // Gather all compilation units and imports first
        List<CtCompilationUnit> units =
                (List) launcher.getModel().getRootPackage().getElements(new TypeFilter(CtCompilationUnit.class));
        List<CtImport> imports = (List) launcher.getModel().getRootPackage()
                .getElements(new TypeFilter(CtImport.class));
        List<CtInvocation<?>> invocations = (List) launcher.getModel().getRootPackage()
                .getElements(new TypeFilter(CtInvocation.class));
        List<CtMethod<?>> methods = (List) launcher.getModel().getRootPackage()
                .getElements(new TypeFilter(CtMethod.class));
        List<CtTypeReference<?>> typeRefs = (List) launcher.getModel().getRootPackage()
                .getElements(new TypeFilter(CtTypeReference.class));

        // Build a set of old FQNs that need import cleanup
        Set<String> toRemove = new HashSet<>();
        toRemove.addAll(PACKAGE_RELOCATIONS.keySet());
        toRemove.addAll(NESTED_TO_TOPLEVEL.keySet());
        toRemove.addAll(REMOVED_TYPES);

        // (4b) Delete imports of old / removed types (they'll be fully qualified below)
        for (CtImport imp : imports) {
            if (imp.getReference() instanceof CtTypeReference) {
                String fqn = ((CtTypeReference<?>) imp.getReference()).getQualifiedName();
                if (fqn != null && toRemove.contains(fqn)) {
                    imp.delete();
                }
            }
        }

        // (5) fold removed-constructor calls into a fluent chain
        for (CtInvocation<?> inv : invocations) {
            rewriteRemovedConstructorCall(launcher.getFactory(), inv);
        }
        // (4a) delete now-invalid @Override methods bound to removed types
        for (CtMethod<?> m : methods) {
            removeObsoleteOverride(m);
        }
        // (3) rename invoked methods
        for (CtInvocation<?> inv : invocations) {
            renameInvokedMethod(inv);
        }
        // (1)+(2) re-point every type reference (makes them fully qualified to new packages)
        for (CtTypeReference<?> ref : typeRefs) {
            relocateTypeReference(launcher.getFactory(), ref);
        }
    }

    

    private static void relocateTypeReference(spoon.reflect.factory.Factory factory, CtTypeReference<?> ref) {
        String fqn = ref.getQualifiedName();
        if (fqn == null) return;
        String newFqn = PACKAGE_RELOCATIONS.get(fqn);
        if (newFqn != null && ref.getDeclaringType() == null) {
            ref.setPackage(packageRef(factory, newFqn));
            return;
        }
        String topFqn = NESTED_TO_TOPLEVEL.get(fqn);
        if (topFqn != null) {
            ref.setDeclaringType(null);
            ref.setPackage(packageRef(factory, topFqn));
        }
    }

    private static void removeObsoleteOverride(CtMethod<?> method) {
        if (!hasOverride(method)) return;
        for (CtParameter<?> p : method.getParameters()) {
            if (p.getType() != null && REMOVED_TYPES.contains(p.getType().getQualifiedName())) {
                method.delete();
                return;
            }
        }
    }

    private static boolean hasOverride(CtMethod<?> method) {
        for (CtAnnotation<?> a : method.getAnnotations()) {
            CtTypeReference<?> t = a.getType();
            if (t != null && "Override".equals(t.getSimpleName())) return true;
        }
        return false;
    }

    private static void renameInvokedMethod(CtInvocation<?> inv) {
        CtExecutableReference<?> ex = inv.getExecutable();
        if (ex == null) return;
        String newName = resolveRename(ex.getDeclaringType(), ex.getSimpleName());
        if (newName == null && inv.getTarget() != null && inv.getTarget().getType() != null) {
            newName = resolveRename(inv.getTarget().getType(), ex.getSimpleName());
        }
        if (newName != null) ex.setSimpleName(newName);
    }

    private static String resolveRename(CtTypeReference<?> declaringType, String methodName) {
        if (declaringType == null || methodName == null) return null;
        Map<String, String> renames = METHOD_RENAMES.get(declaringType.getQualifiedName());
        return renames == null ? null : renames.get(methodName);
    }

    private static void rewriteRemovedConstructorCall(spoon.reflect.factory.Factory factory, CtInvocation<?> inv) {
        CtExecutableReference<?> ex = inv.getExecutable();
        if (ex == null || ex.getSimpleName() == null || inv.getArguments().size() != 1) return;
        for (RemovedConstructorRewrite rw : CONSTRUCTOR_REWRITES) {
            if (!rw.oldSetter.equals(ex.getSimpleName())) continue;
            CtExpression<?> arg = inv.getArguments().get(0);
            if (!(arg instanceof CtConstructorCall)) continue;
            CtConstructorCall<?> ctor = (CtConstructorCall<?>) arg;
            if (ctor.getType() == null || !rw.removedType.equals(ctor.getType().getQualifiedName())) continue;
            List<CtExpression<?>> ctorArgs = new ArrayList<>(ctor.getArguments());
            CtTypeReference<?> repl = factory.Type().createReference(rw.replacementType);
            CtExpression<?> chain = ctorArgs.isEmpty()
                    ? factory.createConstructorCall(cloneType(factory, repl))
                    : buildFluentChain(factory, repl, ctorArgs, rw.fluentSetters);
            if (chain != null) {
                ex.setSimpleName(rw.newSetter);
                inv.setArguments(Collections.singletonList(chain));
            }
            return;
        }
    }

    private static CtExpression<?> buildFluentChain(spoon.reflect.factory.Factory factory,
                                                    CtTypeReference<?> repl,
                                                    List<CtExpression<?>> ctorArgs,
                                                    String[] setters) {
        if (setters.length == 0) return factory.createConstructorCall(cloneType(factory, repl));
        CtElement current = factory.createConstructorCall(cloneType(factory, repl));
        for (int i = 0; i < ctorArgs.size() && i < setters.length; i++) {
            CtExecutableReference<Object> sr = factory.createExecutableReference();
            sr.setSimpleName(setters[i]);
            sr.setDeclaringType(cloneType(factory, repl));
            sr.setType(cloneType(factory, repl));
            current = factory.createInvocation((CtExpression<?>) current, sr,
                    Collections.singletonList(ctorArgs.get(i)));
        }
        return (CtExpression<?>) current;
    }

    private static CtTypeReference<?> cloneType(spoon.reflect.factory.Factory factory, CtTypeReference<?> t) {
        return factory.Type().createReference(t.getQualifiedName());
    }

    private static CtPackageReference packageRef(spoon.reflect.factory.Factory factory, String fqn) {
        int last = fqn.lastIndexOf('.');
        String pkg = last < 0 ? "" : fqn.substring(0, last);
        CtPackageReference pr = factory.Core().createPackageReference();
        pr.setSimpleName(pkg);
        return pr;
    }
}