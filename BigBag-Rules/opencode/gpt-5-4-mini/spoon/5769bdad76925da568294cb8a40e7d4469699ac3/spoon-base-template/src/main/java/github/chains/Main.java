package github.chains;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import spoon.Launcher;
import spoon.reflect.code.CtExpression;
import spoon.reflect.code.CtFieldRead;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.reference.CtExecutableReference;
import spoon.reflect.visitor.filter.TypeFilter;

public class Main {
    public static void main(String[] args) {
        if (args.length < 1 || args.length > 2) {
            throw new IllegalArgumentException("Usage: Main <source-dir> [output-dir]");
        }

        File sourceDir = new File(args[0]);
        File outputDir = args.length == 2 ? new File(args[1]) : new File(sourceDir, "spoon-out");

        Launcher launcher = new Launcher();
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true);
        for (File input : sourceRoots(sourceDir.toPath())) {
            launcher.addInputResource(input.getAbsolutePath());
        }
        launcher.setSourceOutputDirectory(outputDir);
        launcher.buildModel();

        CtExecutableReference<?> getStatus = launcher.getFactory().createExecutableReference();
        getStatus.setDeclaringType(launcher.getFactory().createReference("org.kohsuke.github.GHCompare"));
        getStatus.setSimpleName("getStatus");
        getStatus.setType(launcher.getFactory().createReference("org.kohsuke.github.GHCompare$Status"));
        getStatus.setParameters(Collections.emptyList());

        List<CtFieldRead<?>> reads = new ArrayList<>(launcher.getModel().getElements(new TypeFilter<>(CtFieldRead.class)));
        for (CtFieldRead<?> read : reads) {
            if (read.getVariable() == null || !"status".equals(read.getVariable().getSimpleName())) {
                continue;
            }
            CtExpression<?> target = read.getTarget();
            if (!(target instanceof CtInvocation)) {
                continue;
            }
            CtInvocation<?> receiverInvocation = (CtInvocation<?>) target;
            if (!"getCompare".equals(receiverInvocation.getExecutable().getSimpleName())) {
                continue;
            }

            CtInvocation<?> replacement = launcher.getFactory().Code().createInvocation(target.clone(), getStatus);
            read.replace(replacement);
        }

        launcher.prettyprint();
    }

    private static List<File> sourceRoots(Path root) {
        List<File> roots = new ArrayList<>();
        ArrayDeque<Path> stack = new ArrayDeque<>();
        stack.push(root);
        while (!stack.isEmpty()) {
            Path dir = stack.pop();
            if (Files.isDirectory(dir)) {
                if (dir.endsWith("src/main/java") || dir.endsWith("src/test/java")) {
                    roots.add(dir.toFile());
                    continue;
                }
                try {
                    Files.list(dir).forEach(stack::push);
                } catch (IOException ignored) {
                    // Skip unreadable directories.
                }
            }
        }
        if (roots.isEmpty()) {
            roots.add(root.toFile());
        }
        return roots;
    }
}
