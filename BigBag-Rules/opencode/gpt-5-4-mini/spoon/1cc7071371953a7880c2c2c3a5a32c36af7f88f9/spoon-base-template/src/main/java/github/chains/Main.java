package github.chains;

import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.declaration.CtElement;
import spoon.reflect.declaration.CtImport;
import spoon.reflect.reference.CtReference;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.filter.TypeFilter;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class Main {

    private static final String OLD_PREFIX = "org.assertj.core.internal.bytebuddy";
    private static final String NEW_PREFIX = "net.bytebuddy";

    public static void main(String[] args) {
        if (args.length < 1) {
            throw new IllegalArgumentException("Usage: Main <source-dir> [output-dir]");
        }

        File inputDir = new File(args[0]);
        File outputDir = args.length > 1 ? new File(args[1]) : inputDir;

        Launcher launcher = new Launcher();
        for (File sourceRoot : sourceRoots(inputDir)) {
            launcher.addInputResource(sourceRoot.getAbsolutePath());
        }
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(false);
        launcher.getEnvironment().setCommentEnabled(true);
        launcher.setSourceOutputDirectory(outputDir);
        launcher.buildModel();

        CtModel model = launcher.getModel();
        for (CtTypeReference<?> typeReference : model.getElements(new TypeFilter<>(CtTypeReference.class))) {
            rewriteReference(typeReference);
        }
        for (CtImport ctImport : model.getElements(new TypeFilter<>(CtImport.class))) {
            CtReference reference = ctImport.getReference();
            if (reference instanceof CtTypeReference) {
                rewriteReference((CtTypeReference<?>) reference);
            }
        }

        launcher.prettyprint();
    }

    private static List<File> sourceRoots(File inputDir) {
        List<File> roots = new ArrayList<>();
        File mainJava = new File(inputDir, "src/main/java");
        File testJava = new File(inputDir, "src/test/java");
        if (mainJava.isDirectory()) {
            roots.add(mainJava);
        }
        if (testJava.isDirectory()) {
            roots.add(testJava);
        }
        if (roots.isEmpty() && inputDir.isDirectory()) {
            roots.add(inputDir);
        }
        return roots;
    }

    private static void rewriteReference(CtTypeReference<?> reference) {
        String qualifiedName = reference.getQualifiedName();
        if (!qualifiedName.startsWith(OLD_PREFIX)) {
            return;
        }

        CtElement owner = reference.getParent(CtElement.class);
        if (owner == null) {
            return;
        }

        CtTypeReference<?> replacement = owner.getFactory().createReference(qualifiedName.replace(OLD_PREFIX, NEW_PREFIX));
        reference.replace(replacement);
    }
}
