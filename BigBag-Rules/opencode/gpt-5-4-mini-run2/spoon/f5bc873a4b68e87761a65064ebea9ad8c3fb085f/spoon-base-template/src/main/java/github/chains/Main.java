package github.chains;

import spoon.Launcher;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.declaration.CtElement;
import spoon.reflect.reference.CtExecutableReference;
import spoon.reflect.visitor.filter.TypeFilter;

import java.util.LinkedHashMap;
import java.util.Map;

public class Main {

    private static final class MethodRewrite {
        private final String declaringType;
        private final String oldName;
        private final String newName;
        private final int arity;

        private MethodRewrite(String declaringType, String oldName, String newName, int arity) {
            this.declaringType = declaringType;
            this.oldName = oldName;
            this.newName = newName;
            this.arity = arity;
        }
    }

    private static final Map<String, MethodRewrite> REWRITES = new LinkedHashMap<>();

    static {
        // Generic structural rewrites for Maven Archiver 3.6.0 API changes.
        register(new MethodRewrite(
                "org.apache.maven.archiver.MavenArchiver",
                "configureReproducible",
                "configureReproducibleBuild",
                1));
        register(new MethodRewrite(
                "org.apache.maven.archiver.MavenArchiver",
                "parseOutputTimestamp",
                "parseBuildOutputTimestamp",
                1));
    }

    private static void register(MethodRewrite rewrite) {
        REWRITES.put(rewrite.declaringType + "#" + rewrite.oldName + "/" + rewrite.arity, rewrite);
    }

    public static void main(String[] args) {
        if (args.length < 2) {
            throw new IllegalArgumentException("Expected <inputDir> <outputDir>");
        }

        Launcher launcher = new Launcher();
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true);
        launcher.getEnvironment().setCommentEnabled(true);
        launcher.addInputResource(args[0]);
        launcher.setSourceOutputDirectory(args[1]);
        launcher.buildModel();

        for (CtInvocation<?> invocation : launcher.getModel().getElements(new TypeFilter<>(CtInvocation.class))) {
            rewriteInvocation(invocation);
        }

        launcher.prettyprint();
    }

    private static void rewriteInvocation(CtInvocation<?> invocation) {
        CtExecutableReference<?> executable = invocation.getExecutable();
        if (executable == null || executable.getDeclaringType() == null) {
            return;
        }

        MethodRewrite rewrite = REWRITES.get(key(executable.getDeclaringType().getQualifiedName(), executable.getSimpleName(), invocation.getArguments().size()));
        if (rewrite == null) {
            return;
        }

        executable.setSimpleName(rewrite.newName);
    }

    private static String key(String declaringType, String methodName, int arity) {
        return declaringType + "#" + methodName + "/" + arity;
    }
}
