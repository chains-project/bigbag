package github.chains;

import java.io.File;
import java.io.IOException;

import java.nio.file.Files;
import java.nio.file.Path;

import spoon.Launcher;
import spoon.processing.AbstractProcessor;
import spoon.reflect.code.CtFieldAccess;
import spoon.reflect.factory.Factory;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.reference.CtExecutableReference;

public class Main {
    private static final String OLD_DECLARING_TYPE = "org.kohsuke.github.GHCompare";
    private static final String OLD_FIELD_NAME = "status";
    private static final String NEW_METHOD_NAME = "getStatus";

    public static void main(String[] args) {
        if (args.length < 1) {
            throw new IllegalArgumentException("Usage: Main <sourceDir> [outputDir]");
        }
        File sourceDir = new File(args[0]);
        File outputDir = args.length > 1 ? new File(args[1]) : sourceDir;
        if (sourceDir.getAbsoluteFile().equals(outputDir.getAbsoluteFile())) {
            try {
                Path temp = Files.createTempDirectory("spoon-output-");
                outputDir = temp.toFile();
            } catch (IOException e) {
                throw new IllegalStateException(e);
            }
        }

        Launcher launcher = new Launcher();
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true);
        launcher.getEnvironment().setCommentEnabled(true);
        launcher.addInputResource(sourceDir.getAbsolutePath());
        launcher.setSourceOutputDirectory(outputDir);
        launcher.addProcessor(new AbstractProcessor<CtFieldAccess<?>>() {
            @Override
            public boolean isToBeProcessed(CtFieldAccess<?> fieldAccess) {
                if (fieldAccess == null || fieldAccess.getVariable() == null) {
                    return false;
                }
                if (!OLD_FIELD_NAME.equals(fieldAccess.getVariable().getSimpleName())) {
                    return false;
                }
                CtTypeReference<?> declaringType = fieldAccess.getVariable().getDeclaringType();
                return declaringType != null && OLD_DECLARING_TYPE.equals(declaringType.getQualifiedName());
            }

            @Override
            public void process(CtFieldAccess<?> fieldAccess) {
                Factory factory = fieldAccess.getFactory();
                if (fieldAccess.getTarget() == null) {
                    return;
                }
                CtExecutableReference<?> method = factory.Executable().createReference(
                        factory.Type().createReference(OLD_DECLARING_TYPE),
                        factory.Type().createReference("org.kohsuke.github.GHCompare.Status"),
                        NEW_METHOD_NAME);
                fieldAccess.replace(factory.Code().createInvocation(fieldAccess.getTarget().clone(), method));
            }
        });
        launcher.run();
    }
}
