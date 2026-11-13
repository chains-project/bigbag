package github.chains;

import spoon.Launcher;
import spoon.processing.AbstractProcessor;
import spoon.reflect.code.CtExpression;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.reference.CtTypeReference;

public class Main {
    private static final String OBJECT_MAPPER = "com.fasterxml.jackson.databind.ObjectMapper";
    private static final String OBJECT_WRITER = "com.fasterxml.jackson.databind.ObjectWriter";
    private static final String FILE = "java.io.File";

    public static void main(String[] args) {
        if (args.length < 1 || args.length > 2) {
            throw new IllegalArgumentException("Usage: Main <input-source-dir> [output-source-dir]");
        }

        String inputDir = args[0];
        String outputDir = args.length == 2 ? args[1] : inputDir + "-spoon-out";

        Launcher launcher = new Launcher();
        launcher.getEnvironment().setAutoImports(true);
        launcher.getEnvironment().setCommentEnabled(false);
        launcher.getEnvironment().setNoClasspath(true);
        launcher.addInputResource(inputDir);
        launcher.setSourceOutputDirectory(outputDir);
        launcher.addProcessor(new JacksonWriteValueFileProcessor());
        launcher.run();
    }

    public static class JacksonWriteValueFileProcessor extends AbstractProcessor<CtInvocation<?>> {
        @Override
        public boolean isToBeProcessed(CtInvocation<?> invocation) {
            if (!"writeValue".equals(invocation.getExecutable().getSimpleName())) {
                return false;
            }
            if (invocation.getArguments().size() != 2) {
                return false;
            }

            CtExpression<?> target = invocation.getTarget();
            if (target == null) {
                return false;
            }

            CtTypeReference<?> targetType = target.getType();
            if (targetType == null) {
                return false;
            }

            String targetTypeName = targetType.getQualifiedName();
            if (!OBJECT_MAPPER.equals(targetTypeName) && !OBJECT_WRITER.equals(targetTypeName)) {
                return false;
            }

            CtExpression<?> fileArgument = invocation.getArguments().get(0);
            CtTypeReference<?> fileType = fileArgument.getType();
            return fileType != null && FILE.equals(fileType.getQualifiedName());
        }

        @Override
        public void process(CtInvocation<?> invocation) {
            CtExpression<?> fileArgument = invocation.getArguments().get(0);
            CtExpression<?> dataArgument = invocation.getArguments().get(1);
            String replacement = String.format(
                    "java.nio.file.Files.write(%s.toPath(), %s.writeValueAsBytes(%s));",
                    fileArgument,
                    invocation.getTarget(),
                    dataArgument);

            invocation.replace(getFactory().Code().createCodeSnippetStatement(replacement));
        }
    }
}
