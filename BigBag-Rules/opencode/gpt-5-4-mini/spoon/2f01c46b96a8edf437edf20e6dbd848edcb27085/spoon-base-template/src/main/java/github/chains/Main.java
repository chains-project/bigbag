package github.chains;

import java.io.File;
import spoon.Launcher;
import spoon.compiler.Environment;
import spoon.reflect.code.CtInvocation;
import spoon.processing.AbstractProcessor;
import spoon.reflect.code.CtTypeAccess;
import spoon.reflect.reference.CtTypeReference;

public class Main {
    private static final String OLD_TYPE = "org.codehaus.plexus.util.xml.Xpp3Dom";
    private static final String NEW_TYPE = "org.apache.maven.shared.utils.xml.Xpp3Dom";
    private static final String OLD_READER_TYPE = "org.apache.maven.model.io.xpp3.MavenXpp3Reader";
    private static final String NEW_READER_TYPE = "org.apache.maven.model.io.DefaultModelReader";
    private static final String READ_METHOD_NAME = "read";

    public static void main(String[] args) {
        if (args.length < 2) {
            throw new IllegalArgumentException("Usage: Main <input-source-dir> <output-source-dir>");
        }

        Launcher launcher = new Launcher();
        Environment environment = launcher.getEnvironment();
        environment.setNoClasspath(true);
        environment.setAutoImports(true);
        environment.setCommentEnabled(true);
        launcher.addInputResource(args[0]);
        launcher.setSourceOutputDirectory(args[1]);

        launcher.addProcessor(new AbstractProcessor<CtTypeReference<?>>() {
            @Override
            public boolean isToBeProcessed(CtTypeReference<?> candidate) {
                String qualifiedName = candidate.getQualifiedName();
                return OLD_TYPE.equals(qualifiedName) || OLD_READER_TYPE.equals(qualifiedName);
            }

            @Override
            public void process(CtTypeReference<?> reference) {
                String qualifiedName = reference.getQualifiedName();
                if (OLD_TYPE.equals(qualifiedName)) {
                    reference.replace(getFactory().Type().createReference(NEW_TYPE));
                } else if (OLD_READER_TYPE.equals(qualifiedName)) {
                    reference.replace(getFactory().Type().createReference(NEW_READER_TYPE));
                }
            }
        });

        launcher.addProcessor(new AbstractProcessor<CtTypeAccess<?>>() {
            @Override
            public boolean isToBeProcessed(CtTypeAccess<?> candidate) {
                CtTypeReference<?> type = candidate.getAccessedType();
                return type != null && OLD_TYPE.equals(type.getQualifiedName());
            }

            @Override
            public void process(CtTypeAccess<?> access) {
                access.setAccessedType(getFactory().Type().createReference(NEW_TYPE));
            }
        });

        launcher.addProcessor(new AbstractProcessor<CtInvocation<?>>() {
            @Override
            public boolean isToBeProcessed(CtInvocation<?> candidate) {
                return candidate.getExecutable() != null
                    && READ_METHOD_NAME.equals(candidate.getExecutable().getSimpleName())
                    && candidate.getArguments().size() == 1
                    && candidate.getTarget() != null
                    && candidate.getTarget().getType() != null
                    && (OLD_READER_TYPE.equals(candidate.getTarget().getType().getQualifiedName())
                        || NEW_READER_TYPE.equals(candidate.getTarget().getType().getQualifiedName()));
            }

            @Override
            public void process(CtInvocation<?> invocation) {
                invocation.addArgument(getFactory().Code().createCodeSnippetExpression("java.util.Collections.emptyMap()"));
            }
        });

        launcher.run();
    }
}
